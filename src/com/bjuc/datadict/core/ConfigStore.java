package com.bjuc.datadict.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bjuc.datadict.util.CryptoUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 多数据源配置本地存储（%APPDATA%/BJUC-DataDict/config.json），密码 AES 加密记住。
 */
public final class ConfigStore {
    private static final String FILE_NAME = "config.json";

    private ConfigStore() {
    }

    public static File configFile() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.trim().isEmpty()) {
            appData = System.getProperty("user.home", ".");
        }
        File dir = new File(appData, "BJUC-DataDict");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, FILE_NAME);
    }

    public static List<DataSourceCfg> loadAll() {
        List<DataSourceCfg> list = new ArrayList<>();
        File f = configFile();
        if (!f.isFile()) {
            return list;
        }
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(json);
            if (root == null) {
                return list;
            }
            JSONArray arr = root.getJSONArray("list");
            if (arr == null) {
                return list;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                DataSourceCfg cfg = new DataSourceCfg();
                cfg.name = value(o, "name");
                cfg.jdbcUrl = value(o, "jdbcUrl");
                cfg.user = value(o, "user");
                cfg.driverClass = value(o, "driverClass");
                if (cfg.driverClass == null || cfg.driverClass.isEmpty()) {
                    cfg.driverClass = "oracle.jdbc.OracleDriver";
                }
                cfg.remark = value(o, "remark");
                String enc = value(o, "password");
                String dec = CryptoUtil.decrypt(enc);
                cfg.password = dec == null ? "" : dec;
                list.add(cfg);
            }
        } catch (Throwable e) {
            // 配置损坏时忽略，重新开始
        }
        return list;
    }

    private static String value(JSONObject o, String key) {
        String v = o.getString(key);
        return v == null ? "" : v;
    }

    public static void saveAll(List<DataSourceCfg> cfgs) {
        JSONArray arr = new JSONArray();
        for (DataSourceCfg c : cfgs) {
            JSONObject o = new JSONObject(true);
            o.put("name", c.name);
            o.put("jdbcUrl", c.jdbcUrl);
            o.put("user", c.user);
            o.put("driverClass", c.driverClass == null ? "oracle.jdbc.OracleDriver" : c.driverClass);
            o.put("remark", c.remark);
            o.put("password", CryptoUtil.encrypt(c.password));
            arr.add(o);
        }
        JSONObject root = new JSONObject(true);
        root.put("list", arr);
        try {
            byte[] bytes = JSON.toJSONString(root, true).getBytes(StandardCharsets.UTF_8);
            File f = configFile();
            Files.write(f.toPath(), bytes);
        } catch (IOException ignore) {
        }
    }
}