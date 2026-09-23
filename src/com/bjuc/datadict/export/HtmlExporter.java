package com.bjuc.datadict.export;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.bjuc.datadict.core.model.DataDictionary;
import com.bjuc.datadict.core.model.DataDictionary.Cls;
import com.bjuc.datadict.core.model.DataDictionary.Comp;
import com.bjuc.datadict.core.model.DataDictionary.Module;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 数据字典 -> 离线网页 导出器（经典风格）。
 *
 * <p>导出产物分两种形态，由模板是否带 {{DATA_DIR}} 占位自动决定：
 * <ul>
 *   <li><b>拆分模式（默认，模板含 {{DATA_DIR}}）</b>：index.html 仅内嵌“清单”
 *       （各模块 id/名称 + 统计计数 _cs/_es/_ps），模块明细按需写成
 *       {@code <文件基名>_data/m&lt;i&gt;.js}（JSONP：{@code window.__ddm(i, {...})}）。
 *       浏览器打开首页秒开，点开哪个模块才加载哪个模块的数据。</li>
 *   <li><b>单文件模式（模板无 {{DATA_DIR}}，兼容旧模板）</b>：全部数据内嵌
 *       {@code window.agg = {{JSON}};} 一份 html 走天下。</li>
 * </ul>
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
        String key = (theme == null || theme.trim().length() == 0) ? "cbd" : theme.trim();
        String html;
        if (tpl.contains("{{DATA_DIR}}")) {
            html = exportSplit(dict, outFile, tpl, key);
        } else {
            String json = JSON.toJSONString(dict, SerializerFeature.DisableCircularReferenceDetect);
            html = tpl.replace("{{THEME}}", key).replace("{{JSON}}", json);
        }
        Files.write(outFile.toPath(), html.getBytes(StandardCharsets.UTF_8));
    }

    /** 拆分模式：index.html 内嵌清单，模块明细各写 <基名>_data/m<i>.js（JSONP 按需加载）。 */
    private static String exportSplit(DataDictionary dict, File outFile, String tpl, String theme) throws IOException {
        String base = outFile.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String dirName = base + "_data";
        File dataDir = new File(outFile.getParent(), dirName);
        Files.createDirectories(dataDir.toPath());

        List<Module> mods = dict.modules != null ? dict.modules : java.util.Collections.<Module>emptyList();
        JSONArray manModules = new JSONArray();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int cs = 0, es = 0, ps = 0;
            if (m.comps != null) {
                cs = m.comps.size();
                for (Comp c : m.comps) {
                    if (c.classes != null) {
                        es += c.classes.size();
                        for (Cls cl : c.classes) {
                            if (cl.props != null) {
                                ps += cl.props.size();
                            }
                        }
                    }
                }
            }
            JSONObject meta = new JSONObject(true); // 保序，与全量 JSON 字段顺序一致
            meta.put("id", m.id);
            meta.put("name", m.name);
            meta.put("displayname", m.displayname);
            meta.put("_cs", cs);
            meta.put("_es", es);
            meta.put("_ps", ps);
            manModules.add(meta);

            String mjs = "window.__ddm&&window.__ddm(" + i + ","
                    + JSON.toJSONString(m, SerializerFeature.DisableCircularReferenceDetect) + ");";
            Files.write(new File(dataDir, "m" + i + ".js").toPath(), mjs.getBytes(StandardCharsets.UTF_8));
        }

        JSONObject manifest = new JSONObject(true);
        manifest.put("toolName", dict.toolName);
        manifest.put("ncVersion", dict.ncVersion);
        manifest.put("groupName", dict.groupName);
        manifest.put("databaseName", dict.databaseName);
        manifest.put("generateTime", dict.generateTime);
        manifest.put("remark", dict.remark);
        manifest.put("totalModules", dict.totalModules);
        manifest.put("totalComponents", dict.totalComponents);
        manifest.put("totalClasses", dict.totalClasses);
        manifest.put("totalProps", dict.totalProps);
        manifest.put("modules", manModules);

        return tpl.replace("{{THEME}}", theme)
                .replace("{{JSON}}", manifest.toJSONString())
                .replace("{{DATA_DIR}}", dirName);
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
