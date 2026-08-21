package com.bjuc.datadict.db;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC 驱动注册中心：自动扫描可执行程序同级 drivers/ 目录下所有 jar，
 * 逐个尝试加载已知的 Oracle 驱动类并注册到 DriverManager。
 * 用户有特殊的 ojdbc14/classes12 等老驱动，丢进 drivers/ 目录即可，无需改代码。
 */
public final class DbDrivers {
    private static final String[] CANDIDATE_CLASSES = {
            "oracle.jdbc.OracleDriver",
            "oracle.jdbc.driver.OracleDriver"
    };

    private DbDrivers() {
    }

    public static void loadFromExternalDirs() {
        List<File> dirs = new ArrayList<>();
        String userDir = System.getProperty("user.dir", ".");
        File cwd = new File(userDir);
        dirs.add(new File(cwd, "drivers"));
        if (isRunningFromJar()) {
            try {
                File jarDir = new File(
                        com.bjuc.datadict.Main.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                        .getParentFile();
                if (jarDir != null && !jarDir.getAbsolutePath().equals(cwd.getAbsolutePath())) {
                    dirs.add(new File(jarDir, "drivers"));
                }
                // jpackage 原生 exe 布局：jar 在 app/ 下，drivers 放在 exe 同级目录
                File exeDir = jarDir == null ? null : jarDir.getParentFile();
                if (exeDir != null && !exeDir.getAbsolutePath().equals(cwd.getAbsolutePath())
                        && !exeDir.getAbsolutePath().equals(jarDir.getAbsolutePath())) {
                    dirs.add(new File(exeDir, "drivers"));
                }
            } catch (Throwable ignore) {
            }
        }
        for (File dir : dirs) {
            loadDir(dir);
        }
        // 确保默认的 oracle.jdbc.OracleDriver 至少出现在类路径上
        tryLoad("oracle.jdbc.OracleDriver");
    }

    private static boolean isRunningFromJar() {
        try {
            URL loc = com.bjuc.datadict.Main.class.getProtectionDomain().getCodeSource().getLocation();
            return loc != null && loc.getFile().toLowerCase().endsWith(".jar");
        } catch (Throwable e) {
            return false;
        }
    }

    private static void loadDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] jars = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return;
        }
        try {
            URL[] urls = new URL[jars.length];
            for (int i = 0; i < jars.length; i++) {
                urls[i] = jars[i].toURI().toURL();
            }
            URLClassLoader loader = new URLClassLoader(urls, DbDrivers.class.getClassLoader());
            for (String cls : CANDIDATE_CLASSES) {
                tryLoadWith(cls, loader);
            }
        } catch (Throwable ignore) {
        }
    }

    private static void tryLoad(String cls) {
        try {
            Class.forName(cls);
        } catch (Throwable ignore) {
        }
    }

    private static void tryLoadWith(String className, URLClassLoader loader) {
        try {
            Class<?> c = Class.forName(className, true, loader);
            Object inst = c.getDeclaredConstructor().newInstance();
            if (inst instanceof Driver) {
                DriverManager.registerDriver((Driver) inst);
            }
        } catch (Throwable ignore) {
        }
    }
}