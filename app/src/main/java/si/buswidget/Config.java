package si.buswidget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The user's chosen stops, saved on the phone.
 * The widget has two columns ("slots"). Slot 2 can be switched off.
 *
 * Lines are stored as route names, e.g. "3", "3B".
 * An entry ending in "*" (e.g. "3*") means every variant of that line (3, 3B, 3G…).
 * An empty set means "all lines".
 */
final class Config {

    static final int SLOTS = 2;

    static final class Slot {
        boolean enabled;
        String code;        // LPP station code, e.g. "503041"
        String label;       // text shown on the widget
        Set<String> lines;  // see class comment
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    /** A new install starts with no stops: a slot is only "enabled" once a stop was chosen for it. */
    static Slot load(Context c, int n) {
        SharedPreferences p = prefs(c);
        Slot s = new Slot();
        s.code = p.getString("s" + n + "_code", null);
        s.label = p.getString("s" + n + "_label", "");
        s.lines = split(p.getString("s" + n + "_lines", ""));
        s.enabled = s.code != null && p.getBoolean("s" + n + "_enabled", false);
        return s;
    }

    /** True once the first stop has been chosen. */
    static boolean isSetUp(Context c) {
        return load(c, 1).enabled;
    }

    static void save(Context c, int n, String code, String label, Set<String> lines) {
        prefs(c).edit()
                .putBoolean("s" + n + "_enabled", true)
                .putString("s" + n + "_code", code)
                .putString("s" + n + "_label", label)
                .putString("s" + n + "_lines", String.join(",", lines))
                .apply();
    }

    static void disable(Context c, int n) {
        prefs(c).edit().putBoolean("s" + n + "_enabled", false).apply();
    }

    static Set<String> split(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Line number without letters: "3B" -> "3", "19I" -> "19". */
    static String group(String route) {
        int i = 0;
        while (i < route.length() && Character.isDigit(route.charAt(i))) i++;
        return i == 0 ? route : route.substring(0, i);
    }

    static boolean matches(Set<String> selected, String route) {
        if (selected.isEmpty()) return true;
        if (selected.contains(route)) return true;
        return selected.contains(group(route) + "*");
    }

    static String linesText(Set<String> lines) {
        if (lines.isEmpty()) return "All lines";
        List<String> list = new ArrayList<>(lines);
        list.sort(LINE_ORDER);
        List<String> pretty = new ArrayList<>();
        for (String l : list) pretty.add(l.endsWith("*") ? l.substring(0, l.length() - 1) + " (all)" : l);
        return "Lines " + String.join(", ", pretty);
    }

    /** Sorts 1, 2, 3, 3B, 3G, 6, 11 … instead of 1, 11, 2 … */
    static final Comparator<String> LINE_ORDER = (a, b) -> {
        int na = num(a), nb = num(b);
        if (na != nb) return Integer.compare(na, nb);
        return a.compareTo(b);
    };

    private static int num(String s) {
        String g = group(s);
        try {
            return Integer.parseInt(g);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
