package com.bjuc.datadict.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * NC 库 SQL 脚本导出 —— 以「功能节点号(funcode)」为起点，递归关联导出相关元数据行。
 *
 * 做法：先按 funcode 反查 sm_funcregister 并向下取整个功能节点子树（parentid -> cfunid），
 * 得到本节点的 funcode 集合与 cfunid 集合；再对相关表逐表按“允许的关联列名”匹配真实表列，
 * 用派生 key 集合过滤出相关行导出。
 *
 * 容错：某张表缺可用关联列 / 查询失败时，记日志并跳过该表，不中断整体导出。
 *
 * 约定（逐字节贴近用户样例）：
 *   - NULL      -> null
 *   - 字符串     -> '...'，单引号 '' 转义；'~' 作字面量原样输出
 *   - 数值       -> 原样
 *   - 日期/时间戳 -> 'yyyy-MM-dd HH:mm:ss'
 *   - BLOB      -> null
 *   - CLOB      -> 取字符串按引号输出
 *   - 每行以 ; 结尾
 */
public class SqlScriptExporter {

    public interface Progress {
        void step(String message);
        void log(String message);
    }

    /** 功能节点子树上下文：递归结果 */
    private static class Ctx {
        final Set<String> funcodes = new LinkedHashSet<String>();
        final Set<String> cfunids = new LinkedHashSet<String>();
        final Set<String> menuitemIds = new LinkedHashSet<String>();
        final Set<String> billtypeIds = new LinkedHashSet<String>();
        final Set<String> billtempletIds = new LinkedHashSet<String>();
        final Set<String> queryTempletIds = new LinkedHashSet<String>();
        final Set<String> printTemplateIds = new LinkedHashSet<String>();
        final Set<String> bcrRuleBaseIds = new LinkedHashSet<String>();
        boolean rootFound = false;
    }

