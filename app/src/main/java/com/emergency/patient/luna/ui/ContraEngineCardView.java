package com.emergency.patient.luna.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.emergency.patient.luna.model.Contradiction;
import com.emergency.patient.luna.model.ContraSeverity;

import java.util.List;

/**
 * Builds the contradiction engine card list programmatically.
 * Each item is expandable on tap to show mechanism + recommendation.
 */
public class ContraEngineCardView extends LinearLayout {

    public ContraEngineCardView(Context context) { super(context); setOrientation(VERTICAL); }
    public ContraEngineCardView(Context context, AttributeSet attrs) { super(context, attrs); setOrientation(VERTICAL); }

    public void bind(List<Contradiction> contradictions) {
        removeAllViews();
        if (contradictions == null || contradictions.isEmpty()) return;
        for (Contradiction c : contradictions) addView(buildItem(c));
    }

    private View buildItem(Contradiction c) {
        // Container
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(14), dp(16), dp(12));

        // Left border via background colour — we embed into a horizontal wrapper
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);

        // Severity border bar
        View border = new View(getContext());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(3), LayoutParams.MATCH_PARENT);
        bp.setMarginEnd(dp(12));
        border.setLayoutParams(bp);
        border.setBackgroundColor(severityColor(c.severity));
        row.addView(border);

        // Right content
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        // Drug pills row
        LinearLayout pillRow = new LinearLayout(getContext());
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setGravity(Gravity.CENTER_VERTICAL);
        pillRow.addView(makePill(c.drugA != null ? capitalise(c.drugA) : "?"));
        pillRow.addView(makeVs());
        String b = c.drugB != null ? capitalise(c.drugB)
                : c.phaseTarget != null ? capitalise(c.phaseTarget.label) + " Phase"
                : c.nutrientTarget != null ? c.nutrientTarget
                : "Unknown";
        pillRow.addView(makePill(b));
        content.addView(pillRow);

        // Severity badge
        TextView badge = new TextView(getContext());
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        badge.setText(c.severity.name());
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        badge.setBackgroundColor(severityBg(c.severity));
        badge.setTextColor(severityTextColor(c.severity));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = dp(6);
        badge.setLayoutParams(badgeParams);
        content.addView(badge);

        // Summary
        TextView summary = new TextView(getContext());
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        summary.setTextColor(0x8AFFFFFF);
        summary.setText(c.summary);
        summary.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6);
        summary.setLayoutParams(sp);
        content.addView(summary);

        // Effects (shown always)
        if (c.effects != null && !c.effects.isEmpty()) {
            TextView effects = new TextView(getContext());
            effects.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            effects.setTextColor(0x61FFFFFF);
            StringBuilder sb = new StringBuilder();
            for (String e : c.effects) sb.append("• ").append(e).append("\n");
            effects.setText(sb.toString().trim());
            effects.setLineSpacing(0, 1.4f);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            ep.topMargin = dp(4);
            effects.setLayoutParams(ep);
            content.addView(effects);
        }

        // Expandable: mechanism + recommendation
        LinearLayout expanded = new LinearLayout(getContext());
        expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setVisibility(GONE);
        expanded.setBackgroundColor(0x40000000);
        LinearLayout.LayoutParams exParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        exParams.topMargin = dp(8);
        expanded.setLayoutParams(exParams);
        expanded.setPadding(dp(10), dp(10), dp(10), dp(10));

        if (c.mechanism != null) {
            TextView mech = new TextView(getContext());
            mech.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            mech.setTextColor(0x80FFFFFF);
            mech.setText("Mechanism: " + c.mechanism);
            mech.setLineSpacing(0, 1.5f);
            expanded.addView(mech);
        }
        if (c.recommendation != null) {
            TextView rec = new TextView(getContext());
            rec.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            rec.setTextColor(0x80FFFFFF);
            rec.setText("\nRecommendation: " + c.recommendation);
            rec.setLineSpacing(0, 1.5f);
            expanded.addView(rec);
        }
        content.addView(expanded);

        row.addView(content);
        container.addView(row);

        // Tap to expand
        container.setOnClickListener(v -> {
            boolean nowVisible = expanded.getVisibility() == VISIBLE;
            expanded.setVisibility(nowVisible ? GONE : VISIBLE);
        });

        // Bottom divider
        View divider = new View(getContext());
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(dp);
        divider.setBackgroundColor(0x0DFFFFFF);
        container.addView(divider);

        // Glass-style background
        container.setBackgroundColor(0x0AFFFFFF);

        return container;
    }

    private TextView makePill(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTextColor(0xCCFFFFFF);
        tv.setPadding(dp(10), dp(3), dp(10), dp(3));
        tv.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p.setMarginEnd(dp(4));
        tv.setLayoutParams(p);
        return tv;
    }

    private TextView makeVs() {
        TextView tv = new TextView(getContext());
        tv.setText("✕");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setTextColor(0x4DFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p.setMarginEnd(dp(4));
        tv.setLayoutParams(p);
        return tv;
    }

    private int severityColor(ContraSeverity s) {
        if (s == ContraSeverity.CRITICAL) return 0xFFE05555;
        if (s == ContraSeverity.MODERATE) return 0xFFE0935A;
        return 0xFF5BBFB5;
    }

    private int severityBg(ContraSeverity s) {
        if (s == ContraSeverity.CRITICAL) return 0x33E05555;
        if (s == ContraSeverity.MODERATE) return 0x33E0935A;
        return 0x265BBFB5;
    }

    private int severityTextColor(ContraSeverity s) {
        if (s == ContraSeverity.CRITICAL) return 0xFFFF9999;
        if (s == ContraSeverity.MODERATE) return 0xFFE8C48A;
        return 0xFFA0E8E2;
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).replace("_", " ");
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getContext().getResources().getDisplayMetrics());
    }
}
