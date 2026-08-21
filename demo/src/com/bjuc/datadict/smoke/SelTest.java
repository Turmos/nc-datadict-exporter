package com.bjuc.datadict.smoke;

import com.bjuc.datadict.core.ConfigStore;
import com.bjuc.datadict.core.DataSourceCfg;
import com.bjuc.datadict.ui.MainFrame;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** 验证左侧数据源列表点击后回填表单（配合隔离 APPDATA，不影响真实配置）。 */
public class SelTest {

    private static List<Component> all(Container c) {
        List<Component> out = new ArrayList<Component>();
        for (Component ch : c.getComponents()) {
            out.add(ch);
            if (ch instanceof Container) {
                out.addAll(all((Container) ch));
            }
        }
        return out;
    }

    private static List<Component> rows(MainFrame f) throws Exception {
        List<Component> comps = all(f.getContentPane());
        List<Component> rows = new ArrayList<Component>();
        for (Component c : comps) {
            if (c.getClass().getSimpleName().equals("DsRow")) {
                rows.add(c);
            }
        }
        return rows;
    }

    private static JTextField field(MainFrame f, String name) throws Exception {
        Field fd = MainFrame.class.getDeclaredField(name);
        fd.setAccessible(true);
        return (JTextField) fd.get(f);
    }

    private static void clickRow(MainFrame f, int rowIndex) throws Exception {
        List<Component> rows = rows(f);
        if (rowIndex >= rows.size()) {
            throw new IllegalStateException("row " + rowIndex + " not found, rows=" + rows.size());
        }
        Component row = rows.get(rowIndex);
        row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 5, 5, 1, false));
    }

    private static void check(String a, String b, String label) {
        if (!a.equals(b)) {
            throw new IllegalStateException(label + ": got=" + a + " expect=" + b);
        }
        System.out.println("PASS " + label + " -> " + a);
    }

    private static void checkRows(MainFrame f, int expect) throws Exception {
        int n = rows(f).size();
        if (n != expect) {
            throw new IllegalStateException("row count=" + n + " expect=" + expect);
        }
    }

    public static void main(String[] args) throws Exception {
        // 先清空再写入两个数据源（本进程使用隔离 APPDATA）
        ConfigStore.saveAll(new ArrayList<DataSourceCfg>());
        DataSourceCfg ds0 = new DataSourceCfg();
        ds0.name = "示例_生产";
        ds0.jdbcUrl = "192.168.1.10:1521/orcl";
        DataSourceCfg ds1 = new DataSourceCfg();
        ds1.name = "示例_测试";
        ds1.jdbcUrl = "192.168.1.20:1521/orcl";
        List<DataSourceCfg> cfgs = new ArrayList<DataSourceCfg>();
        cfgs.add(ds0);
        cfgs.add(ds1);
        ConfigStore.saveAll(cfgs);

        SwingUtilities.invokeAndWait(() -> {
            try {
                com.formdev.flatlaf.FlatLightLaf.setup();
            } catch (Throwable ignore) {
            }
            MainFrame f = new MainFrame();
            f.pack();

            JTextField nameF;
            JTextField urlF;
            try {
                nameF = field(f, "nameField");
                urlF = field(f, "urlField");
                checkRows(f, 2);
                check(nameF.getText(), "示例_生产", "default name");
                check(urlF.getText(), "192.168.1.10:1521/orcl", "default url");

                clickRow(f, 1);
                check(nameF.getText(), "示例_测试", "after click row1 name");
                check(urlF.getText(), "192.168.1.20:1521/orcl", "after click row1 url");

                clickRow(f, 0);
                check(nameF.getText(), "示例_生产", "after click row0 name");
                check(urlF.getText(), "192.168.1.10:1521/orcl", "after click row0 url");

                System.out.println("SEL_OK");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            } finally {
                f.dispose();
            }
        });
    }
}
