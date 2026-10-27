package si.buswidget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers so every screen is built in code with one consistent look. */
final class Ui {
    static final int BG = Color.parseColor("#0F1512");
    static final int CARD = Color.parseColor("#1E2A24");
    static final int CARD_PRESSED = Color.parseColor("#2A3B32");
    static final int CARD_SELECTED = Color.parseColor("#24402F");
    static final int BORDER = Color.parseColor("#34493E");
    static final int ACCENT = Color.parseColor("#8FD3A8");
    static final int ON_ACCENT = Color.parseColor("#0F1A14");
    static final int TEXT = Color.WHITE;
    static final int MUTED = Color.parseColor("#B0B8B4");
    static final int WARN = Color.parseColor("#F2C46D");

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    static GradientDrawable shape(Context c, int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1), stroke);
        return d;
    }

    static LinearLayout.LayoutParams fullWidth(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    /** A bordered box. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setBackground(shape(c, CARD, BORDER, 18));
        l.setLayoutParams(fullWidth(c, 12));
        return l;
    }

    /** A bordered box you can tap: it darkens while pressed. */
    static LinearLayout tapCard(Context c, View.OnClickListener onClick) {
        LinearLayout l = card(c);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, shape(c, CARD_PRESSED, ACCENT, 18));
        states.addState(new int[]{}, shape(c, CARD, BORDER, 18));
        l.setBackground(states);
        l.setClickable(true);
        l.setOnClickListener(onClick);
        return l;
    }

    /** Shows a box as ticked or not (used for the line choices). */
    static void setSelected(Context c, View box, boolean selected) {
        box.setBackground(selected ? shape(c, CARD_SELECTED, ACCENT, 18) : shape(c, CARD, BORDER, 18));
    }

    /** Small rounded label, e.g. "Goes via city center". */
    static TextView badge(Context c, String s, int color) {
        TextView t = text(c, s, 12, color, true);
        int h = dp(c, 10), v = dp(c, 4);
        t.setPadding(h, v, h, v);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, 50));
        d.setColor(Color.TRANSPARENT);
        d.setStroke(dp(c, 1), color);
        t.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 8);
        t.setLayoutParams(lp);
        return t;
    }

    static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(17);
        int p = dp(c, 14);
        e.setPadding(p, p, p, p);
        e.setBackground(shape(c, CARD, BORDER, 14));
        e.setLayoutParams(fullWidth(c, 14));
        return e;
    }

    /** A rounded button made from a TextView, so it looks the same on every phone. */
    static TextView button(Context c, String s, boolean primary, View.OnClickListener onClick) {
        TextView b = text(c, s, 15, primary ? ON_ACCENT : ACCENT, true);
        int h = dp(c, 18), v = dp(c, 11);
        b.setPadding(h, v, h, v);
        b.setBackground(primary ? shape(c, ACCENT, 0, 50) : shape(c, Color.TRANSPARENT, ACCENT, 50));
        b.setClickable(true);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 8);
        b.setLayoutParams(lp);
        return b;
    }
}