    /**
     * 以功能节点号导出相关元数据脚本。
     *
     * @return 各表行数合计
     */
    public int export(DataSourceCfg cfg, String funcode, File outFile,
                      Progress progress) throws Exception {
        DictLoader.ensureDriver(cfg);
        int totalRows = 0;
        BufferedWriter w = null;
        try (Connection conn = DictLoader.open(cfg)) {
            Ctx ctx = initNodeSubtree(conn, funcode, progress);
            if (!ctx.rootFound) {
                throw new RuntimeException(
                        "未在 sm_funcregister 中找到功能节点号: " + funcode + "，请核对后再试");
            }
            w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outFile), StandardCharsets.UTF_8));

            // 进度驱动：18 组连续导出步骤，每组完成后推进确定进度条
            final int[] st = {0};
            final int STEPS = 18;

            // 直接以 cfunid / funcode 关联的功能主相关表
            emitStep(progress, st, STEPS, "导出功能注册表");
            totalRows += exportSubtreeRegisters(conn, w, ctx, progress);
            emitStep(progress, st, STEPS, "导出菜单注册");
            totalRows += exportByColumnsCollect(conn, w, "SM_MENUITEMREG", ctx.funcodes,
                    new String[]{"funcode"}, "pk_menuitem", ctx.menuitemIds, progress);
            emitStep(progress, st, STEPS, "导出参数注册");
            totalRows += exportByColumns(conn, w, "SM_PARAMREGISTER", ctx.cfunids,
                    new String[]{"parentid"}, progress);
            // 按钮：PARENT_ID 可挂在功能节点(cfunid)或菜单(pk_menuitem)，两者并集过滤
            Set<String> btnKeys = new LinkedHashSet<String>();
            btnKeys.addAll(ctx.cfunids);
            btnKeys.addAll(ctx.menuitemIds);
            emitStep(progress, st, STEPS, "导出按钮注册");
            totalRows += exportByColumns(conn, w, "SM_BUTNREGISTER", btnKeys,
                    new String[]{"parent_id", "parentid", "sourceid", "funcregisterid", "menuitemid"}, progress);

            // 单据类型：nodecode=funcode 关联，同时收齐单据类型主键(pk_billtypecode/pk_billtypeid)
            emitStep(progress, st, STEPS, "导出单据类型");
            totalRows += exportBilltype(conn, w, ctx, progress);

            // 模板/动作/业务类/编码规则：优先按单据类型或节点关联
            Set<String> tb = new LinkedHashSet<String>();
            tb.addAll(ctx.billtypeIds);
            tb.addAll(ctx.funcodes);

            // 单据模板头：按 nodecode/funccode/pk_billtypecode 过滤，并收集 pk_billtemplet 供表体/表尾关联
            emitStep(progress, st, STEPS, "导出单据模板头");
            totalRows += exportByColumnsCollect(conn, w, "PUB_BILLTEMPLET", tb,
                    new String[]{"nodecode", "funccode", "pk_billtypecode"},
                    "pk_billtemplet", ctx.billtempletIds, progress);
            Set<String> tbHead = new LinkedHashSet<String>();
            tbHead.addAll(ctx.billtempletIds);
            tbHead.addAll(tb);
            emitStep(progress, st, STEPS, "导出单据模板表体");
            totalRows += exportByColumns(conn, w, "PUB_BILLTEMPLET_B", tbHead,
                    new String[]{"pk_billtemplet", "billtempletid", "nodecode", "funccode"}, progress);
            emitStep(progress, st, STEPS, "导出单据模板表尾");
            totalRows += exportByColumns(conn, w, "PUB_BILLTEMPLET_T", tbHead,
                    new String[]{"pk_billtemplet", "billtempletid", "nodecode", "funccode"}, progress);

            emitStep(progress, st, STEPS, "导出查询模板");
            totalRows += exportQueryTemplet(conn, w, ctx, progress);
            emitStep(progress, st, STEPS, "导出查询条件");
            totalRows += exportByColumns(conn, w, "PUB_QUERY_CONDITION", ctx.queryTempletIds,
                    new String[]{"pk_templet", "id", "templetcode", "node_code", "nodecode"}, progress);

            emitStep(progress, st, STEPS, "导出系统模板");
            totalRows += exportByColumns(conn, w, "PUB_SYSTEMPLATE_BASE", tb,
                    new String[]{"funnode", "nodekey", "nodecode", "funccode"}, progress);

            emitStep(progress, st, STEPS, "导出打印模板");
            totalRows += exportPrintTemplate(conn, w, ctx, progress);
            emitStep(progress, st, STEPS, "导出打印单元格");
            totalRows += exportByColumns(conn, w, "PUB_PRINT_CELL", ctx.printTemplateIds,
                    new String[]{"ctemplateid", "ptemplateid", "pk_printtemplate", "printtemplate"}, progress);

            emitStep(progress, st, STEPS, "导出单据动作");
            totalRows += exportByColumns(conn, w, "PUB_BILLACTION", tb,
                    new String[]{"pk_billtypeid", "pk_billtype", "pk_billtypecode", "nodecode", "funccode"}, progress);
            emitStep(progress, st, STEPS, "导出业务类");
            totalRows += exportByColumns(conn, w, "PUB_BUSICLASS", tb,
                    new String[]{"pk_billtypeid", "pk_billtype", "pk_billtypecode", "nodecode", "funccode"}, progress);

            // BCR 编码规则链：
            //   PUB_BCR_NBCR.CODE = BD_BILLTYPE.PK_BILLTYPECODE（如 KQ01/HAL2）
            //   PUB_BCR_RULEBASE.NBCRCODE = PUB_BCR_NBCR.CODE，并收集 pk_billcodebase
            //   PUB_BCR_ELEM.PK_BILLCODEBASE = PUB_BCR_RULEBASE.PK_BILLCODEBASE
            emitStep(progress, st, STEPS, "导出编码规则NBCR");
            totalRows += exportByColumns(conn, w, "PUB_BCR_NBCR", tb,
                    new String[]{"code", "pk_billtypecode"}, progress);
            emitStep(progress, st, STEPS, "导出编码规则基");
            totalRows += exportByColumnsCollect(conn, w, "PUB_BCR_RULEBASE", tb,
                    new String[]{"nbcrcode", "code"}, "pk_billcodebase", ctx.bcrRuleBaseIds, progress);
            emitStep(progress, st, STEPS, "导出编码规则元素");
            totalRows += exportByColumns(conn, w, "PUB_BCR_ELEM", ctx.bcrRuleBaseIds,
                    new String[]{"pk_billcodebase"}, progress);

            w.flush();
            if (progress != null) {
                progress.log("  [阶段] 全部表已写完并 flush，共 " + totalRows + " 行。");
            }
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Throwable ignore) {
                }
            }
        }
        if (progress != null) {
            progress.log("  [阶段] 已关闭写入流（导出完成，共 " + totalRows + " 行）。");
        }
        return totalRows;
    }

    /**
     * 自定义档案脚本导出：按档案分类主键(pk_defdoclist / mdclassid / para1)导出 7 张关联表。
     * 关联图示(用户核对)：
     *   BD_DEFDOCLIST.pk_defdoclist = <pk>         档案分类头
     *   BD_DEFDOC     .pk_defdoclist = <pk>         档案项
     *   BD_MODE_ALL   .mdclassid     = <pk>         档案模式（全）
     *   BD_MODE_SELECTED.mdclassid   = <pk>         档案模式（选）
     *   BD_UNIQUERULE .mdclassid     = <pk>         唯一性规则（并收集 pk_rule）
     *   BD_UNIQUERULE_ITEM.pk_rule   in (pk_rule)   规则明细
     *   BD_REFINFO    .para1         = <pk>
     */
    public int exportDefdoc(DataSourceCfg cfg, String pk, File outFile,
                            Progress progress) throws Exception {
        DictLoader.ensureDriver(cfg);
        if (pk == null || pk.trim().isEmpty()) {
            throw new RuntimeException("请先输入档案分类 PK（pk_defdoclist / mdclassid）");
        }
        String pkv = pk.trim();
        int totalRows = 0;
        BufferedWriter w = null;
        try (Connection conn = DictLoader.open(cfg)) {
            w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outFile), StandardCharsets.UTF_8));
            final int[] st = {0};
            final int STEPS = 7;
            emitStep(progress, st, STEPS, "导出档案分类");
            totalRows += exportRows(conn, w, "BD_DEFDOCLIST",
                    "pk_defdoclist = '" + esc(pkv) + "'", null, null, progress);
            emitStep(progress, st, STEPS, "导出档案项");
            totalRows += exportRows(conn, w, "BD_DEFDOC",
                    "pk_defdoclist = '" + esc(pkv) + "'", null, null, progress);
            emitStep(progress, st, STEPS, "导出档案模式(全)");
            totalRows += exportRows(conn, w, "BD_MODE_ALL",
                    "mdclassid = '" + esc(pkv) + "'", null, null, progress);
            emitStep(progress, st, STEPS, "导出档案模式(选)");
            totalRows += exportRows(conn, w, "BD_MODE_SELECTED",
                    "mdclassid = '" + esc(pkv) + "'", null, null, progress);
            emitStep(progress, st, STEPS, "导出唯一性规则");
            Set<String> ruleIds = new LinkedHashSet<String>();
            totalRows += exportRows(conn, w, "BD_UNIQUERULE",
                    "mdclassid = '" + esc(pkv) + "'", "pk_rule", ruleIds, progress);
            emitStep(progress, st, STEPS, "导出规则明细");
            totalRows += exportRows(conn, w, "BD_UNIQUERULE_ITEM",
                    inClause("pk_rule", ruleIds), null, null, progress);
            emitStep(progress, st, STEPS, "导出引用信息");
            totalRows += exportRows(conn, w, "BD_REFINFO",
                    "para1 = '" + esc(pkv) + "'", null, null, progress);
            w.flush();
            if (progress != null) {
                progress.log("  [阶段] 自定义档案导出完成，共 " + totalRows + " 行。");
            }
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Throwable ignore) {
                }
            }
        }
        return totalRows;
    }

    /** 把字符串拼进单引号 SQL 字面量，并对单引号转义 */
    private static String esc(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    /** 由收集到的值集构造 IN 子句；空集时构造永不匹配的条件(1=0)避免非法空 IN */
    private static String inClause(String column, Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "1 = 0";
        }
        StringBuilder sb = new StringBuilder(column).append(" in (");
        int i = 0;
        for (String v : values) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append("'").append(esc(v)).append("'");
        }
        sb.append(")");
        return sb.toString();
    }

    /** 反查 sm_funcregister：定位 funcode 节点并向下取整个子树（parentid -> cfunid） */
    private Ctx initNodeSubtree(Connection conn, String rootFuncode, Progress progress)
            throws Exception {
        Ctx ctx = new Ctx();
        List<String[]> nodeRows = new ArrayList<String[]>();
        Statement st = null;
        ResultSet rs = null;
        try {
            st = conn.createStatement();
            rs = st.executeQuery("select * from sm_funcregister");
            ResultSetMetaData md = rs.getMetaData();
            int iC = indexOf(md, "cfunid");
            int iF = indexOf(md, "funcode");
            int iP = indexOf(md, "parent_id");
            if (iP < 0) {
                iP = indexOf(md, "parentid"); // 老库兼容
            }
            while (rs.next()) {
                String c = iC > 0 ? nz(rs.getString(iC)) : "";
                String f = iF > 0 ? nz(rs.getString(iF)) : "";
                String p = iP > 0 ? nz(rs.getString(iP)) : "";
                nodeRows.add(new String[]{c, f, p});
                if (f.equalsIgnoreCase(rootFuncode)) {
                    ctx.rootFound = true;
                    ctx.funcodes.add(f);
                    if (!c.isEmpty()) {
                        ctx.cfunids.add(c);
                    }
                }
            }
        } finally {
            close(rs);
            close(st);
        }
        if (!ctx.rootFound) {
            return ctx;
        }
        // BFS：向下找所有后代（某行的 parentid 属于已收集的 cfunid）
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String[] r : nodeRows) {
                String c = r[0];
                String p = r[2];
                if (c.isEmpty() || p.isEmpty()) {
                    continue;
                }
                if (ctx.cfunids.contains(p) && !ctx.cfunids.contains(c)) {
                    ctx.cfunids.add(c);
                    changed = true;
                }
            }
        }
        for (String[] r : nodeRows) {
            if (r[1] != null && !r[1].isEmpty() && ctx.cfunids.contains(r[0])) {
                ctx.funcodes.add(r[1]);
            }
        }
        if (progress != null) {
            progress.log("  功能节点子树：cfunid 节点 " + ctx.cfunids.size()
                    + " 个，funcode " + ctx.funcodes.size() + " 个");
        }
        return ctx;
    }

    /** 导出功能注册表中属于子树的行（节点本身 + 所有后代） */
    private int exportSubtreeRegisters(Connection conn, BufferedWriter w, Ctx ctx,
                                       Progress progress) {
        if (ctx.cfunids.isEmpty()) {
            return 0;
        }
        StringBuilder where = new StringBuilder("cfunid in (");
        int i = 0;
        for (String c : ctx.cfunids) {
            if (i > 0) {
                where.append(",");
            }
            if (i >= 900) {
                break;
            }
            where.append('\'').append(c.replace("'", "''")).append('\'');
            i++;
        }
        where.append(")");
        return exportRows(conn, w, "SM_FUNCREGISTER", where.toString(), null, null, progress);
    }

    /**
     * 通用智能导出：读取表真实列，匹配允许的关联列名，用 keySet 过滤；写出相关行。
     * 若表无可用关联列或缺 key，记日志返回 0。
     */
    private int exportByColumns(Connection conn, BufferedWriter w, String tableName,
                                Set<String> keySet, String[] allowedCols, Progress progress) {
        if (keySet == null || keySet.isEmpty()) {
            return 0;
        }
        String matchCol = null;
        try {
            Set<String> cols = columnsOf(conn, tableName);
            if (cols == null) {
                logSkip(progress, tableName, "表不存在或不可读");
                return 0;
            }
            for (String a : allowedCols) {
                if (cols.contains(a.toLowerCase(Locale.ENGLISH))) {
                    matchCol = a;
                    break;
                }
            }
            if (matchCol == null) {
                logSkip(progress, tableName,
                        "无可用关联列（候选: " + String.join("/", allowedCols) + "）");
                return 0;
            }
        } catch (Throwable e) {
            logSkip(progress, tableName, "读列失败: " + e.getMessage());
            return 0;
        }
        StringBuilder where = new StringBuilder(matchCol).append(" in (");
        int i = 0;
        for (String k : keySet) {
            if (i > 0) {
                where.append(",");
            }
            if (i >= 900) {
                break;
            }
            where.append('\'').append(k.replace("'", "''")).append('\'');
            i++;
        }
        where.append(")");
        return exportRows(conn, w, tableName, where.toString(), null, null, progress);
    }

    /** 单据类型：nodecode=funcode 关联，同时收集 pk_billtypecode 作为单据类型主键 */
    private int exportBilltype(Connection conn, BufferedWriter w, Ctx ctx, Progress progress) {
        if (ctx.funcodes.isEmpty()) {
            return 0;
        }
        StringBuilder where = new StringBuilder("nodecode in (");
        int i = 0;
        for (String f : ctx.funcodes) {
            if (i > 0) {
                where.append(",");
            }
            if (i >= 900) {
                break;
            }
            where.append('\'').append(f.replace("'", "''")).append('\'');
            i++;
        }
        where.append(")");
        // 手动遍历：写行 + 同时收集 pk_billtypecode(如 HAL2) 与 pk_billtypeid(如 0001ZZ10000000951R89)。
        // 前者供 BCR 编码规则按 CODE 关联、后者供 PUB_BILLACTION/PUB_BUSICLASS 按单据类型主键关联。
        int count = 0;
        Statement st = null;
        ResultSet rs = null;
        try {
            String sql = "select * from BD_BILLTYPE where " + where;
            if (progress != null) {
                progress.log("  开始导出表 BD_BILLTYPE ...");
            }
            st = conn.createStatement();
            try {
                st.setQueryTimeout(120);
            } catch (Throwable ignore) {
            }
            rs = st.executeQuery(sql);
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            int iTypeCode = indexOf(md, "pk_billtypecode");
            int iTypeId = indexOf(md, "pk_billtypeid");
            String[] names = new String[cols];
            for (int c = 0; c < cols; c++) {
                names[c] = md.getColumnName(c + 1);
            }
            while (rs.next()) {
                if (iTypeCode > 0) {
                    String v = rs.getString(iTypeCode);
                    if (v != null && !v.isEmpty()) {
                        ctx.billtypeIds.add(v);
                    }
                }
                if (iTypeId > 0) {
                    String v = rs.getString(iTypeId);
                    if (v != null && !v.isEmpty()) {
                        ctx.billtypeIds.add(v);
                    }
                }
                StringBuilder sb = new StringBuilder("INSERT INTO BD_BILLTYPE (");
                for (int c = 0; c < cols; c++) {
                    if (c > 0) {
                        sb.append(", ");
                    }
                    sb.append(names[c]);
                }
                sb.append(") VALUES (");
                for (int c = 1; c <= cols; c++) {
                    if (c > 1) {
                        sb.append(", ");
                    }
                    sb.append(value(rs, md, c));
                }
                sb.append(");");
                w.write(sb.toString());
                w.newLine();
                count++;
            }
            if (progress != null) {
                progress.log("  表 BD_BILLTYPE：" + count + " 行");
            }
            try {
                w.flush();
            } catch (Throwable ignore) {
            }
        } catch (Throwable e) {
            if (progress != null) {
                progress.log("  表 BD_BILLTYPE 导出失败(跳过): " + e.getMessage());
            }
        } finally {
            close(rs);
            close(st);
        }
        return count;
    }

    /** 查询模板：funcode/nodecode/单据类型 关联，收集 pk_query_templet */
    private int exportQueryTemplet(Connection conn, BufferedWriter w, Ctx ctx, Progress progress) {
        Set<String> ks = new LinkedHashSet<String>();
        ks.addAll(ctx.funcodes);
        ks.addAll(ctx.billtypeIds);
        if (ks.isEmpty()) {
            return 0;
        }
        String[] allowed = {"node_code", "nodecode", "funccode", "pk_billtypecode"};
        return exportByColumnsCollect(conn, w, "PUB_QUERY_TEMPLET", ks, allowed,
                "id", ctx.queryTempletIds, progress);
    }

    /** 打印模板：单据类型/节点 关联，收集打印模板主键 */
    private int exportPrintTemplate(Connection conn, BufferedWriter w, Ctx ctx, Progress progress) {
        Set<String> ks = new LinkedHashSet<String>();
        ks.addAll(ctx.billtypeIds);
        ks.addAll(ctx.funcodes);
        if (ks.isEmpty()) {
            return 0;
        }
        String[] allowed = {"vnodecode", "nodecode", "funccode", "pk_billtypecode"};
        // 同时收集 ptemplateid 与 ctemplateid：PUB_PRINT_CELL 是按打印模板的内容模板主键 CTEMPLATEID 关联的，
        // 只收 ptemplateid 会导致打印单元格 0 行。
        return exportByColumnsCollectTwo(conn, w, "PUB_PRINT_TEMPLATE", ks, allowed,
                new String[]{"ptemplateid", "ctemplateid"}, ctx.printTemplateIds, progress);
    }

    /** 同 exportByColumnsCollect，但收集多列值到同一 collectInto */
    private int exportByColumnsCollectTwo(Connection conn, BufferedWriter w, String tableName,
                                          Set<String> keySet, String[] allowedCols,
                                          String[] collectCols, Set<String> collectInto,
                                          Progress progress) {
        if (keySet.isEmpty()) {
            return 0;
        }
        String matchCol = null;
        try {
            Set<String> cols = columnsOf(conn, tableName);
            if (cols == null) {
                logSkip(progress, tableName, "表不存在或不可读");
                return 0;
            }
            for (String a : allowedCols) {
                if (cols.contains(a.toLowerCase(Locale.ENGLISH))) {
                    matchCol = a;
                    break;
                }
            }
            if (matchCol == null) {
                logSkip(progress, tableName,
                        "无可用关联列（候选: " + String.join("/", allowedCols) + "）");
                return 0;
            }
        } catch (Throwable e) {
            logSkip(progress, tableName, "读列失败: " + e.getMessage());
            return 0;
        }
        StringBuilder where = new StringBuilder(matchCol).append(" in (");
        int i = 0;
        for (String k : keySet) {
            if (i > 0) {
                where.append(",");
            }
            if (i >= 900) {
                break;
            }
            where.append('\'').append(k.replace("'", "''")).append('\'');
            i++;
        }
        where.append(")");
        int[] collectIdx = new int[collectCols.length];
        java.util.Arrays.fill(collectIdx, -1);
        int count = 0;
        Statement st = null;
        ResultSet rs = null;
        try {
            String sql = "select * from " + tableName + " where " + where;
            if (progress != null) {
                progress.log("  开始导出表 " + tableName + " ...");
            }
            st = conn.createStatement();
            try {
                st.setQueryTimeout(120);
            } catch (Throwable ignore) {
            }
            rs = st.executeQuery(sql);
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            for (int c = 1; c <= cols; c++) {
                String cn = md.getColumnName(c);
                for (int j = 0; j < collectCols.length; j++) {
                    if (collectIdx[j] < 0 && cn.equalsIgnoreCase(collectCols[j])) {
                        collectIdx[j] = c;
                    }
                }
            }
            String[] names = new String[cols];
            for (int c = 0; c < cols; c++) {
                names[c] = md.getColumnName(c + 1);
            }
            while (rs.next()) {
                if (collectInto != null) {
                    for (int j = 0; j < collectCols.length; j++) {
                        if (collectIdx[j] > 0) {
                            String v = rs.getString(collectIdx[j]);
                            if (v != null && !v.isEmpty()) {
                                collectInto.add(v);
                            }
                        }
                    }
                }
                StringBuilder sb = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
                for (int c = 0; c < cols; c++) {
                    if (c > 0) {
                        sb.append(", ");
                    }
                    sb.append(names[c]);
                }
                sb.append(") VALUES (");
                for (int c = 1; c <= cols; c++) {
                    if (c > 1) {
                        sb.append(", ");
                    }
                    sb.append(value(rs, md, c));
                }
                sb.append(");");
                w.write(sb.toString());
                w.newLine();
                count++;
            }
            if (progress != null) {
                progress.log("  表 " + tableName + "：" + count + " 行");
            }
            try {
                w.flush();
            } catch (Throwable ignore) {
            }
        } catch (Throwable e) {
            if (progress != null) {
                progress.log("  表 " + tableName + " 导出失败(跳过): " + e.getMessage());
            }
        } finally {
            close(rs);
            close(st);
        }
        return count;
    }

    /** 同 exportByColumns，但顺带收集某列值到 collectInto */
    private int exportByColumnsCollect(Connection conn, BufferedWriter w, String tableName,
                                       Set<String> keySet, String[] allowedCols,
                                       String collectCol, Set<String> collectInto,
                                       Progress progress) {
        if (keySet.isEmpty()) {
            return 0;
        }
        String matchCol = null;
        try {
            Set<String> cols = columnsOf(conn, tableName);
            if (cols == null) {
                logSkip(progress, tableName, "表不存在或不可读");
                return 0;
            }
            for (String a : allowedCols) {
                if (cols.contains(a.toLowerCase(Locale.ENGLISH))) {
                    matchCol = a;
                    break;
                }
            }
            if (matchCol == null) {
                logSkip(progress, tableName,
                        "无可用关联列（候选: " + String.join("/", allowedCols) + "）");
                return 0;
            }
        } catch (Throwable e) {
            logSkip(progress, tableName, "读列失败: " + e.getMessage());
            return 0;
        }
        StringBuilder where = new StringBuilder(matchCol).append(" in (");
        int i = 0;
        for (String k : keySet) {
            if (i > 0) {
                where.append(",");
            }
            if (i >= 900) {
                break;
            }
            where.append('\'').append(k.replace("'", "''")).append('\'');
            i++;
        }
        where.append(")");
        return exportRows(conn, w, tableName, where.toString(), collectCol, collectInto, progress);
    }

    /** 核心写行 */
    private int exportRows(Connection conn, BufferedWriter w, String tableName,
                           String where, String collectCol, Set<String> collectInto,
                           Progress progress) {
        int count = 0;
        Statement st = null;
        ResultSet rs = null;
        try {
            String sql = "select * from " + tableName + (where == null ? "" : " where " + where);
            if (progress != null) {
                progress.log("  开始导出表 " + tableName + " ...");
            }
            st = conn.createStatement();
            try {
                st.setQueryTimeout(120); // 秒；单表查询超时则跳过，避免卡死
            } catch (Throwable ignore) {
            }
            rs = st.executeQuery(sql);
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            int idxCollect = -1;
            if (collectCol != null) {
                for (int c = 1; c <= cols; c++) {
                    if (md.getColumnName(c).equalsIgnoreCase(collectCol)) {
                        idxCollect = c;
                        break;
                    }
                }
            }
            String[] names = new String[cols];
            for (int c = 0; c < cols; c++) {
                names[c] = md.getColumnName(c + 1);
            }
            while (rs.next()) {
                if (idxCollect > 0 && collectInto != null) {
                    String v = rs.getString(idxCollect);
                    if (v != null && !v.isEmpty()) {
                        collectInto.add(v);
                    }
                }
                StringBuilder sb = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
                for (int c = 0; c < cols; c++) {
                    if (c > 0) {
                        sb.append(", ");
                    }
                    sb.append(names[c]);
                }
                sb.append(") VALUES (");
                for (int c = 1; c <= cols; c++) {
                    if (c > 1) {
                        sb.append(", ");
                    }
                    sb.append(value(rs, md, c));
                }
                sb.append(");");
                w.write(sb.toString());
                w.newLine();
                count++;
            }
            if (progress != null) {
                progress.log("  表 " + tableName + "：" + count + " 行");
            }
            try {
                w.flush();
            } catch (Throwable ignore) {
            }
        } catch (Throwable e) {
            if (progress != null) {
                progress.log("  表 " + tableName + " 导出失败(跳过): " + e.getMessage());
            }
        } finally {
            close(rs);
            close(st);
        }
        return count;
    }

    /** 读取某表真实列名（小写）；表不存在返回 null */
    private Set<String> columnsOf(Connection conn, String tableName) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select * from " + tableName + " where rownum < 1")) {
            ResultSetMetaData md = rs.getMetaData();
            Set<String> cols = new LinkedHashSet<String>();
            for (int c = 1; c <= md.getColumnCount(); c++) {
                cols.add(md.getColumnName(c).toLowerCase(Locale.ENGLISH));
            }
            return cols;
        } catch (Throwable e) {
            return null;
        }
    }

    private void logSkip(Progress progress, String table, String why) {
        if (progress != null) {
            progress.log("  表 " + table + " 跳过：" + why);
        }
    }

    /** 每组表格导出完成后推进确定进度条（"x/N 标签"），驱动 UI 显示百分比 */
    private static void emitStep(Progress progress, int[] counter, int total, String label) {
        if (progress == null) {
            return;
        }
        counter[0]++;
        progress.step(counter[0] + "/" + total + " " + label);
    }

    private static int indexOf(ResultSetMetaData md, String name) {
        try {
            for (int c = 1; c <= md.getColumnCount(); c++) {
                if (md.getColumnName(c).equalsIgnoreCase(name)) {
                    return c;
                }
            }
        } catch (Throwable ignore) {
        }
        return -1;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /** 求单个字段的 SQL 字面量（与用户样例逐字节一致） */
    private static String value(ResultSet rs, ResultSetMetaData md, int i) throws Exception {
        int sqlType = md.getColumnType(i);
        if (isBlobType(sqlType)) {
            return "null";
        }
        Object o;
        if (sqlType == Types.CLOB || sqlType == Types.NCLOB) {
            o = rs.getString(i);
        } else {
            o = rs.getObject(i);
        }
        if (o == null) {
            return "null";
        }
        if (o instanceof Timestamp) {
            return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Timestamp) o) + "'";
        }
        if (o instanceof java.sql.Date) {
            return "'" + new SimpleDateFormat("yyyy-MM-dd").format((java.sql.Date) o) + "'";
        }
        if (o instanceof java.sql.Time) {
            return "'" + new SimpleDateFormat("HH:mm:ss").format((java.sql.Time) o) + "'";
        }
        if (o instanceof Number) {
            return o.toString();
        }
        if (o instanceof Boolean) {
            return ((Boolean) o) ? "1" : "0";
        }
        if (o instanceof Blob || o instanceof Clob) {
            return "null";
        }
        String s = String.valueOf(o);
        return "'" + s.replace("'", "''") + "'";
    }

    private static boolean isBlobType(int t) {
        return t == Types.BLOB || t == Types.BINARY || t == Types.VARBINARY
                || t == Types.LONGVARBINARY;
    }

    private static void close(Statement st) {
        if (st != null) {
            try {
                st.close();
            } catch (Throwable ignore) {
            }
        }
    }

    private static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Throwable ignore) {
            }
        }
    }
}
