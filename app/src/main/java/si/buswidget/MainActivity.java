package si.buswidget;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/** Home screen of the app: shows the two widget stops and lets you change them. */
public class MainActivity extends Activity {

    /** Where new versions are published (see .github/workflows/build.yml). */
    static final String REPO = "ebrutiminci/lpp-bus-widget";
    static final String APK_URL = "https://github.com/" + REPO + "/releases/latest/download/lpp-bus.apk";

    private LinearLayout slotsBox;
    private LinearLayout updateBox;
    private TextView autoText;
    private boolean updateChecked;
    private int editing; // slot being renamed, 0 = none

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int pad = Ui.dp(this, 20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // Keep content clear of the status bar / gesture bar on newer Android versions.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(pad, pad + insets.getSystemWindowInsetTop(),
                    pad, pad + insets.getSystemWindowInsetBottom());
            return insets;
        });

        root.addView(Ui.text(this, "LPP Bus", 28, Ui.TEXT, true));
        TextView sub = Ui.text(this, "Choose the stops and lines your widget shows.", 15, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        root.addView(sub);

        updateBox = new LinearLayout(this);
        updateBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(updateBox);

        slotsBox = new LinearLayout(this);
        slotsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(slotsBox);

        LinearLayout help = Ui.card(this);
        help.addView(Ui.text(this, "How to use", 13, Ui.ACCENT, true));
        TextView ht = Ui.text(this,
                "• Add the widget: long-press your home screen → Widgets → LPP Bus.\n"
                        + "• Tap the widget to refresh it. When a bus has left, the next one moves up by itself.\n"
                        + "• Times are arrival times. A * means timetable time, not live GPS.",
                14, Ui.MUTED, false);
        ht.setPadding(0, Ui.dp(this, 6), 0, 0);
        ht.setLineSpacing(0, 1.2f);
        help.addView(ht);
        autoText = Ui.text(this, "", 13, Ui.MUTED, false);
        autoText.setPadding(0, Ui.dp(this, 10), 0, 0);
        autoText.setLineSpacing(0, 1.2f);
        help.addView(autoText);
        root.addView(help);

        TextView credit = Ui.text(this, "Data: LPP open data (data.lpp.si)", 12, Ui.MUTED, false);
        credit.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(credit);

        setContentView(scroll);

        // First launch: no stop yet, so go straight to choosing one.
        if (savedInstanceState == null && !Config.isSetUp(this)) pick(1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showSlots();
        autoText.setText(Schedule.describe(this));
        if (Config.isSetUp(this)) Schedule.ensureJob(this);
        if (!updateChecked) {
            updateChecked = true;
            checkForUpdate();
        }
    }

    // ------------------------------------------------------------ update check

    private long installedBuild() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return code - 100; // GitHub builds use 100 + build number
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /** Asks GitHub for the newest release; shows an Update box if it's newer than this one. */
    private void checkForUpdate() {
        final long mine = installedBuild();
        new Thread(() -> {
            try {
                String body = Lpp.get("https://api.github.com/repos/" + REPO + "/releases/latest", 8000);
                JSONObject release = new JSONObject(body);
                String tag = release.optString("tag_name", "");      // e.g. "v1.7-build6"
                int at = tag.lastIndexOf("build");
                if (at < 0) return;
                long latest = Long.parseLong(tag.substring(at + 5));
                if (latest <= mine) return;
                String version = tag.startsWith("v") && tag.contains("-")
                        ? tag.substring(1, tag.indexOf('-')) : tag;
                runOnUiThread(() -> showUpdate(version));
            } catch (Exception ignored) {
                // No internet or GitHub unreachable: just don't show anything.
            }
        }).start();
    }

    private void showUpdate(String version) {
        updateBox.removeAllViews();
        LinearLayout card = Ui.card(this);
        card.setBackground(Ui.shape(this, Ui.CARD_SELECTED, Ui.ACCENT, 18));
        card.addView(Ui.text(this, "NEW VERSION AVAILABLE", 12, Ui.ACCENT, true));
        TextView t = Ui.text(this, "LPP Bus " + version + " is out.", 18, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(t);
        card.addView(Ui.text(this, "Tap Update, then Install. Your stops are kept.", 14, Ui.MUTED, false));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(0, Ui.dp(this, 12), 0, 0);
        buttons.addView(Ui.button(this, "Update", true,
                v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))));
        card.addView(buttons);
        updateBox.addView(card);
    }

    private void showSlots() {
        slotsBox.removeAllViews();
        boolean first = Config.isSetUp(this);

        if (!first) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, "YOUR STOP", 12, Ui.ACCENT, true));
            TextView none = Ui.text(this, "No stop chosen yet", 18, Ui.TEXT, true);
            none.setPadding(0, Ui.dp(this, 6), 0, 0);
            card.addView(none);
            card.addView(Ui.text(this, "Pick the stop you use and the bus you take.", 14, Ui.MUTED, false));
            LinearLayout buttons = new LinearLayout(this);
            buttons.setPadding(0, Ui.dp(this, 12), 0, 0);
            buttons.addView(Ui.button(this, "Choose a stop", true, v -> pick(1)));
            card.addView(buttons);
            slotsBox.addView(card);
            return;
        }

        for (int n = 1; n <= Config.SLOTS; n++) {
            final int slot = n;
            Config.Slot s = Config.load(this, n);

            LinearLayout card = Ui.card(this);
            String header = n == 1
                    ? (Config.load(this, 2).enabled ? "LEFT SIDE OF WIDGET" : "YOUR STOP")
                    : (s.enabled ? "RIGHT SIDE OF WIDGET" : "SECOND STOP (OPTIONAL)");
            card.addView(Ui.text(this, header, 12, Ui.ACCENT, true));

            LinearLayout buttons = new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(0, Ui.dp(this, 12), 0, 0);

            if (s.enabled && editing == slot) {
                // Rename in place: just the name, the stop and buses stay as they are.
                final EditText input = Ui.input(this, s.label);
                input.setText(s.label);
                input.setSelection(input.getText().length());
                card.addView(input);
                card.addView(Ui.text(this, "Shown as the small title on the widget. Keep it short.", 13, Ui.MUTED, false));
                buttons.addView(Ui.button(this, "Save", true, v -> {
                    String label = input.getText().toString().trim();
                    if (!label.isEmpty()) {
                        Config.rename(this, slot, label);
                        BusWidget.render(this);
                    }
                    editing = 0;
                    showSlots();
                }));
                buttons.addView(Ui.button(this, "Cancel", false, v -> {
                    editing = 0;
                    showSlots();
                }));
            } else if (s.enabled) {
                TextView name = Ui.text(this, s.label, 18, Ui.TEXT, true);
                name.setPadding(0, Ui.dp(this, 6), 0, 0);
                card.addView(name);
                card.addView(Ui.text(this, Config.linesText(s.lines) + "  ·  stop " + s.code, 14, Ui.MUTED, false));
                buttons.addView(Ui.button(this, "Change", true, v -> pick(slot)));
                buttons.addView(Ui.button(this, "Rename", false, v -> {
                    editing = slot;
                    showSlots();
                }));
                if (n > 1) {
                    buttons.addView(Ui.button(this, "Remove", false, v -> {
                        Config.disable(this, slot);
                        BusWidget.requestRefresh(this);
                        showSlots();
                    }));
                }
            } else {
                TextView none = Ui.text(this, "Show a second stop next to the first one, e.g. for your way home.",
                        15, Ui.MUTED, false);
                none.setPadding(0, Ui.dp(this, 6), 0, 0);
                card.addView(none);
                buttons.addView(Ui.button(this, "Add a second stop", false, v -> pick(slot)));
            }
            card.addView(buttons);
            slotsBox.addView(card);

            // Both sides in use: a round swap icon between the two cards.
            if (n == 1 && Config.load(this, 2).enabled) slotsBox.addView(swapIcon());
        }
    }

    /** Round icon-only button that swaps the left and right stop. */
    private android.view.View swapIcon() {
        TextView icon = Ui.text(this, "⇅", 22, Ui.ACCENT, true);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setBackground(Ui.shape(this, Ui.CARD, Ui.ACCENT, 50));
        icon.setContentDescription("Swap left and right");
        icon.setClickable(true);
        icon.setOnClickListener(v -> {
            Config.swapSides(this);
            editing = 0;
            BusWidget.requestRefresh(this);
            showSlots();
        });
        int size = Ui.dp(this, 44);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 12);
        icon.setLayoutParams(lp);
        return icon;
    }

    private void pick(int slot) {
        Intent i = new Intent(this, PickStopActivity.class);
        i.putExtra(PickStopActivity.EXTRA_SLOT, slot);
        startActivity(i);
    }
}
