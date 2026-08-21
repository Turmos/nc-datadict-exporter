package com.bjuc.datadict.smoke;

import java.lang.reflect.Method;

/** 验证 DictLoader.normalizeUrl 的 URL 归一化逻辑（不连数据库）。 */
public class NormTest {
    public static void main(String[] args) throws Exception {
        Method m = Class.forName("com.bjuc.datadict.core.DictLoader")
                .getDeclaredMethod("normalizeUrl", String.class);
        m.setAccessible(true);
        String[] in = {
                "host:1521/orcl",
                "  host:1521/orcl  ",
                "host:1521:orcl",
                "jdbc:oracle:thin:@host:1521/orcl",
                "JDBC:mysql://localhost:3306/db",
                "",
                null
        };
        String[] expect = {
                "jdbc:oracle:thin:@host:1521/orcl",
                "jdbc:oracle:thin:@host:1521/orcl",
                "jdbc:oracle:thin:@host:1521:orcl",
                "jdbc:oracle:thin:@host:1521/orcl",
                "JDBC:mysql://localhost:3306/db",
                "",
                ""
        };
        boolean ok = true;
        for (int i = 0; i < in.length; i++) {
            Object got = m.invoke(null, in[i]);
            boolean pass = String.valueOf(got).equals(expect[i]);
            ok &= pass;
            System.out.println((pass ? "PASS" : "FAIL") + " in=" + in[i] + " got=" + got + " expect=" + expect[i]);
        }
        System.out.println(ok ? "NORM_OK" : "NORM_FAIL");
        if (!ok) System.exit(1);
    }
}
