package com.bjuc.datadict.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据字典聚合模型（导出 JSON 时直接序列化）。
 */
public class DataDictionary {
    public String toolName = "NC 数据字典";
    public String ncVersion = "";
    public String groupName = "";
    public String databaseName = "";
    public String generateTime = "";
    public String remark = "NC 数据字典";
    public int totalModules;
    public int totalComponents;
    public int totalClasses;
    public int totalProps;

    public List<Module> modules = new ArrayList<Module>();

    public static class Module {
        public String id;
        public String name;
        public String displayname;
        public List<Comp> comps = new ArrayList<Comp>();
    }

    public static class Comp {
        public String id;
        public String name;
        public String displayname;
        public String ownModule;
        public String billTypeCode;
        public String billTypeName;
        public String nodeName;
        public String beanConfig;
        public List<Cls> classes = new ArrayList<Cls>();
    }

    public static class Cls {
        public String id;
        public String name;
        public String displayName;
        public String tableName;
        public String fullClassName;
        public String aggFullClassName;
        public String componentId;
        public int classType;
        public List<Prop> props = new ArrayList<Prop>();
        /** 实体关联的功能/菜单/单据类型（按 md_class.id ↔ sm_funcregister.mdid 关联） */
        public List<ClsFunc> funcs = new ArrayList<ClsFunc>();
    }

    /** 实体关联的功能注册：功能号 + 菜单 + 单据类型 */
    public static class ClsFunc {
        public String funcode;
        public String menuitemcode;
        public String menuitemname;
        public String billtypecode;
        public String billtypename;
    }

    public static class Prop {
        public String name;
        public String displayName;
        public String typeName;
        public String fieldType;
        public String attrLength;
        public String nullable;
        public String defaultValue;
        public String description;
        public String refDesc;
        /** 被引用对象 id（引用类 / 枚举类），用于前端“穿透”跳到目标实体查看字段 */
        public String refId;
        public boolean isKey;
        public List<EnumVal> enumValues;
    }

    public static class EnumVal {
        public String value;
        public String name;
    }
}