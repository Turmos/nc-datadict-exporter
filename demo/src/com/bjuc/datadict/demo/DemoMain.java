package com.bjuc.datadict.demo;

import com.bjuc.datadict.core.model.DataDictionary;
import com.bjuc.datadict.export.HtmlExporter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 演示：生成一份示例数据字典网页，用于自测渲染效果。 */
public class DemoMain {
    public static void main(String[] args) throws Exception {
        DataDictionary d = new DataDictionary();
        d.ncVersion = "NC6.5 (Build 6.5.26)";
        d.groupName = "示例集团";
        d.databaseName = "NC65_ORACLE";
        d.generateTime = "2026-08-17 10:30:00";
        d.remark = "示例数据字典";

        DataDictionary.Module md = new DataDictionary.Module();
        md.id = "p7";
        md.name = "p7";
        md.displayname = "集团项目管理";

        DataDictionary.Comp c1 = new DataDictionary.Comp();
        c1.id = "c1";
        c1.name = "bas_p7_project";
        c1.displayname = "项目基本信息";
        c1.ownModule = "p7";
        c1.billTypeName = "项目立项单";
        c1.billTypeCode = "P7PCLX01";
        c1.nodeName = "项目立项";

        c1.classes = new ArrayList<DataDictionary.Cls>();
        c1.classes.add(entityProject());
        c1.classes.add(entityProjectCost());

        DataDictionary.Comp c2 = new DataDictionary.Comp();
        c2.id = "c2";
        c2.name = "base_sys";
        c2.displayname = "系统管理";
        c2.ownModule = "p7";
        c2.classes = new ArrayList<DataDictionary.Cls>();
        c2.classes.add(entityUser());

        md.comps = new ArrayList<DataDictionary.Comp>();
        md.comps.add(c1);
        md.comps.add(c2);

        d.modules = new ArrayList<DataDictionary.Module>();
        d.modules.add(md);

        for (DataDictionary.Module m : d.modules) {
            for (DataDictionary.Comp c : m.comps) {
                d.totalComponents++;
                d.totalClasses += c.classes.size();
                for (DataDictionary.Cls cl : c.classes) {
                    d.totalProps += cl.props.size();
                }
            }
        }
        d.totalModules = d.modules.size();

        File out = new File("demo_out/demo_示例_数据字典.html");
        out.getParentFile().mkdirs();
        HtmlExporter.export(d, out);
        System.out.println("demo html -> " + out.getAbsolutePath());
    }

    private static DataDictionary.Cls entityProject() {
        DataDictionary.Cls cl = new DataDictionary.Cls();
        cl.id = "e1";
        cl.name = "ProjectVO";
        cl.displayName = "项目";
        cl.tableName = "p7_project";
        cl.fullClassName = "nc.p7.ProjectVO";
        cl.aggFullClassName = "nc.p7.agg.ProjectAggVO";
        cl.classType = 201;
        cl.props = new ArrayList<DataDictionary.Prop>();

        cl.props.add(prop("pk_project", "项目主键", "UFID", "", "N", "Y", "", "主键标识", true));
        cl.props.add(prop("projectcode", "项目编码", "String", "30", "N", "Y", "", "", false));
        cl.props.add(prop("projectname", "项目名称", "String", "100", "N", "N", "", "", false));
        cl.props.add(prop("pstartdate", "开工日期", "UFDate", "", "N", "N", "", "", false));
        cl.props.add(prop("budget", "预算金额", "UFMoney", "", "N", "N", "0.00", "", false));
        cl.props.add(prop("status", "项目状态", "枚举", "", "N", "N", "", "枚举：项目状态", false,
                EnumVal("0", "未立项"), EnumVal("1", "立项中"), EnumVal("2", "在建"), EnumVal("3", "竣工")));
        cl.props.add(prop("ownerdep", "所属部门", "引用", "", "Y", "N", "", "部门(dep)", false));
        cl.props.add(prop("memo", "备注", "备注", "500", "Y", "N", "", "", false));
        return cl;
    }

    private static DataDictionary.Cls entityProjectCost() {
        DataDictionary.Cls cl = new DataDictionary.Cls();
        cl.id = "e2";
        cl.name = "ProjectCostVO";
        cl.displayName = "项目成本";
        cl.tableName = "p7_project_cost";
        cl.fullClassName = "nc.p7.ProjectCostVO";
        cl.classType = 201;
        cl.props = new ArrayList<DataDictionary.Prop>();
        cl.props.add(prop("pk_cost", "成本主键", "UFID", "", "N", "Y", "", "", true));
        cl.props.add(prop("pk_project", "项目", "引用", "", "N", "N", "", "项目(ProjectVO)", false));
        cl.props.add(prop("costamt", "成本金额", "UFDouble", "18,2", "N", "N", "0", "", false));
        cl.props.add(prop("cdate", "发生日期", "UFDate", "", "N", "N", "", "", false));
        return cl;
    }

    private static DataDictionary.Cls entityUser() {
        DataDictionary.Cls cl = new DataDictionary.Cls();
        cl.id = "e3";
        cl.name = "UserVO";
        cl.displayName = "用户";
        cl.tableName = "sm_user";
        cl.fullClassName = "nc.bs.org.UserVO";
        cl.classType = 201;
        cl.props = new ArrayList<DataDictionary.Prop>();
        cl.props.add(prop("pk_user", "用户主键", "UFID", "", "N", "Y", "", "", true));
        cl.props.add(prop("user_code", "用户编码", "String", "20", "N", "N", "", "", false));
        cl.props.add(prop("user_name", "用户姓名", "String", "50", "N", "N", "", "", false));
        cl.props.add(prop("locked", "是否锁定", "UFBoolean", "", "N", "N", "N", "", false,
                EnumVal("Y", "锁定"), EnumVal("N", "正常")));
        return cl;
    }

    private static DataDictionary.Prop prop(String n, String dn, String type, String len,
                                            String nul, String def, String ref, String desc, boolean key,
                                            DataDictionary.EnumVal... evs) {
        DataDictionary.Prop p = new DataDictionary.Prop();
        p.name = n;
        p.displayName = dn;
        p.typeName = type;
        p.attrLength = len;
        p.nullable = nul;
        p.defaultValue = def;
        p.refDesc = ref;
        p.description = desc;
        p.isKey = key;
        if (evs.length > 0) {
            p.enumValues = new ArrayList<DataDictionary.EnumVal>();
            for (DataDictionary.EnumVal ev : evs) {
                p.enumValues.add(ev);
            }
        }
        return p;
    }

    private static DataDictionary.EnumVal EnumVal(String v, String n) {
        DataDictionary.EnumVal ev = new DataDictionary.EnumVal();
        ev.value = v;
        ev.name = n;
        return ev;
    }
}