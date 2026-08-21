package com.bjuc.datadict.core;

import com.bjuc.datadict.core.model.DataDictionary;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * NC6.5 元数据加载器：直连 Oracle，读取 NC 元数据库（md_* 等）组装数据字典。
 * 本实现为独立移植，不依赖 IntelliJ 平台。
 */
public class DictLoader {

    public interface Progress {
        void step(String message);
        void log(String message);
    }

    public static final int CLASSTYPE_ENTITY = 201;
    public static final int CLASSTYPE_ENUM = 203;

    public static final String DEFAULT_DRIVER = "oracle.jdbc.OracleDriver";

    public String testConnection(DataSourceCfg cfg) throws Exception {
        ensureDriver(cfg);
        String driver = driverOf(cfg);
        try (Connection conn = open(cfg)) {
            StringBuilder sb = new StringBuilder();
            sb.append("连接成功");
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("select banner from v$version where rownum=1")) {
                if (rs.next()) {
                    sb.append("，数据库：").append(rs.getString(1));
                }
            } catch (Throwable e) {
                sb.append("，数据库：").append(conn.getMetaData().getDatabaseProductVersion());
            }
            return sb.toString();
        }
    }

    private static String driverOf(DataSourceCfg cfg) {
        String d = cfg.driverClass;
        if (d == null || d.trim().isEmpty()) {
            return DEFAULT_DRIVER;
        }
        return d.trim();
    }

    /** 驱动加载：优先配置的驱动类，找不到时自动回退老驱动（oracle.jdbc.driver.OracleDriver，兼容 8i/9i）。 */
    static void ensureDriver(DataSourceCfg cfg) throws ClassNotFoundException {
        String d = driverOf(cfg);
        try {
            Class.forName(d);
        } catch (ClassNotFoundException e) {
            if ("oracle.jdbc.driver.OracleDriver".equals(d)) {
                throw e;
            }
            Class.forName("oracle.jdbc.driver.OracleDriver");
        }
    }

    /**
     * 归一化 JDBC URL：允许用户只填 host:port/service 或 host:port/sid，
     * 自动补全 Oracle Thin 前缀；已带 jdbc: 前缀的按原样使用。
     */
    static String normalizeUrl(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return s;
        }
        String lower = s.toLowerCase(Locale.ENGLISH);
        if (lower.startsWith("jdbc:")) {
            return s;
        }
        return "jdbc:oracle:thin:@" + s;
    }

    static Connection open(DataSourceCfg cfg) throws Exception {
        return DriverManager.getConnection(normalizeUrl(cfg.jdbcUrl), cfg.user,
                cfg.password == null ? "" : cfg.password);
    }
    public DataDictionary load(DataSourceCfg cfg, Set<String> componentIds, Progress progress) throws Exception {
        ensureDriver(cfg);
        DataDictionary dict = new DataDictionary();
        dict.databaseName = cfg.user == null ? "" : cfg.user;

        try (Connection conn = open(cfg); Statement st = conn.createStatement()) {
            // 1. NC 版本
            step(progress, "1/13 读取 NC 版本(sm_product_version)...");
            try (ResultSet rs = st.executeQuery("select version from sm_product_version where version is not null")) {
                ResultSetMetaData md = rs.getMetaData();
                if (rs.next()) {
                    dict.ncVersion = str(rs, md, "version");
                }
            } catch (Throwable e) {
                progress.log("版本表不可读(可忽略): " + e.getMessage());
            }

            // 2. 集团
            step(progress, "2/13 读取集团(org_group)...");
            try (ResultSet rs = st.executeQuery("select name from org_group")) {
                ResultSetMetaData md = rs.getMetaData();
                if (rs.next()) {
                    dict.groupName = str(rs, md, "name");
                }
            } catch (Throwable e) {
                try (ResultSet rs = st.executeQuery("select unitname from bd_corp order by ts")) {
                    ResultSetMetaData md = rs.getMetaData();
                    if (rs.next()) {
                        dict.groupName = str(rs, md, "unitname");
                    }
                } catch (Throwable e2) {
                    progress.log("集团表不可读(可忽略): " + e2.getMessage());
                }
            }

            // 3. 模块
            step(progress, "3/13 读取模块列表(md_module)...");
            List<DataDictionary.Module> allModules = new ArrayList<DataDictionary.Module>();
            Map<String, DataDictionary.Module> id2Module = new LinkedHashMap<String, DataDictionary.Module>();
            try (ResultSet rs = st.executeQuery(
                    "select id, name, displayname, parentmoduleid from md_module "
                            + "where id in (select distinct ownmodule from md_component) order by name")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    DataDictionary.Module m = new DataDictionary.Module();
                    m.id = str(rs, md, "id");
                    m.name = str(rs, md, "name");
                    m.displayname = str(rs, md, "displayname");
                    id2Module.put(m.id, m);
                    allModules.add(m);
                }
            } catch (Throwable e) {
                progress.log("模块表不可读: " + e.getMessage());
            }
            DataDictionary.Module orphan = new DataDictionary.Module();
            orphan.id = "-";
            orphan.name = "未分组";
            orphan.displayname = "未分组";

            // 4. 组件
            step(progress, "4/13 读取元数据组件(md_component)...");
            List<DataDictionary.Comp> comps = new ArrayList<DataDictionary.Comp>();
            Map<String, DataDictionary.Comp> id2Comp = new HashMap<String, DataDictionary.Comp>();
            try (ResultSet rs = st.executeQuery(
                    "select id, name, displayname, ownmodule from md_component order by version nulls last")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    DataDictionary.Comp c = new DataDictionary.Comp();
                    c.id = str(rs, md, "id");
                    c.name = str(rs, md, "name");
                    c.displayname = str(rs, md, "displayname");
                    c.ownModule = str(rs, md, "ownmodule");
                    id2Comp.put(c.id, c);
                    comps.add(c);
                }
            } catch (Throwable e) {
                throw new RuntimeException("读取 md_component 失败，请确认连接的是 NC6.5 元数据库: " + e.getMessage(), e);
            }
            // 是否按选中组件做 SQL 级筛选（只选部分组件时只读相关类/属性，避免全库读取拖慢导出）
            // IN 列表分块（每块 <=900，避免超过 Oracle ORA-01795 的 1000 上限）
            boolean filterByScope = componentIds != null && !componentIds.isEmpty()
                    && inWhere("componentid", componentIds).length() > 0;
            String classWhere = filterByScope ? (" where " + inWhere("componentid", componentIds)) : "";
            String propWhere = ""; // 选中组件涉及的类（用于过滤 md_property）

            // 5. 类（实体/枚举）
            step(progress, "5/13 读取元数据类(md_class)..." + (filterByScope ? "（仅选中组件）" : ""));
            Map<String, DataDictionary.Cls> id2Cls = new LinkedHashMap<String, DataDictionary.Cls>();
            try {
                try (ResultSet rs = st.executeQuery(
                        "select c.id, c.name, c.displayname, c.fullclassname, c.componentid, c.classtype, "
                                + "c.defaulttablename "
                                + "from md_class c" + classWhere)) {
                    readClasses(rs, id2Cls);
                }
            } catch (Throwable e) {
                progress.log("md_class 读取失败，降级重试: " + e.getMessage());
                try (ResultSet rs = st.executeQuery(
                        "select id, name, displayname, fullclassname, componentid, classtype, "
                                + "defaulttablename from md_class" + classWhere)) {
                    readClasses(rs, id2Cls);
                }
            }
            // 表名兜底补全：个别类 defaulttablename 为空时，从 md_table 按类关联回填
            backfillTableNames(conn, id2Cls, progress);

            // 绑定 md_property 的筛选：仅过滤选中组件涉及的类；类查询异常导致集合为空时退回全量，保证数据不丢
            if (filterByScope && !id2Cls.isEmpty()) {
                String pw = inWhere("classid", id2Cls.keySet());
                propWhere = pw.length() > 0 ? (" where " + pw) : "";
            }

            // 6. 属性
            step(progress, "6/13 读取元数据属性(md_property，耗时较长)..." + (propWhere.length() > 0 ? "（仅选中组件）" : ""));
            List<Map<String, String>> props = new ArrayList<Map<String, String>>();
            try {
                try (ResultSet rs = st.executeQuery(
                        "select name, displayname, nullable, refmodelname, defaultvalue, description, "
                                + "datatype, calculation, dynamicattr, attrlength, classid "
                                + "from md_property" + propWhere
                                + " order by attrsequence nulls last")) {
                    readProperties(rs, props);
                }
            } catch (Throwable e) {
                throw new RuntimeException("读取 md_property 失败: " + e.getMessage(), e);
            }

            // 7. 枚举
            step(progress, "7/13 读取枚举值(md_enumvalue)...");
            Map<String, List<DataDictionary.EnumVal>> id2Enums = new HashMap<String, List<DataDictionary.EnumVal>>();
            try {
                try (ResultSet rs = st.executeQuery("select value, name, id from md_enumvalue")) {
                    ResultSetMetaData md = rs.getMetaData();
                    while (rs.next()) {
                        String id = str(rs, md, "id");
                        DataDictionary.EnumVal v = new DataDictionary.EnumVal();
                        v.value = str(rs, md, "value");
                        v.name = str(rs, md, "name");
                        List<DataDictionary.EnumVal> list = id2Enums.get(id);
                        if (list == null) {
                            list = new ArrayList<DataDictionary.EnumVal>();
                            id2Enums.put(id, list);
                        }
                        list.add(v);
                    }
                }
            } catch (Throwable e) {
                progress.log("枚举表不可读: " + e.getMessage());
            }

            // 8. 主键
            step(progress, "8/13 读取主键(md_column)...");
            Map<String, HashSet<String>> id2Pks = new HashMap<String, HashSet<String>>();
            try (ResultSet rs = st.executeQuery("select name, tableid from md_column where pkey = 'Y'")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    String tid = str(rs, md, "tableid");
                    HashSet<String> set = id2Pks.get(tid);
                    if (set == null) {
                        set = new HashSet<String>();
                        id2Pks.put(tid, set);
                    }
                    set.add(str(rs, md, "name"));
                }
            } catch (Throwable e) {
                progress.log("主键表不可读: " + e.getMessage());
            }

            // 9. agg 聚合类全名
            step(progress, "9/13 读取聚合全类名(md_accessorpara)...");
            Map<String, String> id2Agg = new HashMap<String, String>();
            try (ResultSet rs = st.executeQuery("select paravalue, id from md_accessorpara")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    String id = str(rs, md, "id");
                    if (id != null && !id.isEmpty()) {
                        id2Agg.put(id, str(rs, md, "paravalue"));
                    }
                }
            } catch (Throwable e) {
                progress.log("agg 表不可读: " + e.getMessage());
            }

            // 10. 单据类型
            step(progress, "10/13 读取单据类型(bd_billtype)...");
            Map<String, Map<String, String>> compBillType = new HashMap<String, Map<String, String>>();
            try (ResultSet rs = st.executeQuery(
                    "select b.pk_billtypecode, b.billtypename, b.nodecode, n.fun_name, np.paramvalue, b.component "
                            + "from bd_billtype b "
                            + "left join sm_funcregister n on n.funcode = b.nodecode "
                            + "left join sm_paramregister np on np.parentid = n.cfunid and np.paramname = 'BeanConfigFilePath'")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    String comp = str(rs, md, "component");
                    if (comp == null || comp.isEmpty() || compBillType.containsKey(comp)) {
                        continue;
                    }
                    Map<String, String> info = new HashMap<String, String>();
                    info.put("code", str(rs, md, "pk_billtypecode"));
                    info.put("name", str(rs, md, "billtypename"));
                    info.put("fun", str(rs, md, "fun_name"));
                    info.put("bean", str(rs, md, "paramvalue"));
                    compBillType.put(comp, info);
                }
            } catch (Throwable e) {
                progress.log("单据类型表不可读: " + e.getMessage());
            }

            // 11. 实体功能关联：功能号 + 菜单 + 单据类型
            //     锚点：组件归属（md_component -> md_class.componentid）+ 单据(component 名关联)，
            //     再用 sm_funcregister / sm_menuitemreg 补功能号与菜单。菜单/功能用 LEFT JOIN 避免丢类。
            step(progress, "11/13 读取实体功能关联(sm_funcregister/menu/billtype)...");
            Map<String, List<DataDictionary.ClsFunc>> id2Funcs = new HashMap<String, List<DataDictionary.ClsFunc>>();
            try (ResultSet rs = st.executeQuery(
                    "select distinct sf.funcode, sm.menuitemcode, sm.menuitemname, "
                            + "bd.pk_billtypecode, bd.billtypename, mc.id "
                            + "from md_component mp "
                            + "join bd_billtype bd on bd.component = mp.name "
                            + "left join sm_funcregister sf on bd.nodecode = sf.funcode "
                            + "join md_class mc on mc.componentid = mp.id "
                            + "left join sm_menuitemreg sm on sm.funcode = sf.funcode")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    String cid = str(rs, md, "id");
                    if (cid == null || cid.isEmpty()) {
                        continue;
                    }
                    DataDictionary.ClsFunc fn = new DataDictionary.ClsFunc();
                    fn.funcode = str(rs, md, "funcode");
                    fn.menuitemcode = str(rs, md, "menuitemcode");
                    fn.menuitemname = str(rs, md, "menuitemname");
                    fn.billtypecode = str(rs, md, "pk_billtypecode");
                    fn.billtypename = str(rs, md, "billtypename");
                    List<DataDictionary.ClsFunc> list = id2Funcs.get(cid);
                    if (list == null) {
                        list = new ArrayList<DataDictionary.ClsFunc>();
                        id2Funcs.put(cid, list);
                    }
                    list.add(fn);
                }
            } catch (Throwable e) {
                progress.log("实体功能关联不可读(可忽略): " + e.getMessage());
            }

            // 12. 组装：属性塞进实体类，枚举值挂到引用属性
            step(progress, "12/12 组装实体与属性...");
            attachProps(id2Cls, props, id2Enums, id2Pks, id2Agg);

            // 13. 组件挂到模块, 实体挂到组件
            step(progress, "13/13 组装模块树...");
            for (DataDictionary.Comp c : comps) {
                if (componentIds != null && !componentIds.contains(c.id)) {
                    continue;
                }
                DataDictionary.Module m = id2Module.get(c.ownModule);
                if (m == null) {
                    m = orphan;
                }
                Map<String, String> bt = compBillType.get(c.name);
                if (bt != null) {
                    c.billTypeCode = bt.get("code");
                    c.billTypeName = bt.get("name");
                    c.nodeName = bt.get("fun");
                    c.beanConfig = bt.get("bean");
                }
                m.comps.add(c);
            }
            if (!orphan.comps.isEmpty() && !allModules.contains(orphan)) {
                allModules.add(orphan);
            }
            for (DataDictionary.Cls cls : id2Cls.values()) {
                List<DataDictionary.ClsFunc> funcs = id2Funcs.get(cls.id);
                if (funcs != null && !funcs.isEmpty()) {
                    cls.funcs = funcs;
                }
                DataDictionary.Comp owner = id2Comp.get(cls.componentId);
                if (owner != null && CLASSTYPE_ENTITY == cls.classType) {
                    owner.classes.add(cls);
                }
            }
            if (filterByScope) {
                // 按组件筛选导出：只保留有内容的模块，统计用筛选后的实际数据，
                // 避免网页列出大量空模块、totalComponents 还是全库数字让用户误以为导出失败
                List<DataDictionary.Module> kept = new ArrayList<DataDictionary.Module>();
                for (DataDictionary.Module m : allModules) {
                    if (m.comps == null || m.comps.isEmpty()) {
                        continue;
                    }
                    kept.add(m);
                }
                allModules = kept;
                comps.clear();
                for (DataDictionary.Module m : allModules) {
                    comps.addAll(m.comps);
                }
            }
            dict.modules = allModules;
            dict.generateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
            dict.totalModules = dict.modules.size();
            dict.totalComponents = comps.size();
            dict.totalProps = props.size();
            int classes = 0;
            for (DataDictionary.Comp c : comps) {
                classes += c.classes.size();
            }
            dict.totalClasses = classes;
            if (dict.ncVersion == null || dict.ncVersion.trim().isEmpty()) {
                dict.ncVersion = "NC6.5";
            }
            if (dict.groupName == null) {
                dict.groupName = "";
            }
        }
        progress.log("数据字典读取完成");
        return dict;
    }
    private void readClasses(ResultSet rs, Map<String, DataDictionary.Cls> id2Cls) throws Exception {
        ResultSetMetaData md = rs.getMetaData();
        while (rs.next()) {
            DataDictionary.Cls cl = new DataDictionary.Cls();
            cl.id = str(rs, md, "id");
            cl.name = str(rs, md, "name");
            cl.displayName = str(rs, md, "displayname");
            cl.fullClassName = str(rs, md, "fullclassname");
            cl.componentId = str(rs, md, "componentid");
            cl.classType = intOf(str(rs, md, "classtype"));
            int idx = findColumn(md, "defaulttablename");
            if (idx > 0) {
                cl.tableName = str(rs, md, "defaulttablename");
            }
            id2Cls.put(cl.id, cl);
        }
    }

    /**
     * 表名兜底补全：主查询的 md_table 关联失败（或个别类缺表名）时，单独查 md_table
     * 建立“类 id → 表名”映射并回填。NC 中 md_table 通过 classid（= md_class.id）关联；
     * 某些库两者共用同一 id，这里两种关系都尝试，兼容不同版本。
     */
    private static void backfillTableNames(Connection conn, Map<String, DataDictionary.Cls> id2Cls, Progress progress) {
        if (id2Cls.isEmpty()) {
            return;
        }
        Map<String, String> map = new HashMap<String, String>();
        boolean viaClassId = queryTableMap(conn, "classid", map);
        if (!viaClassId) {
            progress.log("md_table.classid 不可读，尝试 id 关联重试");
            queryTableMap(conn, "id", map);
        }
        if (map.isEmpty()) {
            return;
        }
        for (DataDictionary.Cls cl : id2Cls.values()) {
            if (cl.tableName != null && !cl.tableName.trim().isEmpty()) {
                continue; // 已有表名跳过
            }
            String name = map.get(cl.id);
            if (name != null && !name.trim().isEmpty()) {
                cl.tableName = name;
            }
        }
    }

    /** 按指定列读取 md_table 的(值→name)映射；列不存在时返回 false。 */
    private static boolean queryTableMap(Connection conn, String keyCol, Map<String, String> map) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select " + keyCol + ", name from md_table where name is not null")) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                String k = str(rs, md, keyCol);
                if (k == null || k.isEmpty()) {
                    continue;
                }
                String name = str(rs, md, "name");
                if (!map.containsKey(k)) {
                    map.put(k, name);
                }
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void readProperties(ResultSet rs, List<Map<String, String>> props) throws Exception {
        ResultSetMetaData md = rs.getMetaData();
        while (rs.next()) {
            Map<String, String> m = new HashMap<String, String>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String label = md.getColumnLabel(i).toLowerCase(Locale.ROOT);
                m.put(label, rs.getString(i));
            }
            props.add(m);
        }
    }

    private void attachProps(Map<String, DataDictionary.Cls> id2Cls,
                             List<Map<String, String>> props,
                             Map<String, List<DataDictionary.EnumVal>> id2Enums,
                             Map<String, HashSet<String>> id2Pks,
                             Map<String, String> id2Agg) {
        for (Map<String, String> raw : props) {
            String classId = raw.get("classid");
            DataDictionary.Cls cl = id2Cls.get(classId);
            if (cl == null) {
                continue;
            }
            if ((cl.aggFullClassName == null || cl.aggFullClassName.isEmpty())
                    && id2Agg.containsKey(classId)) {
                cl.aggFullClassName = id2Agg.get(classId);
            }
            if (cl.classType != CLASSTYPE_ENTITY) {
                continue;
            }
            DataDictionary.Prop p = resolveProp(raw, cl, id2Cls, id2Enums, id2Pks);
            cl.props.add(p);
        }
    }

    private DataDictionary.Prop resolveProp(Map<String, String> raw,
                                            DataDictionary.Cls cl,
                                            Map<String, DataDictionary.Cls> id2Cls,
                                            Map<String, List<DataDictionary.EnumVal>> id2Enums,
                                            Map<String, HashSet<String>> id2Pks) {
        DataDictionary.Prop p = new DataDictionary.Prop();
        p.name = raw.get("name");
        p.displayName = raw.get("displayname");
        p.nullable = raw.get("nullable");
        p.defaultValue = raw.get("defaultvalue");
        p.description = raw.get("description");
        p.attrLength = raw.get("attrlength");

        String dt = raw.get("datatype");
        String mapped = TypeMapper.of(dt);
        if (mapped != null) {
            p.typeName = mapped;
            p.refDesc = "";
        } else if (dt != null && !dt.isEmpty() && id2Enums.containsKey(dt)) {
            p.typeName = "枚举";
            p.enumValues = id2Enums.get(dt);
            p.refId = dt;
            DataDictionary.Cls ec = id2Cls.get(dt);
            p.refDesc = ec == null ? "枚举" : "枚举：" + ec.displayName;
        } else if (dt != null && !dt.isEmpty() && id2Cls.containsKey(dt)) {
            DataDictionary.Cls ref = id2Cls.get(dt);
            p.typeName = "引用";
            p.refId = dt;
            p.refDesc = ref.displayName + "(" + ref.name + ")";
        } else {
            p.typeName = (dt == null || dt.isEmpty()) ? "-" : dt;
            p.refDesc = "";
        }

        HashSet<String> pks = id2Pks.get(cl.tableName);
        if (pks == null) {
            pks = id2Pks.get(cl.id);
        }
        if (pks != null && p.name != null && pks.contains(p.name)) {
            p.isKey = true;
        }
        return p;
    }

    private int idxOf;
    private static void step(Progress p, String msg) {
        if (p != null) {
            p.step(msg);
        }
    }

    private static int findColumn(ResultSetMetaData md, String name) {
        try {
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (md.getColumnLabel(i).equalsIgnoreCase(name)) {
                    return i;
                }
            }
        } catch (Throwable ignore) {
        }
        return -1;
    }

    private static String str(ResultSet rs, ResultSetMetaData md, String label) {
        int idx = findColumn(md, label);
        if (idx < 0) {
            return "";
        }
        try {
            String v = rs.getString(idx);
            return v == null ? "" : v.trim();
        } catch (Throwable e) {
            return "";
        }
    }

    private static int intOf(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * 把 id 集合拼成分块的 IN 条件，用于 WHERE 子句：每块 <= 900 个，多块用 OR 连接，
     * 整体用一对括号包裹，返回形如 "(col in (... ) or col in (...))"。
     * 避免单个 IN 列表超过 Oracle 的 1000 上限（ORA-01795）。
     * 集合为空或无有效 id 时返回空串。
     */
    private static String inWhere(String col, java.util.Collection<String> ids) {
        final int MAX = 900; // 每块上限（低于 Oracle 1000 留余量）
        StringBuilder sb = new StringBuilder();
        int block = 0;
        boolean any = false;
        StringBuilder vals = new StringBuilder();
        int cnt = 0;
        for (String id : ids) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (cnt >= MAX) {
                if (!any) {
                    sb.append("(");
                }
                if (block > 0) {
                    sb.append(" or ");
                }
                sb.append(col).append(" in (").append(vals).append(")");
                block++;
                any = true;
                vals.setLength(0);
                cnt = 0;
            }
            if (vals.length() > 0) {
                vals.append(",");
            }
            vals.append('\'').append(id.replace("'", "''")).append('\'');
            cnt++;
        }
        if (cnt > 0) {
            if (block > 0) {
                sb.append(" or ");
            } else {
                sb.append("(");
            }
            sb.append(col).append(" in (").append(vals).append(")");
            block++;
            any = true;
        }
        if (any) {
            sb.append(")");
            return sb.toString();
        }
        return "";
    }

    public static class ScopeModule {
        public String id;
        public String name;
        public String displayname;
        public List<ScopeComp> comps = new ArrayList<ScopeComp>();
    }

    public static class ScopeComp {
        public String id;
        public String name;
        public String displayname;
        public String ownModule;
    }

    /** 读取模块/组件一览（用于导出范围勾选，轻量查询） */
    public List<ScopeModule> loadScope(DataSourceCfg cfg) throws Exception {
        ensureDriver(cfg);
        List<ScopeModule> result = new ArrayList<ScopeModule>();
        java.util.Map<String, ScopeModule> id2m = new LinkedHashMap<String, ScopeModule>();
        try (Connection conn = open(cfg); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "select id, name, displayname, parentmoduleid from md_module "
                            + "where id in (select distinct ownmodule from md_component) order by name")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    ScopeModule m = new ScopeModule();
                    m.id = str(rs, md, "id");
                    m.name = str(rs, md, "name");
                    m.displayname = str(rs, md, "displayname");
                    id2m.put(m.id, m);
                    result.add(m);
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "select id, name, displayname, ownmodule from md_component order by version nulls last")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    ScopeComp c = new ScopeComp();
                    c.id = str(rs, md, "id");
                    c.name = str(rs, md, "name");
                    c.displayname = str(rs, md, "displayname");
                    c.ownModule = str(rs, md, "ownmodule");
                    ScopeModule m = id2m.get(c.ownModule);
                    if (m == null) {
                        m = id2m.get("-");
                        if (m == null) {
                            m = new ScopeModule();
                            m.id = "-";
                            m.name = "未分组";
                            m.displayname = "未分组";
                            id2m.put("-", m);
                            result.add(m);
                        }
                    }
                    m.comps.add(c);
                }
            }
        }
        return result;
    }
}
