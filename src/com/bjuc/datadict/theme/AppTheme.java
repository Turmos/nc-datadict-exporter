package com.bjuc.datadict.theme;

import java.awt.Color;

/**
 * 程序界面主题：10 套精确配色（色值源自需求清单）。
 *
 * <p>每个主题定义 7 个角色色（主色/辅色/背景/表面/文字/次要文字/强调色），
 * 并派生若干控件需要的颜色（顶栏渐变深端、边框线、悬停高亮、主色上的文字）。
 * 通过 {@link #apply()} 把当前主题写入 {@link com.bjuc.datadict.Main} 的全局色
 * 常量与 FlatLaf UI 默认值，切换后重建界面即整体换肤。
 */
public final class AppTheme {

    // ---------------- 角色色 ----------------
    public final String id;
    public final String name;      // 中文名（下拉框显示）
    public final boolean dark;     // 深色基底（决定 FlatLaf 用浅色/深色 Laf）
    public final Color primary;          // 主色调：顶栏/主按钮/激活态
    public final Color secondary;        // 辅色调：次要按钮/边框/分割线
    public final Color bg;               // 页面整体背景
    public final Color surface;          // 卡片/面板/表格背景
    public final Color text;             // 主要文字
    public final Color textSecondary;    // 次要/提示文字
    public final Color accent;           // 强调色：悬停高亮/链接/重要标记

    // ---------------- 派生色 ----------------
    public final Color primaryDark;   // 顶栏渐变深端、主色 hover
    public final Color line;          // 边框 / 分割线
    public final Color surfaceAlt;    // 次级表面 / 悬停高亮底
    public final Color textOnPrimary; // 主色按钮上的文字（通常白，浅色主色主题反白）

    private static final AppTheme[] ALL = new AppTheme[10];
    private static final String[] ORDER = {
            "cbd", "dark", "min", "gray", "green", "orange",
            "purple", "steel", "marine", "millet"
    };

    // 当前激活主题（默认经典蓝）
    private static AppTheme ACTIVE;

    private AppTheme(String id, String name, boolean dark,
                     int primary, int secondary, int bg, int surface,
                     int text, int textSecondary, int accent,
                     int textOnPrimary) {
        this.id = id;
        this.name = name;
        this.dark = dark;
        this.primary = new Color(primary);
        this.secondary = new Color(secondary);
        this.bg = new Color(bg);
        this.surface = new Color(surface);
        this.text = new Color(text);
        this.textSecondary = new Color(textSecondary);
        this.accent = new Color(accent);
        this.textOnPrimary = new Color(textOnPrimary);
        this.primaryDark = darken(this.primary, 0.82f);
        this.line = dark
                ? mix(this.surface, Color.WHITE, 0.12f)
                : new Color(0xD8DEE7);
        this.surfaceAlt = dark
                ? mix(this.surface, Color.WHITE, 0.06f)
                : mix(this.surface, this.primary, 0.07f);
    }

    private static void register(AppTheme t) {
        if (ACTIVE == null) {
            ACTIVE = t;
        }
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(t.id)) {
                ALL[i] = t;
                break;
            }
        }
    }

    static {
        // 主题1 经典蓝（默认；id 保持 "cbd" 以兼容已保存的选择）
        register(new AppTheme("cbd", "经典蓝", false,
                0x1A3C6E, 0x4A7BB5, 0xF0F4F9, 0xFFFFFF,
                0x333333, 0x666666, 0xFFA500, 0xFFFFFF));
        // 主题2 暗夜星空（深色）
        register(new AppTheme("dark", "暗夜星空", true,
                0x1E1E2E, 0x7F8CD6, 0x14141E, 0x2B2B3A,
                0xE0E0E0, 0xA0A0B0, 0xA78BFA, 0xFFFFFF));
        // 主题3 极简白
        register(new AppTheme("min", "极简白", false,
                0x2C3E50, 0xE8ECF1, 0xF8F9FA, 0xFFFFFF,
                0x333333, 0x888888, 0x1A73E8, 0xFFFFFF));
        // 主题4 石墨灰
        register(new AppTheme("gray", "石墨灰", false,
                0x4A4F5C, 0x6C737F, 0xF5F6F8, 0xFFFFFF,
                0x2C3E50, 0x7F8C8D, 0xE67E22, 0xFFFFFF));
        // 主题5 森林绿
        register(new AppTheme("green", "森林绿", false,
                0x2E7D32, 0x66BB6A, 0xF1F8E9, 0xFFFFFF,
                0x1B3B1B, 0x558B2F, 0xFFB300, 0xFFFFFF));
        // 主题6 暖阳橙
        register(new AppTheme("orange", "暖阳橙", false,
                0xE67E22, 0xF39C12, 0xFEF9E7, 0xFFFFFF,
                0x4A2C0A, 0x8D6E3F, 0xD35400, 0xFFFFFF));
        // 主题7 科技紫
        register(new AppTheme("purple", "科技紫", false,
                0x6C3CE1, 0xA78BFA, 0xF3EFFC, 0xFFFFFF,
                0x2D1B4E, 0x7C6A9E, 0x00D4FF, 0xFFFFFF));
        // 主题8 钢铁灰
        register(new AppTheme("steel", "钢铁灰", false,
                0x607D8B, 0x90A4AE, 0xECEFF1, 0xFFFFFF,
                0x263238, 0x546E7A, 0xFF5722, 0xFFFFFF));
        // 主题9 海洋蓝绿
        register(new AppTheme("marine", "海洋蓝绿", false,
                0x00838F, 0x26C6DA, 0xE0F7FA, 0xFFFFFF,
                0x00363A, 0x006064, 0xFF6F00, 0xFFFFFF));
        // 主题10 复古米黄（主色偏浅，按钮文字用深色）
        register(new AppTheme("millet", "复古米黄", false,
                0xD4A373, 0xFAEDCD, 0xFDF8F0, 0xFFF9F0,
                0x3E2C1B, 0x8B7355, 0xCC8E35, 0x3E2C1B));
    }

    /** 全部主题（按固定顺序，与下拉框一致） */
    public static AppTheme[] all() {
        return ALL;
    }

    /** 按 id 取主题，找不到返回默认（经典蓝） */
    public static AppTheme byId(String id) {
        for (AppTheme t : ALL) {
            if (t != null && t.id.equals(id)) {
                return t;
            }
        }
        return ACTIVE == null ? ALL[0] : ACTIVE;
    }

    public static AppTheme active() {
        return ACTIVE == null ? ALL[0] : ACTIVE;
    }

    /** 下拉框显示中文名 */
    @Override
    public String toString() {
        return name;
    }

    /** 设置全局激活主题（不 apply 到 UI） */
    public static void setActive(AppTheme t) {
        ACTIVE = t;
    }

    /** 把当前主题写入 Main 全局色常量 + FlatLaf UI 默认值（由 Main/ThemeManager 调用） */
    public void apply() {
        com.bjuc.datadict.Main.applyTheme(this);
    }

    // ---------------- 颜色工具 ----------------
    private static Color mix(Color a, Color b, float t) {
        float r = a.getRed() * (1 - t) + b.getRed() * t;
        float g = a.getGreen() * (1 - t) + b.getGreen() * t;
        float bl = a.getBlue() * (1 - t) + b.getBlue() * t;
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private static Color darken(Color c, float factor) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2] * factor));
    }

    private static int clamp(float v) {
        int i = Math.round(v);
        return Math.max(0, Math.min(255, i));
    }
}
