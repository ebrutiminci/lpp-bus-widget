package si.buswidget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * When should the widget refresh by itself?
 *
 * It learns from your taps: every tap on the widget is counted per hour of the day
 * (weekdays and weekends separately). Those counts never leave the phone.
 * Until it has learned enough (10 taps over at least 5 days), it uses weekday rush hours.
 *
 * A background job wakes about every 15 minutes (Android's minimum). It only downloads
 * new times if the current hour, or the next one, is one of your hours. Otherwise it does nothing.
 */
final class Schedule {

    static final int JOB_ID = 4711;
    private static final long PERIOD_MS = 15 * 60 * 1000L;
    private static final int MIN_TAPS = 10;
    private static final long MIN_DAYS_MS = 5L * 24 * 60 * 60 * 1000;
    private static final int TAPS_FOR_HOUR = 2;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("learn", Context.MODE_PRIVATE);
    }

    private static boolean weekend(Calendar cal) {
        int d = cal.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY;
    }

    private static String key(boolean weekend, int hour) {
        return (weekend ? "we_" : "wd_") + hour;
    }

    static void recordTap(Context c) {
        Calendar cal = Calendar.getInstance();
        String k = key(weekend(cal), cal.get(Calendar.HOUR_OF_DAY));
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit()
                .putInt(k, p.getInt(k, 0) + 1)
                .putInt("taps", p.getInt("taps", 0) + 1);
        if (p.getLong("first_tap", 0) == 0) e.putLong("first_tap", System.currentTimeMillis());
        e.apply();
    }

    static boolean learned(Context c) {
        SharedPreferences p = prefs(c);
        long first = p.getLong("first_tap", 0);
        return p.getInt("taps", 0) >= MIN_TAPS && first > 0
                && System.currentTimeMillis() - first >= MIN_DAYS_MS;
    }

    private static boolean rushHour(boolean weekend, int h) {
        return !weekend && ((h >= 6 && h < 9) || (h >= 14 && h < 18));
    }

    private static boolean yourHour(Context c, boolean weekend, int h) {
        return prefs(c).getInt(key(weekend, h), 0) >= TAPS_FOR_HOUR;
    }

    /** Should the widget refresh by itself right now? */
    static boolean isActiveNow(Context c) {
        Calendar cal = Calendar.getInstance();
        boolean we = weekend(cal);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        if (!learned(c)) return rushHour(we, h);
        // Your hour, or the hour before it (so it's fresh when you look).
        return yourHour(c, we, h) || yourHour(c, we, (h + 1) % 24);
    }

    /** Human-readable summary for the app screen. */
    static String describe(Context c) {
        if (!learned(c)) {
            int taps = prefs(c).getInt("taps", 0);
            return "Auto-refresh: weekdays 6–9 and 14–18 for now. It's learning when you usually look ("
                    + taps + " of " + MIN_TAPS + " taps, over at least 5 days).";
        }
        String wd = hours(c, false), we = hours(c, true);
        StringBuilder sb = new StringBuilder("Auto-refresh learned from your taps: ");
        sb.append(wd.isEmpty() ? "weekdays none" : "weekdays " + wd);
        sb.append(" · ").append(we.isEmpty() ? "weekends none" : "weekends " + we);
        sb.append(". Tapping still refreshes any time.");
        return sb.toString();
    }

    /** e.g. "7–9, 16–18" for the hours (including the hour before) that auto-refresh. */
    private static String hours(Context c, boolean weekend) {
        boolean[] on = new boolean[24];
        for (int h = 0; h < 24; h++) on[h] = yourHour(c, weekend, h) || yourHour(c, weekend, (h + 1) % 24);
        List<String> ranges = new ArrayList<>();
        int h = 0;
        while (h < 24) {
            if (!on[h]) { h++; continue; }
            int start = h;
            while (h < 24 && on[h]) h++;
            ranges.add(start + "–" + h);
        }
        return String.join(", ", ranges);
    }

    // ---------------------------------------------------------------- background job

    static void ensureJob(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, RefreshJob.class))
                .setPeriodic(PERIOD_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    static void cancelJob(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(JOB_ID);
    }
}
