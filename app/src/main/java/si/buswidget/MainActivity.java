package si.buswidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Home screen of the app: shows the two widget stops and lets you change them. */
public class MainActivity extends Activity {

    private LinearLayout slotsBox;

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

        slotsBox = new LinearLayout(this);
        slotsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(slotsBox);

        LinearLayout help = Ui.card(this);
        help.addView(Ui.text(this, "How to use", 13, Ui.ACCENT, true));
        TextView ht = Ui.text(this,
                "• Add the widget: long-press your home screen → Widgets → LPP Bus.\n"
                        + "• Tap the widget to refresh it.\n"
                        + "• Times are arrival times. A * means timetable time, not live GPS.",
                14, Ui.MUTED, false);
        ht.setPadding(0, Ui.dp(this, 6), 0, 0);
        ht.setLineSpacing(0, 1.2f);
        help.addView(ht);
        root.addView(help);

        TextView credit = Ui.text(this, "Data: LPP open data (data.lpp.si)", 12, Ui.MUTED, false);
        credit.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(credit);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showSlots();
    }

    private void showSlots() {
        slotsBox.removeAllViews();
        for (int n = 1; n <= Config.SLOTS; n++) {
            final int slot = n;
            Config.Slot s = Config.load(this, n);

            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, n == 1 ? "LEFT SIDE OF WIDGET" : "RIGHT SIDE OF WIDGET", 12, Ui.ACCENT, true));

            LinearLayout buttons = new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(0, Ui.dp(this, 12), 0, 0);

            if (s.enabled) {
                TextView name = Ui.text(this, s.label, 18, Ui.TEXT, true);
                name.setPadding(0, Ui.dp(this, 6), 0, 0);
                card.addView(name);
                card.addView(Ui.text(this, Config.linesText(s.lines) + "  ·  stop " + s.code, 14, Ui.MUTED, false));
                buttons.addView(Ui.button(this, "Change", true, v -> pick(slot)));
                if (n > 1) {
                    buttons.addView(Ui.button(this, "Remove", false, v -> {
                        Config.disable(this, slot);
                        BusWidget.requestRefresh(this);
                        showSlots();
                    }));
                }
            } else {
                TextView none = Ui.text(this, "Not used, the widget shows one stop", 15, Ui.MUTED, false);
                none.setPadding(0, Ui.dp(this, 6), 0, 0);
                card.addView(none);
                buttons.addView(Ui.button(this, "Add a stop", true, v -> pick(slot)));
            }
            card.addView(buttons);
            slotsBox.addView(card);
        }
    }

    private void pick(int slot) {
        Intent i = new Intent(this, PickStopActivity.class);
        i.putExtra(PickStopActivity.EXTRA_SLOT, slot);
        startActivity(i);
    }
}
