package com.bjuc.datadict;

import com.bjuc.datadict.db.DbDrivers;
import com.bjuc.datadict.theme.AppTheme;
import com.bjuc.datadict.theme.ThemeManager;
import com.bjuc.datadict.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;

/**
 * NC 数据字典导出工具 入口
 */
public class Main {
    /** 品牌主色（随主题变化，见 applyTheme） */
    public static Color BLUE = new Color(0x1A3C6E);
    public static Color BLUE_DARK = new Color(0x13305A);
    public static Color BLUE_LIGHT = new Color(0xEAF2FF);
    public static Color BLUE_LIGHTER = new Color(0xF5F8FF);
    /** 中性色 */
    public static Color BG = new Color(0xF0F4F9);
    public static Color TEXT = new Color(0x333333);
    public static Color TEXT_SUB = new Color(0x666666);
    public static Color LINE = new Color(0xD8DEE7);
    /** 卡片/面板表面背景（受主题控制，深色主题为深色表面） */
    public static Color SURFACE = new Color(0xFFFFFF);
    /** 主色按钮上的文字颜色（浅色主色主题自动深色） */
    public static Color TEXT_ON_PRIMARY = new Color(0xFFFFFF);
    public static Color OK = new Color(0x12B76A);
    public static Color ERR = new Color(0xE5484D);
    public static final String APP_NAME = "NC 数据字典导出工具";

    public static void main(String[] args) {
        // 动态加载 drivers 目录下的 JDBC 驱动（兼容 Oracle 8i 老库等）
        DbDrivers.loadFromExternalDirs();
        // 读取上次选择的界面主题（未设置则默认经典蓝）
        AppTheme theme = ThemeManager.load();
        AppTheme.setActive(theme);
        Main.applyTheme(theme);

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    /** 应用程序主题：切换 FlatLaf 浅/深基底 + 写入全局色常量 + UI 默认值（EDT 调用） */
    public static void applyTheme(AppTheme t) {
        try {
            if (t.dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Throwable ignore) {
            // FlatLaf 不可用时退化为默认外观
        }

        // 全局色常量（MainFrame 各处直接引用，重建界面即整体换色）
        BLUE = t.primary;
        BLUE_DARK = t.primaryDark;
        BLUE_LIGHT = t.surfaceAlt;
        BLUE_LIGHTER = t.bg;
        BG = t.bg;
        TEXT = t.text;
        TEXT_SUB = t.textSecondary;
        LINE = t.line;
        SURFACE = t.surface;
        TEXT_ON_PRIMARY = t.textOnPrimary;

        // FlatLaf UI 默认值（覆盖标准控件）
        UIManager.put("Component.focusColor", t.primary);
        UIManager.put("ProgressBar.foreground", t.primary);
        UIManager.put("ProgressBar.background", t.dark ? t.surfaceAlt : new Color(0xE8EEFA));
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 8);

        UIManager.put("Panel.background", t.bg);
        UIManager.put("Panel.foreground", t.text);
        UIManager.put("Label.foreground", t.text);
        UIManager.put("Label.disabledText", t.textSecondary);
        UIManager.put("TextField.background", t.surface);
        UIManager.put("TextField.foreground", t.text);
        UIManager.put("TextField.caretForeground", t.text);
        UIManager.put("TextField.inactiveBackground", t.surfaceAlt);
        UIManager.put("PasswordField.background", t.surface);
        UIManager.put("PasswordField.foreground", t.text);
        UIManager.put("TextArea.background", t.surface);
        UIManager.put("TextArea.foreground", t.text);
        UIManager.put("Table.background", t.surface);
        UIManager.put("Table.foreground", t.text);
        UIManager.put("Table.selectionBackground", t.primary);
        UIManager.put("Table.selectionForeground", t.textOnPrimary);
        UIManager.put("Table.gridColor", t.line);
        UIManager.put("Tree.background", t.surface);
        UIManager.put("Tree.foreground", t.text);
        UIManager.put("Tree.selectionBackground", t.primary);
        UIManager.put("Tree.selectionForeground", t.textOnPrimary);
        UIManager.put("ScrollPane.background", t.surface);
        UIManager.put("Viewport.background", t.surface);
        UIManager.put("ComboBox.background", t.surface);
        UIManager.put("ComboBox.foreground", t.text);
        UIManager.put("List.background", t.surface);
        UIManager.put("List.foreground", t.text);
        UIManager.put("List.selectionBackground", t.primary);
        UIManager.put("List.selectionForeground", t.textOnPrimary);
        UIManager.put("Spinner.background", t.surface);
        UIManager.put("ToolTip.background", t.surface);
        UIManager.put("ToolTip.foreground", t.text);
    }
}
