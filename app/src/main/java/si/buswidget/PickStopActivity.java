package si.buswidget;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Choosing a stop for one side of the widget, all on this one screen:
 * 1. search the stop by name
 * 2. tick the bus you take, written like the sign on the bus ("3 → RUDNIK").
 *    The app works out the side of the road from that.
 * 3. confirm the name shown on the widget
 */
public class PickStopActivity extends Activity {

    static final String EXTRA_SLOT = "slot";
    private static final Locale SL = Locale.forLanguageTag("sl");

    /** Stops in the city center, used for the "via city center" label. */
    private static final Set<String> CENTER = new HashSet<>(Arrays.asList(
            "bavarski dvor", "ajdovscina", "posta", "konzorcij", "drama", "tavcarjeva",
            "dalmatinova", "kolodvor", "krizanke", "gornji trg", "zmajski most",
            "cankarjev dom", "razstavisce", "zelezna-1", "zelezna-2", "friskovec"));

    /** One choice on the "which bus" screen: a line + where it ends, at one platform. */
    private static final class BusOption {
        Lpp.Station platform;
        String line;
        String dest;
        String tripId;
        Lpp.Arrival next;          // soonest bus of this kind
        String nextStop = "";      // stop right after this one
        Boolean viaCenter;         // null = can't tell, or this stop is itself in the center
    }

    private int slot;
    private int step = 1;

    private TextView title;
    private TextView subtitle;
    private FrameLayout body;

    // step 1
    private View searchView;
    private EditText search;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private List<Lpp.Station> stations = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private boolean busy;

