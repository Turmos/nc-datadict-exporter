package com.bjuc.datadict.core;

/**
 * 数据源配置（内存态，密码为明文；持久化时由 ConfigStore 加密）。
 */
public class DataSourceCfg {
    public String name = "";
    public String jdbcUrl = "";
    public String user = "";
    public String password = "";
    public String driverClass = "oracle.jdbc.OracleDriver";
    public String remark = "";

    public DataSourceCfg() {
    }

    public DataSourceCfg copy() {
        DataSourceCfg c = new DataSourceCfg();
        c.name = name;
        c.jdbcUrl = jdbcUrl;
        c.user = user;
        c.password = password;
        c.driverClass = driverClass;
        c.remark = remark;
        return c;
    }

    public String display() {
        return name;
    }
}
