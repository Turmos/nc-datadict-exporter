package com.bjuc.datadict.export;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.bjuc.datadict.core.model.DataDictionary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 数据字典 -> 单文件离线网页 导出器（经典风格）。
 */
public final class HtmlExporter {

    private HtmlExporter() {
    }

    public static void export(DataDictionary dict, File outFile) throws IOException {
        export(dict, outFile, "cbd");
    }

    /**
     * 导出数据字典网页，并指定默认主题。
     * 主题键：cbd(经典蓝) / min-light(极简浅色) / dark(暗色) / teal(青绿)；
     * 模板用 {{THEME}} 占位，打开即套用该主题，页面内仍可随时切换。
     */
    public static void export(DataDictionary dict, File outFile, String theme) throws IOException {
        String tpl = loadTemplate();
        String json = JSON.toJSONString(dict, SerializerFeature.DisableCircularReferenceDetect);
        String key = (theme == null || theme.trim().length() == 0) ? "cbd" : theme.trim();
        String html = tpl.replace("{{THEME}}", key).replace("{{JSON}}", json);
        Files.write(outFile.toPath(), html.getBytes(StandardCharsets.UTF_8));
    }

    public static String loadTemplate() throws IOException {
        // 1) 类路径（打包进 jar / class 输出目录）
        InputStream in = HtmlExporter.class.getResourceAsStream("/template.html");
        if (in != null) {
            try {
                return readAll(in);
            } finally {
                in.close();
            }
        }
        // 2) 文件系统兜底
        File[] candidates = {
                new File("resources/template.html"),
                new File(new File(System.getProperty("user.dir", ".")), "template.html"),
                new File(System.getProperty("user.dir", "."), "template.html")
        };
        for (File f : candidates) {
            if (f.isFile()) {
                return readAll(new FileInputStream(f));
            }
        }
        throw new IOException("找不到数据字典网页模板 template.html");
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}