    // remembered choices, so Back can return to the previous step
    private String chosenName;
    private List<BusOption> options = new ArrayList<>();
    private Set<String> notRunning = new TreeSet<>(Config.LINE_ORDER);
    private boolean loadFailed; // true if the bus data didn't answer for at least one side of the road
    private final List<BusOption> ticked = new ArrayList<>();
    private TextView nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        slot = getIntent().getIntExtra(EXTRA_SLOT, 1);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        final int pad = Ui.dp(this, 20);
        root.setPadding(pad, pad, pad, 0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(pad, pad + insets.getSystemWindowInsetTop(), pad, insets.getSystemWindowInsetBottom());
            return insets;
        });

        root.addView(Ui.text(this, slot == 1 ? "YOUR STOP" : "SECOND STOP", 12, Ui.ACCENT, true));
        title = Ui.text(this, "", 24, Ui.TEXT, true);
        title.setPadding(0, Ui.dp(this, 4), 0, 0);
        root.addView(title);
        subtitle = Ui.text(this, "", 14, Ui.MUTED, false);
        subtitle.setPadding(0, Ui.dp(this, 4), 0, 0);
        root.addView(subtitle);

        body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        searchView = buildSearch();
        setContentView(root);
        showSearch();
        loadStations();
    }

    @Override
    public void onBackPressed() {
        if (step == 3) showBuses();
        else if (step == 2) showSearch();
        else super.onBackPressed();
    }

    private void setBody(View v) {
        body.removeAllViews();
        body.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** A scrolling column of boxes with a button bar at the bottom. */
    private LinearLayout scrollingPage(LinearLayout bottomBar) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        scroll.addView(col);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        if (bottomBar != null) page.addView(bottomBar);
        setBody(page);
        return col;
    }

    /** Bottom bar: Back on the left, the main button in the bottom-right corner. */
    private LinearLayout buttonBar(TextView back, TextView primary) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 16));
        bar.addView(back);
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        primary.setLayoutParams(lp);
        bar.addView(primary);
        return bar;
    }

    private static String clock(int etaMin) {
        long when = System.currentTimeMillis() + Math.max(0, etaMin) * 60_000L;
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(when));
    }

    // ------------------------------------------------------------ step 1: search

    private View buildSearch() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        search = Ui.input(this, "Search, e.g. Ajdovščina");
        box.addView(search);

        status = Ui.text(this, "", 14, Ui.MUTED, false);
        status.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        box.addView(status);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 8));
        list.setSelector(android.R.color.transparent);
        list.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 16));
        list.setClipToPadding(false);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, shown) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (convertView instanceof TextView) ? (TextView) convertView : new TextView(getContext());
                t.setText(getItem(position));
                t.setTextSize(17);
                t.setTextColor(Ui.TEXT);
                int p = Ui.dp(PickStopActivity.this, 16);
                t.setPadding(p, p, p, p);
                t.setBackground(Ui.shape(PickStopActivity.this, Ui.CARD, Ui.BORDER, 14));
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (!busy && position < shown.size()) onStopChosen(shown.get(position));
        });
        box.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });
        return box;
    }

    private void showSearch() {
        step = 1;
        busy = false;
        title.setText("Choose your stop");
        subtitle.setText("Type the name of the stop you get on at.");
        setBody(searchView);
        filter(search.getText().toString());
    }

    private void loadStations() {
        status.setText("Loading stops…");
        status.setOnClickListener(null);
        new Thread(() -> {
            try {
                List<Lpp.Station> st = Lpp.stations();
                Set<String> unique = new TreeSet<>(Collator.getInstance(SL));
                for (Lpp.Station s : st) unique.add(s.name);
                runOnUiThread(() -> {
                    stations = st;
                    names.clear();
                    names.addAll(unique);
                    filter(search.getText().toString());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Couldn't load the stops. Check your internet, then tap here to try again.");
                    status.setOnClickListener(v -> loadStations());
                });
            }
        }).start();
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private void filter(String query) {
        if (names.isEmpty()) return;
        String q = norm(query);
        shown.clear();
        for (String n : names) {
            if (q.isEmpty() || norm(n).contains(q)) shown.add(n);
            if (shown.size() >= 150) break;
        }
        adapter.notifyDataSetChanged();
        status.setText(shown.isEmpty() ? "No stop with that name." : (q.isEmpty() ? "Type to search, or scroll." : ""));
    }

    // ------------------------------------------------------------ step 2: which bus

    private void onStopChosen(String name) {
        final List<Lpp.Station> platforms = new ArrayList<>();
        for (Lpp.Station s : stations) if (s.name.equals(name)) platforms.add(s);
        if (platforms.isEmpty()) return;

        busy = true;
        chosenName = name;
        status.setText("Finding the buses at " + name + "…");
        new Thread(() -> {
            List<BusOption> found = new ArrayList<>();
            Set<String> running = new HashSet<>();
            Set<String> groupsHere = new LinkedHashSet<>();
            boolean failed = false;

            // 1. Every "line → destination" that stops at each platform, including buses that
            //    don't run right now (night buses, 3G, …). Then add the next live time where there is one.
            for (Lpp.Station p : platforms) {
                groupsHere.addAll(p.groups);
                // Mobile data can be slow: try twice before giving up.
                List<Lpp.Route> routes = null;
                List<Lpp.Arrival> arr = null;
                for (int attempt = 0; attempt < 2 && routes == null; attempt++) {
                    try {
                        routes = Lpp.routesOnStation(p.code, 10000);
                    } catch (Exception ignored) { }
                }
                for (int attempt = 0; attempt < 2 && arr == null; attempt++) {
                    try {
                        arr = Lpp.arrivals(p.code, 10000);
                    } catch (Exception ignored) { }
                }
                if (routes == null && arr == null) {
                    failed = true;
                    continue;
                }
                if (routes == null || arr == null) failed = true;

                Map<String, BusOption> byKey = new LinkedHashMap<>();
                if (routes != null) {
                    for (Lpp.Route r : routes) {
                        String key = r.line + "|" + norm(r.dest);
                        if (byKey.containsKey(key)) continue;
                        BusOption o = new BusOption();
                        o.platform = p;
                        o.line = r.line;
                        o.dest = r.dest;
                        o.tripId = r.tripId;
                        byKey.put(key, o);
                    }
                }
                if (arr != null) {
                    for (Lpp.Arrival a : arr) {             // soonest first
                        if (a.line.isEmpty()) continue;
                        running.add(Config.group(a.line));
                        String key = a.line + "|" + norm(a.dest);
                        BusOption o = byKey.get(key);
                        if (o == null) {
                            // Same line, destination spelled differently: if this line has only one
                            // destination here, it's that one.
                            BusOption only = null;
                            int count = 0;
                            for (BusOption x : byKey.values()) {
                                if (x.line.equals(a.line)) {
                                    only = x;
                                    count++;
                                }
                            }
                            if (count == 1) o = only;
                        }
                        if (o == null) {
                            // A destination the route list doesn't name (e.g. a short trip).
                            if (norm(a.dest).contains("garaz")) continue;
                            o = new BusOption();
                            o.platform = p;
                            o.line = a.line;
                            o.dest = a.dest;
                            o.tripId = a.tripId;
                            byKey.put(key, o);
                        }
                        if (o.next == null) {
                            o.next = a;
                            if (a.tripId != null && !a.tripId.isEmpty()) o.tripId = a.tripId;
                        }
                    }
                }
                for (BusOption o : byKey.values()) running.add(Config.group(o.line));
                found.addAll(byKey.values());
            }

            // 2. For each, look at its route: the next stop, and whether it goes via the center.
            ExecutorService pool = Executors.newFixedThreadPool(6);
            List<Callable<Void>> jobs = new ArrayList<>();
            for (final BusOption o : found) jobs.add(() -> {
                fillRouteInfo(o);
                return null;
            });
            try {
                pool.invokeAll(jobs, 15, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            } finally {
                pool.shutdownNow();
            }

            found.sort((x, y) -> {
                int c = Config.LINE_ORDER.compare(x.line, y.line);
                return c != 0 ? c : x.dest.compareTo(y.dest);
            });

            Set<String> idle = new TreeSet<>(Config.LINE_ORDER);
            for (String g : groupsHere) if (!g.isEmpty() && !running.contains(g)) idle.add(g);

            final boolean anyFailed = failed;
            runOnUiThread(() -> {
                busy = false;
                status.setText("");
                loadFailed = anyFailed;
                options = found;
                notRunning = idle;
                ticked.clear();
                preTick();
                showBuses();
            });
        }).start();
    }

    private static void fillRouteInfo(BusOption o) {
        if (o.tripId == null || o.tripId.isEmpty()) return;
        try {
            List<Lpp.RouteStop> route = Lpp.stopsOnRoute(o.tripId, 6000);
            int at = -1;
            for (int i = 0; i < route.size(); i++) {
                if (route.get(i).code.equals(o.platform.code)) {
                    at = i;
                    break;
                }
            }
            if (at < 0) return;
            if (at + 1 < route.size()) o.nextStop = route.get(at + 1).name;
            if (!CENTER.contains(norm(o.platform.name))) {
                boolean via = false;
                for (int i = at + 1; i < route.size() && i <= at + 15; i++) {
                    if (CENTER.contains(norm(route.get(i).name))) {
                        via = true;
                        break;
                    }
                }
                o.viaCenter = via;
            }
        } catch (Exception ignored) { }
    }

    /** If this side of the widget already uses this stop, tick what it had. */
    private void preTick() {
        Config.Slot current = Config.load(this, slot);
        if (!current.enabled) return;
        for (BusOption o : options) {
            if (o.platform.code.equals(current.code) && Config.matches(current.lines, o.line)
                    && !current.lines.isEmpty()) {
                ticked.add(o);
            }
        }
    }

    private String tickedPlatform() {
        return ticked.isEmpty() ? null : ticked.get(0).platform.code;
    }

    private void showBuses() {
        step = 2;
        title.setText(chosenName);
        subtitle.setText("Tick every bus you take. You can pick more than one. They're written like the sign on the front of the bus.");

        nextButton = Ui.button(this, "Next", true, v -> {
            if (ticked.isEmpty()) {
                Toast.makeText(this, "Tick at least one bus.", Toast.LENGTH_SHORT).show();
            } else {
                showName();
            }
        });
        LinearLayout col = scrollingPage(buttonBar(Ui.button(this, "Back", false, v -> onBackPressed()), nextButton));

        if (options.isEmpty()) {
            col.addView(Ui.text(this, loadFailed
                    ? "Couldn't reach the bus data. The connection may be slow. Check your internet and try again."
                    : "No buses are coming to this stop right now, so the app can't tell which way they go. "
                      + "Try again when buses are running.", 15, Ui.WARN, false));
            col.addView(retryButton());
            return;
        }

        final List<View> boxes = new ArrayList<>();
        final List<TextView> ticks = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            final BusOption o = options.get(i);
            LinearLayout box = Ui.card(this);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setClickable(true);

            // Line number in a little outlined square, like the bus sign.
            TextView num = Ui.text(this, o.line, 18, Ui.ACCENT, true);
            num.setGravity(Gravity.CENTER);
            num.setMinWidth(Ui.dp(this, 48));
            int np = Ui.dp(this, 8);
            num.setPadding(np, np, np, np);
            num.setBackground(Ui.shape(this, android.graphics.Color.TRANSPARENT, Ui.ACCENT, 10));
            box.addView(num);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
            texts.addView(Ui.text(this, "→ " + (o.dest.isEmpty() ? "?" : o.dest.toUpperCase(SL)), 17, Ui.TEXT, true));

            List<String> info = new ArrayList<>();
            if (Boolean.TRUE.equals(o.viaCenter)) info.add("via city center");
            else if (Boolean.FALSE.equals(o.viaCenter)) info.add("not via center");
            if (!o.nextStop.isEmpty()) info.add("next stop " + o.nextStop);
            if (!info.isEmpty()) {
                TextView inf = Ui.text(this, String.join(" · ", info), 13,
                        Boolean.TRUE.equals(o.viaCenter) ? Ui.ACCENT : Ui.MUTED, false);
                inf.setPadding(0, Ui.dp(this, 2), 0, 0);
                texts.addView(inf);
            }
            TextView when = Ui.text(this, o.next != null
                    ? "next one " + clock(o.next.eta) + (o.next.type == 1 ? "*" : "")
                    : "not running right now", 13, Ui.MUTED, false);
            texts.addView(when);
            box.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tick = Ui.text(this, "", 16, Ui.ON_ACCENT, true);
            tick.setGravity(Gravity.CENTER);
            int ts = Ui.dp(this, 28);
            box.addView(tick, new LinearLayout.LayoutParams(ts, ts));

            box.setOnClickListener(v -> {
                String side = tickedPlatform();
                if (ticked.contains(o)) {
                    ticked.remove(o);
                } else if (side == null || side.equals(o.platform.code)) {
                    ticked.add(o);
                } else {
                    Toast.makeText(this, "That bus leaves from the other side of the road. "
                            + "Untick the others first.", Toast.LENGTH_SHORT).show();
                }
                paintBoxes(boxes, ticks);
            });
            boxes.add(box);
            ticks.add(tick);
            col.addView(box);
        }

        if (loadFailed) {
            TextView warn = Ui.text(this, "Some buses may be missing: the data for one side of the road didn't load.",
                    14, Ui.WARN, false);
            warn.setPadding(0, Ui.dp(this, 14), 0, 0);
            col.addView(warn);
            col.addView(retryButton());
        }

        if (!notRunning.isEmpty()) {
            TextView idle = Ui.text(this, "Not running right now: line " + String.join(", ", notRunning)
                    + ". Come back when they run to add them.", 13, Ui.MUTED, false);
            idle.setPadding(0, Ui.dp(this, 14), 0, 0);
            col.addView(idle);
        }
        paintBoxes(boxes, ticks);
    }

    private View retryButton() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, Ui.dp(this, 12), 0, 0);
        row.addView(Ui.button(this, "Try again", true, v -> {
            if (!busy) {
                showSearch();
                onStopChosen(chosenName);
            }
        }));
        return row;
    }

    /** Ticked boxes go green; boxes from the other side of the road fade out. */
    private void paintBoxes(List<View> boxes, List<TextView> ticks) {
        String side = tickedPlatform();
        for (int i = 0; i < boxes.size(); i++) {
            BusOption o = options.get(i);
            boolean on = ticked.contains(o);
            boolean otherSide = side != null && !side.equals(o.platform.code);
            Ui.setSelected(this, boxes.get(i), on);
            ticks.get(i).setText(on ? "✓" : "");
            ticks.get(i).setBackground(on
                    ? Ui.shape(this, Ui.ACCENT, 0, 8)
                    : Ui.shape(this, android.graphics.Color.TRANSPARENT, Ui.MUTED, 8));
            boxes.get(i).setAlpha(otherSide ? 0.35f : 1f);
        }
        if (nextButton != null) {
            nextButton.setText(ticked.isEmpty() ? "Next" : "Next (" + ticked.size() + ")");
            nextButton.setAlpha(ticked.isEmpty() ? 0.5f : 1f);
        }
    }

    // ------------------------------------------------------------ step 3: name & save

    private void showName() {
        step = 3;
        final BusOption first = ticked.get(0);
        final Lpp.Station platform = first.platform;
        final Set<String> lines = new LinkedHashSet<>();
        Set<String> dests = new LinkedHashSet<>();
        for (BusOption o : ticked) {
            lines.add(o.line);
            if (!o.dest.isEmpty()) dests.add(o.dest);
        }

        title.setText("Name on the widget");
        subtitle.setText("The small title above the times. Keep it short.");

        String dest = dests.isEmpty() ? null : dests.iterator().next();
        final String suggestion = platform.name.toUpperCase(SL) + (dest != null ? " → " + dest.toUpperCase(SL) : "");

        final EditText input = Ui.input(this, suggestion);
        input.setText(suggestion);
        input.setSelection(input.getText().length());

        LinearLayout col = scrollingPage(buttonBar(
                Ui.button(this, "Back", false, v -> onBackPressed()),
                Ui.button(this, "Save", true, v -> save(platform, lines, input.getText().toString().trim(), suggestion))));
        col.addView(input);

        // What the widget will show, so you can check it against the real bus.
        BusOption soonest = null;
        for (BusOption o : ticked) {
            if (o.next != null && (soonest == null || o.next.eta < soonest.next.eta)) soonest = o;
        }
        LinearLayout check = Ui.card(this);
        check.addView(Ui.text(this, "NEXT BUS FROM HERE", 12, Ui.ACCENT, true));
        if (soonest != null) {
            TextView big = Ui.text(this, clock(soonest.next.eta) + (soonest.next.type == 1 ? "*" : ""), 30, Ui.TEXT, true);
            big.setPadding(0, Ui.dp(this, 4), 0, 0);
            check.addView(big);
            check.addView(Ui.text(this, "Line " + soonest.line + " → " + soonest.dest, 15, Ui.MUTED, false));
        } else {
            TextView none = Ui.text(this, "None of your buses run right now. The widget will show them when they do.",
                    15, Ui.MUTED, false);
            none.setPadding(0, Ui.dp(this, 4), 0, 0);
            check.addView(none);
        }
        col.addView(check);

        List<String> chosen = new ArrayList<>();
        for (BusOption o : ticked) chosen.add(o.line + " → " + o.dest);
        LinearLayout summary = Ui.card(this);
        summary.addView(Ui.text(this, "YOUR BUSES", 12, Ui.ACCENT, true));
        TextView s = Ui.text(this, String.join("\n", chosen), 15, Ui.TEXT, false);
        s.setPadding(0, Ui.dp(this, 6), 0, 0);
        s.setLineSpacing(0, 1.25f);
        summary.addView(s);
        col.addView(summary);
    }

    private void save(Lpp.Station platform, Set<String> lines, String label, String suggestion) {
        if (label.isEmpty()) label = suggestion;
        Config.save(this, slot, platform.code, label, lines);
        BusWidget.requestRefresh(this);
        Toast.makeText(this, "Saved. The widget is updating.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
