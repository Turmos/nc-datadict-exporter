package com.bjuc.datadict.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bjuc.datadict.core.model.DataDictionary;
import com.bjuc.datadict.core.model.DataDictionary.Cls;
import com.bjuc.datadict.core.model.DataDictionary.Comp;
import com.bjuc.datadict.core.model.DataDictionary.Module;
import com.bjuc.datadict.core.model.DataDictionary.Prop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 官方 NC65 数据字典（resources/chm_dictionary.json）中文名兜底。
 *
 * <p>数据库元数据查不到中文显示名（displayName 为空）时，从官方 CHM 数据字典
 * 解出的补充字典回填：表按 tableName、字段按 (tableName, prop.name) 匹配，
 * 匹配键统一大写。只填空值，绝不覆盖数据库已有的中文名。</p>
 *
 * <p>数据来源：tools/chm_to_json.py 解析 NC65数据字典.chm 生成，
 * 随构建打进 classpath（/chm_dictionary.json），运行期找不到再退到文件系统。</p>
 */
public final class ChmDictionary {

    private static final String RESOURCE = "/chm_dictionary.json";

    /** 延迟加载 + 单例缓存；加载失败返回 null，调用方静默跳过（不影响正常导出）。 */
    private static volatile JSONObject root;

    private ChmDictionary() {
    }

    /** 对导出数据字典做中文名兜底：只补空白 displayName，找不到或加载失败就照常导出。 */
    public static void backfill(DataDictionary dict) {
        if (dict == null || dict.modules == null) {
            return;
        }
        JSONObject data = load();
        if (data == null) {
            return;
        }
        for (Module m : dict.modules) {
            if (m == null || m.comps == null) {
                continue;
            }
            for (Comp c : m.comps) {
                if (c == null || c.classes == null) {
                    continue;
                }
                for (Cls cl : c.classes) {
                    fillCls(data, cl);
                }
            }
        }
    }

    private static void fillCls(JSONObject data, Cls cl) {
        if (cl == null || cl.tableName == null) {
            return;
        }
        String tableKey = cl.tableName.trim().toUpperCase(Locale.ROOT);
        if (tableKey.isEmpty()) {
            return;
        }
        JSONObject table = data.getJSONObject(tableKey);
        if (table == null) {
            return;
        }
        // 表中文名：只补空白
        if (isEmpty(cl.displayName)) {
            cl.displayName = table.getString("n");
        }
        // 字段中文名：只补空白
        if (cl.props != null) {
            JSONObject fields = table.getJSONObject("f");
            if (fields == null) {
                return;
            }
            for (Prop p : cl.props) {
                if (p == null || !isEmpty(p.displayName) || isEmpty(p.name)) {
                    continue;
                }
                String propKey = p.name.trim().toUpperCase(Locale.ROOT);
                String cn = fields.getString(propKey);
                if (!isEmpty(cn)) {
                    p.displayName = cn;
                }
            }
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static JSONObject load() {
        JSONObject r = root;
        if (r != null) {
            return r;
        }
        synchronized (ChmDictionary.class) {
            if (root != null) {
                return root;
            }
            String text = readResource();
            if (text == null) {
                return null;
            }
            try {
                JSONObject parsed = JSON.parseObject(text);
                root = parsed;
                return parsed;
            } catch (Exception e) {
                // 补充字典损坏不阻塞导出
                return null;
            }
        }
    }

    private static String readResource() {
        // 1) 类路径（打进 jar / class 输出目录）
        InputStream in = ChmDictionary.class.getResourceAsStream(RESOURCE);
        if (in != null) {
            try {
                return readAll(in);
            } catch (IOException ignore) {
            } finally {
                try {
                    in.close();
                } catch (IOException ignore) {
                }
            }
        }
        // 2) 文件系统兜底（开发目录 / 工具输出目录）
        File[] candidates = {
                new File("resources/chm_dictionary.json"),
                new File(new File(System.getProperty("user.dir", ".")), "chm_dictionary.json")
        };
        for (File f : candidates) {
            if (f.isFile()) {
                try {
                    return readAll(new FileInputStream(f));
                } catch (IOException ignore) {
                }
            }
        }
        return null;
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
