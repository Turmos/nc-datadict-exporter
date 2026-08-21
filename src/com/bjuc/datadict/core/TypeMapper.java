package com.bjuc.datadict.core;

import java.util.HashMap;
import java.util.Map;

/**
 * NC 元数据内置类型码 -> 展示类型映射。
 */
public final class TypeMapper {
    private static final Map<String, String> MAP = new HashMap<String, String>();

    static {
        MAP.put("BS000010000100001001", "String");
        MAP.put("BS000010000100001051", "UFID");
        MAP.put("BS000010000100001004", "Integer");
        MAP.put("BS000010000100001031", "UFDouble");
        MAP.put("BS000010000100001032", "UFBoolean");
        MAP.put("BS000010000100001033", "UFDate");
        MAP.put("BS000010000100001037", "UFDate_begin");
        MAP.put("BS000010000100001038", "UFDate_end");
        MAP.put("BS000010000100001039", "UFLiteralDate");
        MAP.put("BS000010000100001034", "UFDateTime");
        MAP.put("BS000010000100001036", "UFTime");
        MAP.put("BS000010000100001040", "BigDecimal");
        MAP.put("BS000010000100001052", "UFMoney");
        MAP.put("BS000010000100001055", "Image");
        MAP.put("BS000010000100001053", "BLOB");
        MAP.put("BS000010000100001059", "自由项");
        MAP.put("BS000010000100001030", "备注");
        MAP.put("BS000010000100001058", "多语文本");
        MAP.put("BS000010000100001056", "自定义项");
    }

    private TypeMapper() {
    }

    /** 有映射返回映射名，否则返回 null（null 表示可能是引用类/枚举） */
    public static String of(String dataType) {
        if (dataType == null) {
            return null;
        }
        return MAP.get(dataType);
    }
}