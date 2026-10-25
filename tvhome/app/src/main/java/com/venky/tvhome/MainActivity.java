package com.venky.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    static class Tile {
        String pkg;
        CharSequence label;
        Drawable img;
        boolean banner;
        int color = 0xFF555555;
        LinearLayout box;
        FrameLayout frame;
        TextView name;
    }

    static class Item {
        String label;
        TvInputInfo info;
    }

    int cols = 5, W, H, tw, th, gap, margin, bandPad, labelH, hw, hh;
    final List<Tile> dock = new ArrayList<>();
    final List<Tile> grid = new ArrayList<>();
    FrameLayout root, content;
    ScrollView scroll;
    View highlight, glow;
    GradientDrawable glowBg;
    LinearLayout topbar, panel;
    TextView heroName, greeting;
    final TextView[] pills = new TextView[3];
    Typeface tfThin, tfLight, tfReg;

    // cursor: row 0 = top bar (3 pills), row 1 = dock, row 2+ = grid rows
    int row = 1, col = 0, lastDockCol = 0;
    Tile shown;
    boolean placed, menuOpen;
    int menuIdx;
    final List<Item> items = new ArrayList<>();
    final List<TextView> itemViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        W = getResources().getDisplayMetrics().widthPixels;
        H = getResources().getDisplayMetrics().heightPixels;
        cols = Math.max(3, Math.min(7, getResources().getInteger(R.integer.columns)));
        gap = (int) (W * 0.012f);
        margin = (int) (W * 0.06f);
        bandPad = (int) (W * 0.012f);
        labelH = (int) (W * 0.024f);
        int inner = W - 2 * margin - 2 * bandPad;
        tw = (inner - gap * (cols - 1)) / cols;
        th = tw * 9 / 16;
        loadFonts();
        loadApps();
        build();
        setContentView(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (!placed) {
                    placed = true;
                    refresh(false);
                }
            }
        });
    }

    // ---------- setup ----------

    void loadFonts() {
        Typeface o = null;
        try {
            o = Typeface.createFromAsset(getAssets(), "fonts/Outfit.ttf");
        } catch (Exception e) { /* font not bundled: fall back */ }
        tfThin = o != null ? o : Typeface.create("sans-serif-thin", Typeface.NORMAL);
        tfLight = o != null ? o : Typeface.create("sans-serif-light", Typeface.NORMAL);
        tfReg = o != null ? o : Typeface.DEFAULT;
    }

    void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, ResolveInfo> map = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String p = ri.activityInfo.packageName;
            if (!p.equals(getPackageName()) && !map.containsKey(p)) map.put(p, ri);
        }
        Set<String> used = new HashSet<>();
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        for (String p : getResources().getStringArray(R.array.dock_apps)) {
            ResolveInfo ri = map.get(p);
            if (ri != null && dock.size() < cols && !used.contains(p)) {
                dock.add(makeData(ri, pm));
                used.add(p);
            }
        }
        for (String p : getResources().getStringArray(R.array.grid_apps)) {
            ResolveInfo ri = map.get(p);
            if (ri != null && !used.contains(p)) {
                grid.add(makeData(ri, pm));
                used.add(p);
            }
        }
        final PackageManager fpm = pm;
        List<ResolveInfo> rest = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : map.entrySet()) {
            if (!used.contains(e.getKey()) && !hidden.contains(e.getKey())) rest.add(e.getValue());
        }
        Collections.sort(rest, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo c) {
                return a.loadLabel(fpm).toString().compareToIgnoreCase(c.loadLabel(fpm).toString());
            }
        });
        for (ResolveInfo ri : rest) grid.add(makeData(ri, pm));
    }

    Tile makeData(ResolveInfo ri, PackageManager pm) {
        Tile t = new Tile();
        t.pkg = ri.activityInfo.packageName;
        t.label = ri.loadLabel(pm);
        Drawable d = null;
        try {
            d = ri.activityInfo.loadBanner(pm);
        } catch (Exception e) { /* no banner */ }
        if (d != null) t.banner = true; else d = ri.loadIcon(pm);
        t.img = d;
        t.color = sample(d);
        return t;
    }

    int sample(Drawable d) {
        try {
            Drawable c = d.getConstantState() != null ? d.getConstantState().newDrawable() : d;
            Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            c.setBounds(0, 0, 24, 24);
            c.draw(cv);
            Bitmap s = Bitmap.createScaledBitmap(b, 1, 1, true);
            int p = s.getPixel(0, 0);
            b.recycle();
            return p;
        } catch (Exception e) {
            return 0xFF555555;
        }
    }

    TextView tv(float frac, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, W * frac);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setSingleLine(true);
        return t;
    }

    void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);

        glowBg = new GradientDrawable();
        glowBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowBg.setGradientRadius(W * 0.5f);
        glowBg.setGradientCenter(0.5f, 0.85f);
        glowBg.setColors(new int[]{0, 0});
        glow = new View(this);
        glow.setBackground(glowBg);
        glow.setAlpha(0f);
        root.addView(glow, new FrameLayout.LayoutParams(W, (int) (H * 0.8f), Gravity.BOTTOM));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        content.addView(column, new FrameLayout.LayoutParams(-1, -2));

        // hero block = one full screen: big app name above the dock band
        FrameLayout heroBox = new FrameLayout(this);
        heroBox.setClipChildren(false);
        column.addView(heroBox, new LinearLayout.LayoutParams(-1, H));

        int bandH = th + 2 * bandPad;
        int bottom = (int) (H * 0.07f);
        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setPadding(bandPad, bandPad, bandPad, bandPad);
        band.setClipChildren(false);
        band.setClipToPadding(false);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(0xFF1B1B1D);
        bb.setCornerRadius(W * 0.02f);
        band.setBackground(bb);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, bandH, Gravity.BOTTOM);
        bp.setMargins(margin, 0, margin, bottom);
        heroBox.addView(band, bp);
        for (int i = 0; i < dock.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tw, th);
            if (i > 0) lp.leftMargin = gap;
            band.addView(makeTile(dock.get(i), false), lp);
        }

        heroName = tv(0.026f, tfLight, Color.WHITE);
        heroName.setAlpha(0f);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        hp.leftMargin = margin + bandPad;
        hp.bottomMargin = bottom + bandH + (int) (H * 0.035f);
        heroBox.addView(heroName, hp);

        // grid below the hero block
        LinearLayout gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setClipChildren(false);
        gridBox.setPadding(0, (int) (H * 0.04f), 0, 0);
        column.addView(gridBox, new LinearLayout.LayoutParams(-1, -2));
        int rows = (grid.size() + cols - 1) / cols;
        for (int r = 0; r < rows; r++) {
            LinearLayout rl = new LinearLayout(this);
            rl.setOrientation(LinearLayout.HORIZONTAL);
            rl.setClipChildren(false);
            rl.setPadding(margin + bandPad, 0, 0, 0);
            for (int c = 0; c < cols && r * cols + c < grid.size(); c++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tw, th + labelH);
                if (c > 0) lp.leftMargin = gap;
                rl.addView(makeTile(grid.get(r * cols + c), true), lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, th + labelH);
            rp.bottomMargin = gap;
            gridBox.addView(rl, rp);
        }
        column.addView(new View(this), new LinearLayout.LayoutParams(-1, (int) (H * 0.6f)));

        // the one glass highlight that springs between tiles
        int pad = (int) (W * 0.006f);
        hw = (int) (tw * 1.08f) + 2 * pad;
        hh = (int) (th * 1.08f) + 2 * pad;
        highlight = new View(this);
        GradientDrawable hl = new GradientDrawable();
        hl.setColor(0x2EFFFFFF);
        hl.setStroke(Math.max(2, (int) (W * 0.0015f)), 0x88FFFFFF);
        hl.setCornerRadius(W * 0.011f);
        highlight.setBackground(hl);
        highlight.setAlpha(0f);
        content.addView(highlight, new FrameLayout.LayoutParams(hw, hh));

        // top bar: greeting, clock, date + pills
        topbar = new LinearLayout(this);
        topbar.setOrientation(LinearLayout.HORIZONTAL);
        topbar.setPadding(margin, (int) (H * 0.045f), margin, (int) (H * 0.02f));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = tv(0.0125f, tfReg, 0xCCFFFFFF);
        left.addView(greeting);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, W * 0.045f);
        clock.setTypeface(tfThin);
        clock.setTextColor(Color.WHITE);
        clock.setIncludeFontPadding(false);
        left.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, W * 0.0115f);
        date.setTypeface(tfReg);
        date.setTextColor(0x99FFFFFF);
        left.addView(date);
        topbar.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.HORIZONTAL);
        topbar.addView(pr, new LinearLayout.LayoutParams(-2, -2));
        String[] names = {"Inputs", "Wi-Fi", "Settings"};
        for (int i = 0; i < 3; i++) {
            TextView p = tv(0.0105f, tfReg, Color.WHITE);
            p.setText(names[i]);
            p.setGravity(Gravity.CENTER);
            p.setPadding((int) (W * 0.014f), (int) (W * 0.007f), (int) (W * 0.014f), (int) (W * 0.007f));
            setPill(p, false);
            pills[i] = p;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = gap;
            pr.addView(p, lp);
        }
        root.addView(topbar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Inputs dropdown
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pp = (int) (W * 0.006f);
        panel.setPadding(pp, pp, pp, pp);
        panel.setMinimumWidth((int) (W * 0.16f));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xF02C2C2E);
        pbg.setCornerRadius(W * 0.012f);
        panel.setBackground(pbg);
        panel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(-2, -2));
    }

    View makeTile(Tile t, boolean label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xFF2A2A2D);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(t.img);
        iv.setScaleType(t.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        if (!t.banner) {
            int p = th / 6;
            iv.setPadding(p, p, p, p);
        }
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        final float r = W * 0.009f;
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        f.setClipToOutline(true);
        box.addView(f, new LinearLayout.LayoutParams(tw, th));
        if (label) {
            TextView n = tv(0.0095f, tfReg, Color.WHITE);
            n.setText(t.label);
            n.setGravity(Gravity.CENTER);
            n.setEllipsize(TextUtils.TruncateAt.END);
            n.setAlpha(0f);
            box.addView(n, new LinearLayout.LayoutParams(tw, labelH));
            t.name = n;
        }
        box.setPivotX(tw / 2f);
        box.setPivotY(th / 2f);
        t.box = box;
        t.frame = f;
        return box;
    }

    void setPill(TextView p, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(W * 0.03f);
        g.setColor(focused ? 0xE6FFFFFF : 0x33FFFFFF);
        p.setBackground(g);
        p.setTextColor(focused ? Color.BLACK : Color.WHITE);
    }

    // ---------- state ----------

    int rowCount() { return 2 + (grid.size() + cols - 1) / cols; }

    int rowSize(int r) {
        if (r == 0) return 3;
        if (r == 1) return dock.size();
        return Math.min(cols, grid.size() - (r - 2) * cols);
    }

    Tile cur() {
        if (row == 1) return col < dock.size() ? dock.get(col) : null;
        if (row >= 2) {
            int i = (row - 2) * cols + col;
            return i < grid.size() ? grid.get(i) : null;
        }
        return null;
    }

    int[] posIn(View v, View anc) {
        int x = 0, y = 0;
        View c = v;
        while (c != null && c != anc) {
            x += c.getLeft();
            y += c.getTop();
            ViewParent p = c.getParent();
            if (!(p instanceof View)) break;
            c = (View) p;
        }
        return new int[]{x, y};
    }

    int vivid(int c) {
        float[] h = new float[3];
        Color.colorToHSV(c, h);
        h[1] = Math.min(1f, Math.max(h[1], 0.35f));
        h[2] = Math.max(h[2], 0.6f);
        return Color.HSVToColor(0x80, h);
    }

    void refresh(boolean anim) {
        if (dock.isEmpty()) return;
        if (row == 1) lastDockCol = col;
        int d1 = anim ? 160 : 0;
        Tile c = cur();
        if (shown != null && shown != c) {
            shown.box.animate().scaleX(1f).scaleY(1f).setDuration(d1);
            if (shown.name != null) shown.name.animate().alpha(0f).setDuration(d1);
        }
        shown = c;
        if (c != null) {
            c.box.animate().scaleX(1.08f).scaleY(1.08f).setDuration(d1);
            if (c.name != null) c.name.animate().alpha(1f).setDuration(d1);
            int[] p = posIn(c.frame, content);
            float x = p[0] + tw / 2f - hw / 2f;
            float y = p[1] + th / 2f - hh / 2f;
            if (anim) {
                highlight.animate().x(x).y(y).alpha(1f).setDuration(280)
                        .setInterpolator(new OvershootInterpolator(1.3f));
            } else {
                highlight.animate().cancel();
                highlight.setX(x);
                highlight.setY(y);
                highlight.setAlpha(1f);
            }
        } else {
            highlight.animate().alpha(0f).setDuration(120);
        }
        for (int i = 0; i < 3; i++) setPill(pills[i], row == 0 && col == i);

        if (row <= 1) {
            Tile d = dock.get(Math.min(row == 1 ? col : lastDockCol, dock.size() - 1));
            heroName.setText(d.label);
            heroName.animate().alpha(1f).setDuration(d1);
            int g = vivid(d.color);
            glowBg.setColors(new int[]{g, g & 0x00FFFFFF});
            glow.animate().alpha(1f).setDuration(anim ? 300 : 0);
        } else {
            heroName.animate().alpha(0f).setDuration(d1);
            glow.animate().alpha(0f).setDuration(d1);
        }
        topbar.setBackgroundColor(row >= 2 ? Color.BLACK : Color.TRANSPARENT);

        int ty = 0;
        if (row >= 2 && c != null) ty = Math.max(0, posIn(c.frame, content)[1] - (int) (H * 0.28f));
        if (anim) scroll.smoothScrollTo(0, ty); else scroll.scrollTo(0, ty);
    }

    // ---------- keys ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BACK;
        if (!nav) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) onNav(k, e.getRepeatCount());
        return true;
    }

    void onNav(int k, int rep) {
        if (menuOpen) {
            menuKey(k, rep);
            return;
        }
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (col > 0) { col--; refresh(true); }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (col < rowSize(row) - 1) { col++; refresh(true); }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                moveRow(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveRow(1);
                break;
            case KeyEvent.KEYCODE_BACK:
                if (rep == 0 && row != 1) {
                    row = 1;
                    col = Math.min(lastDockCol, dock.size() - 1);
                    refresh(true);
                }
                break;
            default: // center / enter
                if (rep == 0) activate();
        }
    }

    void moveRow(int d) {
        int nr = row + d;
        if (nr < 0 || nr >= rowCount()) return;
        if (row == 1 && d < 0) {
            lastDockCol = col;
            col = Math.min(2, col * 3 / Math.max(1, dock.size()));
        } else if (row == 0) {
            col = Math.min(lastDockCol, rowSize(1) - 1);
        } else {
            col = Math.min(col, rowSize(nr) - 1);
        }
        row = nr;
        refresh(true);
    }

    void activate() {
        if (row == 0) {
            if (col == 0) {
                openMenu();
            } else {
                try {
                    startActivity(new Intent(col == 1 ? Settings.ACTION_WIFI_SETTINGS : Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    toast("Cannot open settings");
                }
            }
            return;
        }
        Tile t = cur();
        if (t == null) return;
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(t.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(t.pkg);
        if (i == null) { toast("Cannot open " + t.label); return; }
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Cannot open " + t.label);
        }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------- inputs dropdown ----------

    void openMenu() {
        items.clear();
        itemViews.clear();
        panel.removeAllViews();
        Item live = null;
        try {
            TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo in : m.getTvInputList()) {
                if (in.getType() == TvInputInfo.TYPE_TUNER) {
                    if (live == null) {
                        live = new Item();
                        live.info = in;
                        live.label = "Live TV";
                    }
                } else if (in.isPassthroughInput()) {
                    CharSequence cl = in.loadCustomLabel(this);
                    CharSequence l = (cl != null && cl.length() > 0) ? cl : in.loadLabel(this);
                    Item it = new Item();
                    it.info = in;
                    it.label = String.valueOf(l);
                    items.add(it);
                }
            }
        } catch (Exception e) { /* handled below */ }
        if (live != null) items.add(live);
        if (items.isEmpty()) { toast("No inputs found"); return; }
        for (Item it : items) {
            TextView t = tv(0.0115f, tfReg, Color.WHITE);
            t.setText(it.label);
            int p = (int) (W * 0.008f);
            t.setPadding(p * 2, p, p * 2, p);
            panel.addView(t, new LinearLayout.LayoutParams(-1, -2));
            itemViews.add(t);
        }
        menuIdx = 0;
        menuOpen = true;
        paintMenu();
        int[] loc = new int[2];
        pills[0].getLocationInWindow(loc);
        panel.setX(loc[0]);
        panel.setY(loc[1] + pills[0].getHeight() + W * 0.006f);
        panel.setVisibility(View.VISIBLE);
    }

    void paintMenu() {
        for (int i = 0; i < itemViews.size(); i++) {
            TextView t = itemViews.get(i);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(W * 0.008f);
            g.setColor(i == menuIdx ? 0x66FFFFFF : 0);
            t.setBackground(g);
        }
    }

    void closeMenu() {
        menuOpen = false;
        panel.setVisibility(View.GONE);
    }

    void menuKey(int k, int rep) {
        if (k == KeyEvent.KEYCODE_DPAD_UP) {
            if (menuIdx > 0) { menuIdx--; paintMenu(); }
        } else if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (menuIdx < items.size() - 1) { menuIdx++; paintMenu(); }
        } else if (k == KeyEvent.KEYCODE_BACK) {
            closeMenu();
        } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (rep == 0) {
                Item it = items.get(menuIdx);
                closeMenu();
                switchInput(it);
            }
        }
    }

    void switchInput(Item it) {
        try {
            Intent in;
            if (it.info.isPassthroughInput()) {
                in = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.info.getId()));
            } else {
                String pkg = it.info.getServiceInfo().packageName;
                in = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (in == null) in = getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (in == null) { toast("Cannot open " + it.label); return; }
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(in);
        } catch (Exception e) {
            toast("Cannot switch to " + it.label);
        }
    }

    // ---------- lifecycle ----------

    @Override
    protected void onResume() {
        super.onResume();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 12 ? "Good morning" : (h < 17 ? "Good afternoon" : "Good evening"));
        String s = "Wi-Fi";
        try {
            WifiManager w = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo i = w.getConnectionInfo();
            if (i != null) {
                String n = i.getSSID();
                if (n != null && n.length() > 0 && !n.equals("<unknown ssid>") && !n.equals("0x")) {
                    if (n.length() > 1 && n.startsWith("\"") && n.endsWith("\"")) n = n.substring(1, n.length() - 1);
                    s = n;
                }
            }
        } catch (Exception e) { /* keep default label */ }
        pills[1].setText(s);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (menuOpen) closeMenu();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (menuOpen) closeMenu();
        row = 1;
        col = Math.max(0, Math.min(lastDockCol, dock.size() - 1));
        if (placed) refresh(false);
    }
}
