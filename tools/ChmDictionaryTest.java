import com.bjuc.datadict.core.ChmDictionary;
import com.bjuc.datadict.core.model.DataDictionary;
import com.bjuc.datadict.core.model.DataDictionary.Cls;
import com.bjuc.datadict.core.model.DataDictionary.Comp;
import com.bjuc.datadict.core.model.DataDictionary.Module;
import com.bjuc.datadict.core.model.DataDictionary.Prop;

import java.io.File;
import java.util.ArrayList;

/**
 * ChmDictionary 兜底逻辑的本地验证（不连库）。
 * 用法：javac -cp out\mainclasses;lib\fastjson-1.2.83.jar ChmDictionaryTest.java
 *       java  -cp .;out\mainclasses;lib\fastjson-1.2.83.jar ChmDictionaryTest
 */
public class ChmDictionaryTest {

    static int failed = 0;

    static void check(String label, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + label);
        if (!ok) {
            failed++;
        }
    }

    public static void main(String[] args) {
        DataDictionary dict = new DataDictionary();

        // 模块1：BD_DEFDOC —— 表名空中文名，字段空中文名；期望被 CHM 补上
        Module m1 = new Module();
        m1.name = "m_defdoc";
        Comp c1 = new Comp();
        Cls defdoc = new Cls();
        defdoc.tableName = "bd_defdoc";
        defdoc.displayName = null; // 数据库查不到
        Prop code = new Prop();
        code.name = "code";
        code.displayName = null;
        Prop pk = new Prop();
        pk.name = "pk_defdoc";
        pk.displayName = "主键"; // 数据库已有，不应被覆盖
        defdoc.props = new ArrayList<Prop>();
        defdoc.props.add(code);
        defdoc.props.add(pk);
        c1.classes = new ArrayList<Cls>();
        c1.classes.add(defdoc);
        m1.comps = new ArrayList<Comp>();
        m1.comps.add(c1);

        // 模块2：SM_USER —— 已有中文名，不应被覆盖
        Module m2 = new Module();
        Comp c2 = new Comp();
        Cls user = new Cls();
        user.tableName = "sm_user";
        user.displayName = "用户（库内）";
        Prop uname = new Prop();
        uname.name = "user_name";
        uname.displayName = "登录名";
        user.props = new ArrayList<Prop>();
        user.props.add(uname);
        c2.classes = new ArrayList<Cls>();
        c2.classes.add(user);
        m2.comps = new ArrayList<Comp>();
        m2.comps.add(c2);

        // 模块3：未知表 —— 不应报错，保持原样
        Module m3 = new Module();
        Comp c3 = new Comp();
        Cls unknown = new Cls();
        unknown.tableName = "xx_no_such_table_123";
        unknown.displayName = null;
        c3.classes = new ArrayList<Cls>();
        c3.classes.add(unknown);
        m3.comps = new ArrayList<Comp>();
        m3.comps.add(c3);

        dict.modules = new ArrayList<Module>();
        dict.modules.add(m1);
        dict.modules.add(m2);
        dict.modules.add(m3);

        ChmDictionary.backfill(dict);

        check("BD_DEFDOC 表中文名补为 自定义档案", "自定义档案".equals(defdoc.displayName));
        check("BD_DEFDOC.CODE 字段中文名补为非空", code.displayName != null && !code.displayName.isEmpty());
        check("BD_DEFDOC.PK_DEFDOC 已有中文名未被覆盖", "主键".equals(pk.displayName));
        check("SM_USER 已有中文名未被覆盖", "用户（库内）".equals(user.displayName));
        check("SM_USER.USER_NAME 已有中文名未被覆盖", "登录名".equals(uname.displayName));
        check("未知表保持原样", unknown.displayName == null);

        System.out.println();
        System.out.println(failed == 0 ? "全部通过" : ("失败 " + failed + " 项"));
        System.exit(failed == 0 ? 0 : 1);
    }
}
