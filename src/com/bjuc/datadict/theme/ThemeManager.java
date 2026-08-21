package com.bjuc.datadict.theme;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 程序界面主题的持久化：把用户选择的主题 id 保存到
 * %APPDATA%/BJUC-DataDict/theme.txt（与数据源配置同目录），下次启动自动恢复。
 */
public final class ThemeManager {

    private static final String FILE_NAME = "theme.txt";

    private ThemeManager() {
    }

    private static File themeFile() {
        File cfgDir = com.bjuc.datadict.core.ConfigStore.configFile().getParentFile();
        try {
            if (cfgDir != null && !cfgDir.exists()) {
                cfgDir.mkdirs();
            }
        } catch (Throwable ignore) {
            // 目录创建失败不致命
        }
        return new File(cfgDir, FILE_NAME);
    }

    /** 读取上次保存的主题，读不到或异常时返回默认（经典蓝）。 */
    public static AppTheme load() {
        File f = themeFile();
        if (f == null || !f.isFile()) {
            return AppTheme.active();
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            if (line != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    return AppTheme.byId(line);
                }
            }
        } catch (Throwable ignore) {
            // 读取失败回退默认主题
        }
        return AppTheme.active();
    }

    /** 保存当前激活主题 id。 */
    public static void save(AppTheme t) {
        File f = themeFile();
        if (f == null) {
            return;
        }
        try {
            PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8.name());
            w.print(t.id);
            w.close();
        } catch (Throwable ignore) {
            // 写失败不影响运行
        }
    }
}
