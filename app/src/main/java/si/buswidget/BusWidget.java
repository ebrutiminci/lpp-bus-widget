package si.buswidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home-screen widget: next buses at up to two stops chosen in the app.
 *
 * How it stays right without constant internet:
 *  - Each refresh saves the next few buses with their arrival clock time.
 *  - When a shown bus's time passes, an alarm redraws the widget from that saved list,
 *    so the next bus moves up. No internet needed for that.
 *  - It refreshes from the internet when tapped, after settings change, and in the
 *    background every ~15 min during the hours you usually look (see {@link Schedule}).
 */
public class BusWidget extends AppWidgetProvider {

    static final String ACTION_REFRESH = "si.buswidget.REFRESH"; // reload (e.g. after settings change)
    static final String ACTION_TAP = "si.buswidget.TAP";         // the user tapped the widget
    static final String ACTION_TICK = "si.buswidget.TICK";       // a shown bus has left: redraw only

    private static final int KEEP = 8;              // buses saved per stop
    private static final long GRACE_MS = 60_000L;   // keep a bus on screen 1 min past its time

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        final Context app = context.getApplicationContext();
        if (ACTION_TICK.equals(action)) {
            // A shown bus's time has passed. Ask the live data again: if the bus is running late,
            // it stays on the widget with its new time. Only if there's no internet is it dropped.
            final PendingResult pending = goAsync();
            new Thread(() -> {
                try {
                    refresh(app, 6000);
                } finally {
                    pending.finish();
                }
            }).start();
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || ACTION_REFRESH.equals(action) || ACTION_TAP.equals(action)) {
            if (ACTION_TAP.equals(action)) Schedule.recordTap(app);
            Schedule.ensureJob(app);
            final PendingResult pending = goAsync();
            showUpdating(app);
            new Thread(() -> {
                try {
                    refresh(app, 4000);
                } finally {
                    pending.finish();
                }
            }).start();
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onEnabled(Context context) {
        Schedule.ensureJob(context.getApplicationContext());
    }

    @Override
    public void onDisabled(Context context) {
        Schedule.cancelJob(context.getApplicationContext());
        try {
            alarm(context).cancel(tickIntent(context));
        } catch (Exception ignored) { }
    }

    /** Ask all widgets to reload (used after settings change). */
    static void requestRefresh(Context c) {
        Intent i = new Intent(c, BusWidget.class);
        i.setAction(ACTION_REFRESH);
        c.sendBroadcast(i);
    }

    // ---------------------------------------------------------------- data

    /** One saved upcoming bus. */
    private static final class Bus {
        String line;
        long when;   // arrival, epoch millis
        int type;    // 0 = live, 1 = timetable, 2 = at the stop
    }

    private static SharedPreferences cache(Context c) {
        return c.getSharedPreferences("widget_cache", Context.MODE_PRIVATE);
    }

    static boolean hasWidgets(Context c) {
        return widgetIds(c).length > 0;
    }

    /** Downloads the next buses for both stops, saves them, and redraws. */
    static void refresh(Context c, int timeoutMs) {
        if (!hasWidgets(c)) return;
        if (Config.isSetUp(c)) {
            long now = System.currentTimeMillis();
            SharedPreferences.Editor e = cache(c).edit();
            boolean ok = true;
            for (int n = 1; n <= Config.SLOTS; n++) {
                Config.Slot s = Config.load(c, n);
                if (!s.enabled) {
                    e.remove("s" + n).remove("s" + n + "_ok");
                    continue;
                }
                try {
                    StringBuilder sb = new StringBuilder();
                    int count = 0;
                    for (Lpp.Arrival a : Lpp.arrivals(s.code, timeoutMs)) {
                        if (!Config.matches(s.lines, a.line)) continue;
                        long when = now + Math.max(0, a.eta) * 60_000L;
                        sb.append(a.line.replace("|", "").replace(";", ""))
                                .append('|').append(when).append('|').append(a.type).append(';');
                        if (++count >= KEEP) break;
                    }
                    e.putString("s" + n, sb.toString()).putBoolean("s" + n + "_ok", true);
                } catch (Exception ex) {
                    // Keep the buses we already know, so the widget still shows something useful.
                    ok = false;
                    e.putBoolean("s" + n + "_ok", false);
                }
            }
            e.putLong("fetched", now).putBoolean("ok", ok).commit();
        }
        render(c);
    }

    private static List<Bus> saved(Context c, int n) {
        List<Bus> list = new ArrayList<>();
        String raw = cache(c).getString("s" + n, "");
        if (raw == null || raw.isEmpty()) return list;
        for (String item : raw.split(";")) {
            String[] f = item.split("\\|");
            if (f.length < 3) continue;
            try {
                Bus b = new Bus();
                b.line = f[0];
                b.when = Long.parseLong(f[1]);
                b.type = Integer.parseInt(f[2]);
                list.add(b);
            } catch (NumberFormatException ignored) { }
        }
        list.sort((x, y) -> Long.compare(x.when, y.when));
        return list;
    }

    // ---------------------------------------------------------------- UI

    private static int[] widgetIds(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, BusWidget.class));
    }

    private static PendingIntent tapIntent(Context c) {
        Intent i = new Intent(c, BusWidget.class);
        i.setAction(ACTION_TAP);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent tickIntent(Context c) {
        Intent i = new Intent(c, BusWidget.class);
        i.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openAppIntent(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static AlarmManager alarm(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    private static void showUpdating(Context c) {
        if (!Config.isSetUp(c)) return;
        int[] ids = widgetIds(c);
        if (ids.length == 0) return;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_bus);
        v.setTextViewText(R.id.footer, "Updating…");
        v.setOnClickPendingIntent(R.id.root, tapIntent(c));
        AppWidgetManager.getInstance(c).partiallyUpdateAppWidget(ids, v);
    }

    /** Draws the widget from the saved buses (no internet) and sets the next redraw. */
    static void render(Context c) {
        int[] ids = widgetIds(c);
        if (ids.length == 0) return;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_bus);

        // Fresh install: no stop chosen yet. Tapping opens the app to set it up.
        if (!Config.isSetUp(c)) {
            v.setTextViewText(R.id.s1_label, "LPP BUS");
            v.setTextViewText(R.id.s1_big, "Set up");
            v.setTextViewText(R.id.s1_small, "Tap to choose your stop");
            v.setViewVisibility(R.id.s2_col, View.GONE);
            v.setViewVisibility(R.id.divider, View.GONE);
            v.setTextViewText(R.id.footer, "");
            v.setOnClickPendingIntent(R.id.root, openAppIntent(c));
            AppWidgetManager.getInstance(c).updateAppWidget(ids, v);
            return;
        }

        long now = System.currentTimeMillis();
        long nextChange = Long.MAX_VALUE;

        nextChange = Math.min(nextChange, drawSlot(c, v, 1, now, R.id.s1_label, R.id.s1_big, R.id.s1_small));
        if (Config.load(c, 2).enabled) {
            v.setViewVisibility(R.id.s2_col, View.VISIBLE);
            v.setViewVisibility(R.id.divider, View.VISIBLE);
            nextChange = Math.min(nextChange, drawSlot(c, v, 2, now, R.id.s2_label, R.id.s2_big, R.id.s2_small));
        } else {
            v.setViewVisibility(R.id.s2_col, View.GONE);
            v.setViewVisibility(R.id.divider, View.GONE);
        }

        SharedPreferences cache = cache(c);
        long fetched = cache.getLong("fetched", 0);
        String time = fetched > 0 ? clock(fetched) : "–";
        v.setTextViewText(R.id.footer, cache.getBoolean("ok", true)
                ? "Updated " + time + " · tap to refresh  ·  * = timetable"
                : "No connection at " + time + " · tap to retry");
        v.setOnClickPendingIntent(R.id.root, tapIntent(c));
        AppWidgetManager.getInstance(c).updateAppWidget(ids, v);

        // Redraw when the first shown bus has left. RTC (not wakeup): it only runs while the
        // phone is in use, which is when anyone looks at the widget.
        try {
            AlarmManager am = alarm(c);
            if (nextChange != Long.MAX_VALUE) am.set(AlarmManager.RTC, nextChange, tickIntent(c));
            else am.cancel(tickIntent(c));
        } catch (Exception ignored) { }
    }

    /** Fills one column from saved buses. Returns when this column next changes. */
    private static long drawSlot(Context c, RemoteViews v, int n, long now, int labelId, int bigId, int smallId) {
        Config.Slot s = Config.load(c, n);
        v.setTextViewText(labelId, s.label);

        List<Bus> all = saved(c, n);
        List<Bus> upcoming = new ArrayList<>();
        for (Bus b : all) if (b.when + GRACE_MS > now) upcoming.add(b);

        if (upcoming.isEmpty()) {
            boolean ok = cache(c).getBoolean("s" + n + "_ok", true);
            v.setTextViewText(bigId, "–");
            v.setTextViewText(smallId, !ok ? "No data" : all.isEmpty() ? "No buses soon" : "Tap to refresh");
            return Long.MAX_VALUE;
        }

        Bus first = upcoming.get(0);
        v.setTextViewText(bigId, clock(first.when) + (first.type == 1 ? "*" : ""));
        String small = "Line " + first.line;
        if (upcoming.size() > 1) {
            Bus next = upcoming.get(1);
            small += " · next " + next.line + " at " + clock(next.when) + (next.type == 1 ? "*" : "");
        }
        v.setTextViewText(smallId, small);
        return first.when + GRACE_MS;
    }

    private static String clock(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
