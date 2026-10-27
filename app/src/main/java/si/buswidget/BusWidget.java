package si.buswidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home-screen widget: next buses at up to two stops chosen in the app.
 * Tapping the widget refreshes it. Android also refreshes it every 30 min.
 */
public class BusWidget extends AppWidgetProvider {

    static final String ACTION_REFRESH = "si.buswidget.REFRESH";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action) || ACTION_REFRESH.equals(action)) {
            final Context app = context.getApplicationContext();
            final PendingResult pending = goAsync();
            showUpdating(app);
            new Thread(() -> {
                try {
                    refresh(app);
                } finally {
                    pending.finish();
                }
            }).start();
        } else {
            super.onReceive(context, intent);
        }
    }

    /** Ask all widgets to reload (used after settings change). */
    static void requestRefresh(Context c) {
        Intent i = new Intent(c, BusWidget.class);
        i.setAction(ACTION_REFRESH);
        c.sendBroadcast(i);
    }

    // ---------------------------------------------------------------- UI

    private static int[] widgetIds(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, BusWidget.class));
    }

    private static PendingIntent tapIntent(Context c) {
        Intent i = new Intent(c, BusWidget.class);
        i.setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void showUpdating(Context c) {
        int[] ids = widgetIds(c);
        if (ids.length == 0) return;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_bus);
        v.setTextViewText(R.id.footer, "Updating…");
        v.setOnClickPendingIntent(R.id.root, tapIntent(c));
        AppWidgetManager.getInstance(c).partiallyUpdateAppWidget(ids, v);
    }

    static void refresh(Context c) {
        int[] ids = widgetIds(c);
        if (ids.length == 0) return;

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_bus);

        boolean ok1 = fillSlot(c, v, 1, R.id.s1_label, R.id.s1_big, R.id.s1_small);

        Config.Slot s2 = Config.load(c, 2);
        boolean ok2 = true;
        if (s2.enabled) {
            v.setViewVisibility(R.id.s2_col, View.VISIBLE);
            v.setViewVisibility(R.id.divider, View.VISIBLE);
            ok2 = fillSlot(c, v, 2, R.id.s2_label, R.id.s2_big, R.id.s2_small);
        } else {
            v.setViewVisibility(R.id.s2_col, View.GONE);
            v.setViewVisibility(R.id.divider, View.GONE);
        }

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        v.setTextViewText(R.id.footer, (ok1 && ok2)
                ? "Updated " + time + " · tap to refresh  ·  * = timetable"
                : "No connection at " + time + " · tap to retry");
        v.setOnClickPendingIntent(R.id.root, tapIntent(c));

        AppWidgetManager.getInstance(c).updateAppWidget(ids, v);
    }

    /** Fills one column. Returns false if the data couldn't be loaded. */
    private static boolean fillSlot(Context c, RemoteViews v, int n, int labelId, int bigId, int smallId) {
        Config.Slot s = Config.load(c, n);
        v.setTextViewText(labelId, s.label);
        try {
            List<Lpp.Arrival> buses = new ArrayList<>();
            for (Lpp.Arrival a : Lpp.arrivals(s.code, 4000)) {
                if (Config.matches(s.lines, a.line)) buses.add(a);
            }
            if (buses.isEmpty()) {
                v.setTextViewText(bigId, "–");
                v.setTextViewText(smallId, "No buses soon");
                return true;
            }
            Lpp.Arrival first = buses.get(0);
            v.setTextViewText(bigId, clockText(first));
            String small = "Line " + first.line;
            if (buses.size() > 1) {
                Lpp.Arrival next = buses.get(1);
                small += " · next " + next.line + " at " + clockText(next);
            }
            v.setTextViewText(smallId, small);
            return true;
        } catch (Exception e) {
            v.setTextViewText(bigId, "–");
            v.setTextViewText(smallId, "No data");
            return false;
        }
    }

    /** Arrival as a clock time (now + eta), e.g. "09:52", with * if it's from the timetable. */
    private static String clockText(Lpp.Arrival b) {
        long when = System.currentTimeMillis() + Math.max(0, b.eta) * 60_000L;
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(when));
        return t + (b.type == 1 ? "*" : "");
    }
}
