package com.bjuc.datadict.ui;

import com.bjuc.datadict.Main;
import com.bjuc.datadict.core.ConfigStore;
import com.bjuc.datadict.core.DataSourceCfg;
import com.bjuc.datadict.core.DictLoader;
import com.bjuc.datadict.core.model.DataDictionary;
import com.bjuc.datadict.export.HtmlExporter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bjuc.datadict.theme.AppTheme;
import com.bjuc.datadict.theme.ThemeManager;

/**
 * NC 数据字典导出工具 —— 主界面。
 * 极简白 + 灰 + 科技蓝；左侧数据源管理 + 右侧配置/导出，底部可折叠日志控制台。
 */
public class MainFrame extends JFrame {

    private final List<DataSourceCfg> cfgs = ConfigStore.loadAll();
    private int editingIndex = -1;

    private final JTextField nameField = new JTextField();
    private final JTextField urlField = new JTextField();
    private final JTextField userField = new JTextField();
    private final JPasswordField pwdField = new JPasswordField();
    private final JButton btnEye = new JButton(Icons.eye(Main.TEXT_SUB));
    private boolean pwdVisible = false;
    /** 测试连接：状态胶囊 + 详情 + 旋转动画 */
    private final StatusBadge testStatusBadge = new StatusBadge();
    private final JLabel testDetailLabel = new JLabel();
    private Timer testSpinnerTimer;
    private int testSpinnerAngle = 0;
    private final JLabel errUrlHint = new JLabel();
    private final JLabel errPwdHint = new JLabel();
    private final JLabel errUserHint = new JLabel();
    private final JLabel errNameHint = new JLabel();
    private final JTextField remarkField = new JTextField();
    private final JComboBox<String> driverCombo = new JComboBox<>(new String[]{
            "oracle.jdbc.OracleDriver（Oracle Thin，推荐）",
            "oracle.jdbc.driver.OracleDriver（Oracle 8i/9i 老版本）",
            "com.mysql.cj.jdbc.Driver（MySQL）",
            "org.postgresql.Driver（PostgreSQL）",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver（SQL Server）"
    });
    private final JTextField outDirField = new JTextField();
    private final JCheckBox openChk = new JCheckBox("导出成功后自动打开网页", true);

    private JButton btnNew;
    private JButton btnSave;
    private JButton btnExport;
    private JButton btnTest;
    private JButton btnLoadScope;

    private final JPanel dsListPanel = new JPanel();
    private final JScrollPane dsScroll = new JScrollPane();

    private final List<ScopeModuleRow> scopeRows = new ArrayList<ScopeModuleRow>();
    private final JPanel scopeListPanel = new JPanel();
    private final JTextField scopeFilter = new JTextField();
    private final List<JCheckBox> visibleBoxes = new ArrayList<JCheckBox>();
    private final JLabel scopeSummary = new JLabel("尚未读取模块列表（未勾选组件 = 全库导出）");

    // ===== 集成：SQL 脚本导出（导出类型 + 功能节点号输入） =====
    private final JComboBox<String> exportTypeCombo = new JComboBox<>(new String[]{
            "数据字典(HTML)", "SQL脚本(INSERT)", "自定义档案(INSERT)"
    });
    private final CardLayout sqlScopeLayout = new CardLayout();
    private final JPanel sqlScopeContent = new JPanel(sqlScopeLayout);
    private final JTextField sqlFuncodeField = new JTextField();
    private final JTextField defdocPkField = new JTextField();
    /** 程序界面主题下拉（10 套配色），默认选中上次保存的主题 */
    private final JComboBox<AppTheme> themeSelect = new JComboBox<>(AppTheme.all());

    private final JTextArea logArea = new JTextArea();
    private final JScrollPane logScroll = new JScrollPane(logArea);
    private final JLabel logToggleIcon = new JLabel(Icons.chevronRight(Main.TEXT_SUB));
    private boolean logVisible = false;
    private final JLabel statusLabel = new JLabel("就绪");

    /** 渐变色进度条（读取模块 / 导出共用，同一时刻至多一个流程在使用） */
    private final GradientBar gradientBar = new GradientBar();

    public MainFrame() {
        super(Main.APP_NAME);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(loadAppIcon());
        setSize(1240, 980);
        setMinimumSize(new Dimension(1080, 820));
        setLocationRelativeTo(null);
        build();
        bindActions();
        if (cfgs.size() > 0) {
            editingIndex = 0;
            fillForm(cfgs.get(0));
        } else {
            fillForm(new DataSourceCfg());
        }
        refreshSidebar();
        // 以下监听只需注册一次（重建界面时这些实例组件沿用，勿重复注册）
        themeSelect.addActionListener(e -> applyThemeFromCombo());
        btnEye.addActionListener(e -> togglePwd());
        setLogExpanded(false);
        // 默认输出目录 = 桌面（仅当未设置过时）
        if (outDirField.getText() == null || outDirField.getText().trim().isEmpty()) {
            String home = System.getProperty("user.home");
            if (home != null) {
                File desktop = new File(home, "Desktop");
                if (desktop.isDirectory()) {
                    outDirField.setText(desktop.getAbsolutePath());
                }
            }
        }
    }

    /* ====================== 构建 ====================== */

    /** 从 jar 内资源加载程序图标（app.png），失败时返回 null（用系统默认图标） */
    private java.awt.Image loadAppIcon() {
        try {
            java.net.URL url = getClass().getResource("/app.png");
            if (url == null) return null;
            return getToolkit().getImage(url);
        } catch (Throwable t) {
            return null;
        }
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildMain(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    /** 程序主题下拉：应用所选主题、持久化、并重建界面（立即生效） */
    private void applyThemeFromCombo() {
        Object sel = themeSelect.getSelectedItem();
        if (!(sel instanceof AppTheme)) {
            return;
        }
        AppTheme t = (AppTheme) sel;
        if (t == AppTheme.active()) {
            return; // 重复选择同一主题，跳过，避免重建死循环
        }
        AppTheme.setActive(t);
        ThemeManager.save(t);
        Main.applyTheme(t);
        rebuildUI();
    }

    /** 重建整个界面，套用当前主题的颜色；保留各输入框内容与勾选状态 */
    private void rebuildUI() {
        build();
        bindActions();
        refreshSidebar();
        renderScopeList(scopeFilter.getText());
        onExportTypeChanged();
        // 让主题下拉回到当前生效主题
        if (themeSelect.getSelectedItem() != AppTheme.active()) {
            themeSelect.setSelectedItem(AppTheme.active());
        }
        validate();
        repaint();
    }

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, Main.BLUE_DARK, getWidth(), 0, Main.BLUE));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        h.setPreferredSize(new Dimension(1, 60));
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 22));
        JLabel title = new JLabel("NC 数据字典导出工具");
        title.setForeground(Main.TEXT_ON_PRIMARY);
        title.setFont(f(17, Font.BOLD));
        JLabel sub = new JLabel("极简 · 高效 · 安全  —  连接 NC6.5 Oracle 元数据库，一键导出离线数据字典网页");
        sub.setForeground(tint(Main.TEXT_ON_PRIMARY, 215));
        sub.setFont(f(11.5f, Font.PLAIN));
        box.add(title);
        box.add(Box.createVerticalStrut(2));
        box.add(sub);
        h.add(box, BorderLayout.WEST);

        // 个人品牌 logo：Turmo
        JPanel logoBox = new JPanel();
        logoBox.setOpaque(false);
        logoBox.setLayout(new BoxLayout(logoBox, BoxLayout.X_AXIS));
        logoBox.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 22));
        JLabel chip = new JLabel("T", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tint(Main.TEXT_ON_PRIMARY, 46));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        chip.setOpaque(false);
        chip.setPreferredSize(new Dimension(27, 27));
        chip.setForeground(Main.TEXT_ON_PRIMARY);
        chip.setFont(f(13.5f, Font.BOLD));
        logoBox.add(chip);
        logoBox.add(Box.createHorizontalStrut(9));
        JLabel word = new JLabel("Turmo");
        word.setForeground(Main.TEXT_ON_PRIMARY);
        word.setFont(f(15, Font.BOLD));
        logoBox.add(word);
        logoBox.add(Box.createHorizontalStrut(7));
        JLabel tag = new JLabel("个人工具 · v1.0");
        tag.setForeground(tint(Main.TEXT_ON_PRIMARY, 120));
        tag.setFont(f(10.5f, Font.PLAIN));
        logoBox.add(tag);

        // 程序界面主题下拉
        JPanel themeBox = new JPanel();
        themeBox.setOpaque(false);
        themeBox.setLayout(new BoxLayout(themeBox, BoxLayout.X_AXIS));
        themeBox.setBorder(BorderFactory.createEmptyBorder(9, 6, 9, 6));
        JLabel themeCap = new JLabel("主题");
        themeCap.setForeground(tint(Main.TEXT_ON_PRIMARY, 235));
        themeCap.setFont(f(12, Font.PLAIN));
        themeBox.add(themeCap);
        themeBox.add(Box.createHorizontalStrut(7));
        themeSelect.setFont(f(12, Font.PLAIN));
        themeSelect.setPreferredSize(new Dimension(118, 30));
        themeSelect.setMaximumSize(new Dimension(118, 30));
        themeSelect.setSelectedItem(AppTheme.active());
        themeSelect.setToolTipText("切换程序界面主题（10 套配色），立即生效并记住选择");
        themeBox.add(themeSelect);
        themeBox.add(Box.createHorizontalStrut(4));

        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.X_AXIS));
        east.add(themeBox);
        east.add(logoBox);
        h.add(east, BorderLayout.EAST);
        return h;
    }

    private JPanel buildStatusBar() {
        JPanel s = new JPanel(new BorderLayout(10, 0));
        s.setBackground(Main.SURFACE);
        s.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Main.LINE));
        statusLabel.setForeground(Main.TEXT_SUB);
        statusLabel.setFont(f(12, Font.PLAIN));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 18, 0, 12));
        s.add(statusLabel, BorderLayout.WEST);
        JPanel barWrap = new JPanel(new BorderLayout());
        barWrap.setBackground(Main.SURFACE);
        barWrap.setBorder(BorderFactory.createEmptyBorder(10, 4, 6, 18));
        barWrap.add(gradientBar, BorderLayout.CENTER);
        gradientBar.setVisible(false);
        s.add(barWrap, BorderLayout.CENTER);
        s.setPreferredSize(new Dimension(1, 30));
        return s;
    }

    private JPanel buildSidebar() {
        JPanel side = new JPanel(new BorderLayout(0, 12));
        side.setBackground(Main.SURFACE);
        side.setBorder(BorderFactory.createEmptyBorder(16, 14, 12, 14));
        side.setPreferredSize(new Dimension(258, 1));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel cap = new JLabel("数据源管理");
        cap.setFont(f(14, Font.BOLD));
        cap.setForeground(Main.TEXT);
        top.add(cap);
        top.add(Box.createVerticalStrut(12));

        btnNew = new JButton("新增数据源", Icons.plus(Main.TEXT_ON_PRIMARY));
        btnNew.setBackground(Main.BLUE);
        btnNew.setForeground(Main.TEXT_ON_PRIMARY);
        btnNew.setFocusPainted(false);
        btnNew.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        btnNew.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnNew.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(btnNew);
        top.add(Box.createVerticalStrut(14));

        dsListPanel.setLayout(new BoxLayout(dsListPanel, BoxLayout.Y_AXIS));
        dsListPanel.setBackground(Main.SURFACE);
        dsScroll.setViewportView(dsListPanel);
        dsScroll.setBorder(null);
        dsScroll.getViewport().setBackground(Main.SURFACE);
        dsScroll.getVerticalScrollBar().setUnitIncrement(14);

        side.add(top, BorderLayout.NORTH);
        side.add(dsScroll, BorderLayout.CENTER);
        return side;
    }
    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout(0, 10));
        main.setBackground(Main.BG);
        main.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        // 左列：连接配置（含链接信息 + 附加信息备注）垂直拉满；右列：导出范围占大头
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(380, 1));
        left.add(buildConnCard(), BorderLayout.CENTER);

        JPanel split = new JPanel(new BorderLayout(12, 0));
        split.setOpaque(false);
        split.add(left, BorderLayout.WEST);
        split.add(buildScopeCard(), BorderLayout.CENTER);
        main.add(split, BorderLayout.CENTER);

        // 底部：导出操作栏 + 可折叠日志（固定不滚动）
        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(buildExportBar());
        south.add(vs(10));
        south.add(buildLogConsole());
        main.add(south, BorderLayout.SOUTH);
        return main;
    }

    private JPanel buildConnCard() {
        Card card = new Card();

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Main.SURFACE);
        JLabel t = new JLabel("连接配置");
        t.setFont(f(14, Font.BOLD));
        t.setForeground(Main.TEXT);
        header.add(t, BorderLayout.WEST);
        btnSave = outlineBtn("保存配置", Icons.save(Main.BLUE_DARK));
        header.add(btnSave, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        urlField.setToolTipText("Oracle Thin 连接串。可填完整 jdbc:oracle:thin:@host:1521/service，也可只填 host:port/service 或 host:port:sid（自动补全）");
        nameField.setToolTipText("给该数据源起个名字，例如：生产库 / 测试库");

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;

        // 行0：名称 / 用户名
        c.insets = new Insets(4, 8, 2, 8);
        c.gridy = 0;
        c.weighty = 0.1;
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        card.body.add(new JLabel("名称"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        card.body.add(fieldCell(nameField, errNameHint), c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        card.body.add(new JLabel("用户名"), c);
        c.gridx = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        card.body.add(fieldCell(userField, errUserHint), c);

        // 行1：JDBC URL
        c.insets = new Insets(4, 8, 2, 8);
        c.gridy = 1;
        c.weighty = 0.1;
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        card.body.add(new JLabel("JDBC URL"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        urlField.setPreferredSize(new Dimension(200, 30));
        card.body.add(fieldCell(urlField, errUrlHint), c);

        // 行2：密码
        c.insets = new Insets(4, 8, 2, 8);
        c.gridy = 2;
        c.weighty = 0.1;
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        card.body.add(new JLabel("密码"), c);
        JPanel pwWrap = new JPanel(new BorderLayout(6, 0));
        pwWrap.setBackground(Main.SURFACE);
        pwWrap.add(fieldCell(pwdField, errPwdHint), BorderLayout.CENTER);
        btnEye.setFocusPainted(false);
        btnEye.setBorderPainted(false);
        btnEye.setContentAreaFilled(false);
        btnEye.setPreferredSize(new Dimension(30, 30));
        btnEye.setToolTipText("显示 / 隐藏密码");
        pwWrap.add(btnEye, BorderLayout.EAST);
        c.gridx = 1; c.gridwidth = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        card.body.add(pwWrap, c);
        c.gridx = 2; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        card.body.add(Box.createVerticalStrut(0), c);

        // 行3：备注（集成附加信息，不另占卡片）
        c.insets = new Insets(4, 8, 2, 8);
        c.gridy = 3;
        c.weighty = 0.1;
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        card.body.add(new JLabel("备注"), c);
        remarkField.setToolTipText("给该数据源做备注说明，可留空");
        remarkField.setFont(f(12, Font.PLAIN));
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        remarkField.setPreferredSize(new Dimension(200, 28));
        card.body.add(remarkField, c);

        // 行4：驱动选择（可下拉选择常用驱动，也可自行输入自定义驱动类名）
        c.insets = new Insets(4, 8, 2, 8);
        c.gridy = 4;
        c.weighty = 0.1;
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        card.body.add(new JLabel("驱动"), c);
        driverCombo.setEditable(true);
        driverCombo.setPreferredSize(new Dimension(200, 30));
        driverCombo.setToolTipText("选择或输入 JDBC 驱动类名；Oracle 驱动自动扫描程序同级 drivers/ 目录加载");
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        card.body.add(driverCombo, c);

        // 行5：提示（加密说明 + 驱动说明）竖向堆叠
        c.insets = new Insets(2, 8, 4, 8);
        c.gridy = 5;
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        JPanel hints = new JPanel();
        hints.setBackground(Main.SURFACE);
        hints.setLayout(new BoxLayout(hints, BoxLayout.Y_AXIS));
        hints.setAlignmentY(Component.TOP_ALIGNMENT);
        JLabel lockTx = new JLabel("密码 AES-256 本地加密存储，不会上传");
        lockTx.setForeground(Main.TEXT_SUB);
        lockTx.setFont(f(11f, Font.PLAIN));
        lockTx.setAlignmentX(Component.LEFT_ALIGNMENT);
        hints.add(lockTx);
        JLabel drvHint = new JLabel("驱动类放程序同级 drivers/ 目录即可自动加载");
        drvHint.setForeground(Main.TEXT_SUB);
        drvHint.setFont(f(11f, Font.PLAIN));
        drvHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hints.add(Box.createVerticalStrut(2));
        hints.add(drvHint);
        card.body.add(hints, c);

        // 行6：分割线
        c.insets = new Insets(10, 4, 6, 4);
        c.gridy = 6;
        c.gridx = 0; c.gridwidth = 4; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        javax.swing.JSeparator sep = new javax.swing.JSeparator();
        sep.setForeground(Main.LINE);
        sep.setBackground(Main.LINE);
        card.body.add(sep, c);

        // 行7：数据库操作栏（测试连接 + 状态徽章）
        c.insets = new Insets(2, 4, 4, 4);
        c.gridy = 7;
        c.gridx = 0; c.gridwidth = 4; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        JPanel dbBar = new JPanel(new BorderLayout(14, 0));
        dbBar.setBackground(Main.SURFACE);
        btnTest = primaryBtn("测试连接", null);
        btnTest.setPreferredSize(new Dimension(150, 42));
        dbBar.add(btnTest, BorderLayout.WEST);

        JPanel statBox = new JPanel(new BorderLayout(10, 0));
        statBox.setBackground(Main.SURFACE);
        statBox.add(testStatusBadge, BorderLayout.WEST);
        statBox.add(testDetailLabel, BorderLayout.CENTER);
        dbBar.add(statBox, BorderLayout.CENTER);

        card.body.add(dbBar, c);


        card.add(card.body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildScopeCard() {
        Card card = new Card();
        JLabel t = new JLabel("导出范围");
        t.setFont(f(14, Font.BOLD));
        t.setForeground(Main.TEXT);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Main.SURFACE);
        header.add(t, BorderLayout.WEST);

        exportTypeCombo.setFont(f(12, Font.PLAIN));
        exportTypeCombo.setPreferredSize(new Dimension(170, 30));
        exportTypeCombo.setToolTipText("选择导出产物：数据字典网页 / 功能节点 SQL 脚本 / 自定义档案 SQL 脚本(全列 INSERT)");
        exportTypeCombo.addActionListener(e -> onExportTypeChanged());
        header.add(exportTypeCombo, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);

        // 顶部：搜索框 + 操作按钮
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(Main.SURFACE);
        scopeFilter.setToolTipText("按模块 / 组件名称关键字过滤，快速定位导出范围");
        scopeFilter.putClientProperty("JTextField.placeholderText", "搜索模块 / 组件…");
        scopeFilter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                renderScopeList(scopeFilter.getText());
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                renderScopeList(scopeFilter.getText());
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                renderScopeList(scopeFilter.getText());
            }
        });
        bar.add(scopeFilter, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setBackground(Main.SURFACE);
        btnLoadScope = flatBtn("读取模块列表");
        JButton btnAll = flatBtn("全选");
        JButton btnNone = flatBtn("取消全选");
        btnAll.addActionListener(e -> setAllScopes(true));
        btnNone.addActionListener(e -> setAllScopes(false));
        btns.add(btnLoadScope);
        btns.add(btnAll);
        btns.add(btnNone);
        bar.add(btns, BorderLayout.EAST);

        scopeSummary.setForeground(Main.TEXT_SUB);
        scopeSummary.setFont(f(11.5f, Font.PLAIN));

        scopeListPanel.setLayout(new BoxLayout(scopeListPanel, BoxLayout.Y_AXIS));
        scopeListPanel.setBackground(Main.SURFACE);
        JScrollPane sp = new JScrollPane(scopeListPanel);
        sp.setBorder(null);
        sp.getViewport().setBackground(Main.SURFACE);
        sp.setPreferredSize(new Dimension(1, 400));
        sp.getVerticalScrollBar().setUnitIncrement(16);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBackground(Main.SURFACE);
        body.add(bar, BorderLayout.NORTH);
        body.add(sp, BorderLayout.CENTER);
        body.add(scopeSummary, BorderLayout.SOUTH);
        // 数据字典(HTML) 卡片：沿用模块/组件勾选
        sqlScopeContent.add(body, "dict");
        // SQL脚本 卡片：功能节点号输入
        sqlScopeContent.add(buildSqlScopePanel(), "sql");
        // 自定义档案 卡片：档案分类 PK 输入
        sqlScopeContent.add(buildDefdocPanel(), "defdoc");
        sqlScopeLayout.show(sqlScopeContent, "dict");
        card.add(sqlScopeContent, BorderLayout.CENTER);
        return card;
    }

    /** 构建 SQL 脚本导出的「功能节点号输入」面板 */
    private JPanel buildSqlScopePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(Main.SURFACE);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Main.SURFACE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;

        JLabel lbl = new JLabel("功能节点号 (funcode)：");
        lbl.setFont(f(12.5f, Font.PLAIN));
        g.gridx = 0;
        g.gridy = 0;
        form.add(lbl, g);

        sqlFuncodeField.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14));
        sqlFuncodeField.setToolTipText("输入 NC 系统『功能注册表(sm_funcregister)』中的功能号，例如：6010cost 或 3110xx");
        g.gridx = 1;
        g.gridy = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        form.add(sqlFuncodeField, g);
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;

        p.add(form, BorderLayout.NORTH);

        JLabel hint = new JLabel("<html>以该节点为起点，<b>递归关联导出</b>该功能节点及其下级节点的元数据脚本：<br>"
                + "功能注册、菜单、按钮、参数、关联单据类型，以及单据/查询/打印模板、动作、业务类、编码规则等。<br>"
                + "（仅导出与此节点相关的行，逐表按物理列序生成全列 INSERT）</html>");
        hint.setForeground(Main.TEXT_SUB);
        hint.setFont(f(11.5f, Font.PLAIN));
        JPanel hintWrap = new JPanel(new BorderLayout());
        hintWrap.setBackground(Main.SURFACE);
        hintWrap.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        hintWrap.add(hint, BorderLayout.NORTH);
        p.add(hintWrap, BorderLayout.CENTER);
        return p;
    }

    /** 构建「自定义档案」导出的「档案分类 PK 输入」面板 */
    private JPanel buildDefdocPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(Main.SURFACE);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Main.SURFACE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;

        JLabel lbl = new JLabel("档案分类 PK (pk_defdoclist)：");
        lbl.setFont(f(12.5f, Font.PLAIN));
        g.gridx = 0;
        g.gridy = 0;
        form.add(lbl, g);

        defdocPkField.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14));
        defdocPkField.setToolTipText("输入自定义档案分类主键（bd_mode_all.mdclassid / bd_defdoclist.pk_defdoclist），例如：1001A3100000003J236Q");
        g.gridx = 1;
        g.gridy = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        form.add(defdocPkField, g);
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;

        p.add(form, BorderLayout.NORTH);

        JLabel hint = new JLabel("<html>按档案分类主键导出该<b>自定义档案</b>的全部关联脚本（共 7 张表）：<br>"
                + "档案分类(bd_defdoclist)、档案项(bd_defdoc)、模式(bd_mode_all / bd_mode_selected)、<br>"
                + "唯一性规则(bd_uniquerule)及其明细(bd_uniquerule_item)、引用信息(bd_refinfo)。<br>"
                + "（逐表按物理列序生成全列 INSERT；唯一性规则从 bd_uniquerule 收集 pk_rule 后关联导出其明细）</html>");
        hint.setForeground(Main.TEXT_SUB);
        hint.setFont(f(11.5f, Font.PLAIN));
        JPanel hintWrap = new JPanel(new BorderLayout());
        hintWrap.setBackground(Main.SURFACE);
        hintWrap.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        hintWrap.add(hint, BorderLayout.NORTH);
        p.add(hintWrap, BorderLayout.CENTER);
        return p;
    }

    private void onExportTypeChanged() {
        int idx = exportTypeCombo.getSelectedIndex();
        if (idx == 0) {
            sqlScopeLayout.show(sqlScopeContent, "dict");
            statusLabel.setText("数据字典模式：按模块/组件导出 HTML");
        } else if (idx == 1) {
            sqlScopeLayout.show(sqlScopeContent, "sql");
            statusLabel.setText("SQL 脚本模式：请输入功能节点号(funcode)后导出");
        } else {
            sqlScopeLayout.show(sqlScopeContent, "defdoc");
            statusLabel.setText("自定义档案模式：请输入档案分类 PK 后导出");
        }
    }

    private JPanel buildExportBar() {
        JPanel ep = new JPanel(new BorderLayout(0, 12));
        ep.setBackground(Main.SURFACE);
        ep.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Main.LINE),
                BorderFactory.createEmptyBorder(16, 18, 14, 18)));

        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setBackground(Main.SURFACE);
        JPanel outBox = new JPanel(new BorderLayout(8, 0));
        outBox.setBackground(Main.SURFACE);
        JLabel ol = new JLabel("输出目录");
        ol.setPreferredSize(new Dimension(64, 40));
        outBox.add(ol, BorderLayout.WEST);
        outBox.add(outDirField, BorderLayout.CENTER);
        JButton btnBrowse = outlineBtn("浏览", Icons.folder(Main.BLUE_DARK));
        btnBrowse.setPreferredSize(new Dimension(86, 40));
        btnBrowse.addActionListener(e -> chooseDir());
        outBox.add(btnBrowse, BorderLayout.EAST);
        row.add(outBox, BorderLayout.CENTER);

        btnExport = new JButton("开始导出", Icons.download(Main.TEXT_ON_PRIMARY));
        btnExport.setBackground(Main.BLUE);
        btnExport.setForeground(Main.TEXT_ON_PRIMARY);
        btnExport.setFocusPainted(false);
        btnExport.setFont(f(15, Font.BOLD));
        btnExport.setBorder(BorderFactory.createEmptyBorder(10, 26, 10, 26));
        btnExport.setPreferredSize(new Dimension(180, 44));
        row.add(btnExport, BorderLayout.EAST);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        south.setBackground(Main.SURFACE);
        openChk.setForeground(Main.TEXT_SUB);
        openChk.setFont(f(12, Font.PLAIN));
        south.add(openChk);

        ep.add(row, BorderLayout.NORTH);
        gradientBar.setVisible(false);
        ep.add(south, BorderLayout.SOUTH);
        return ep;
    }

    private JPanel buildLogConsole() {
        JPanel lg = new JPanel(new BorderLayout());
        lg.setBackground(Main.BG);

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setBackground(Main.BG);
        hdr.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        JLabel cap = new JLabel("运行日志");
        cap.setFont(f(13, Font.BOLD));
        cap.setForeground(Main.TEXT);
        hdr.add(cap, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setBackground(Main.BG);
        JButton btnClear = flatBtn("清空");
        btnClear.addActionListener(e -> logArea.setText(""));
        right.add(btnClear);
        right.add(logToggleIcon);
        hdr.add(right, BorderLayout.EAST);
        hdr.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        hdr.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setLogExpanded(!logVisible);
            }
        });

        logArea.setEditable(false);
        logArea.setFont(f(12, Font.PLAIN));
        logArea.setBackground(new Color(0x0F172A));
        logArea.setForeground(new Color(0xD7E4FF));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0x22304A)));

        lg.add(hdr, BorderLayout.NORTH);
        lg.add(logScroll, BorderLayout.CENTER);
        return lg;
    }

    private JPanel vs(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(1, h));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return p;
    }

    /* ====================== 测试连接视觉 ====================== */

    private static final Color SUCCESS_GREEN = new Color(0x52C41A);
    private static final Color DANGER_RED = new Color(0xFF4D4F);
    private static final Color TEST_LOADING = Main.BLUE;
    private static final Color TEST_IDLE = new Color(0x9AA3AE);

    /** 字段 + 其下方错误提示，竖向堆叠（错误出现时布局不跳变） */
    private JPanel fieldCell(Component field, JLabel err) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Main.SURFACE);
        field.setPreferredSize(new Dimension(200, 28));
        p.add(field, BorderLayout.CENTER);
        err.setFont(f(10.5f, Font.PLAIN));
        err.setForeground(DANGER_RED);
        err.setBorder(BorderFactory.createEmptyBorder(1, 1, 0, 0));
        err.setVisible(false);
        p.add(err, BorderLayout.SOUTH);
        return p;
    }

    /** 设置字段下方错误提示（空则隐藏） */
    private void setFieldError(JLabel err, String msg) {
        if (msg == null || msg.isEmpty()) {
            err.setText("");
            err.setVisible(false);
        } else {
            err.setText(msg);
            err.setVisible(true);
        }
    }

    /** 清空全部字段错误提示 */
    private void clearFieldErrors() {
        setFieldError(errNameHint, "");
        setFieldError(errUserHint, "");
        setFieldError(errUrlHint, "");
        setFieldError(errPwdHint, "");
    }

    /** 数据库操作栏上的主按钮 —— 蓝色描边 + 浅蓝填充，视觉焦点 */
    private JButton primaryBtn(String text, javax.swing.Icon icon) {
        JButton b = new JButton(text, icon);
        b.setFont(f(14, Font.BOLD));
        b.setForeground(Main.BLUE_DARK);
        b.setBackground(Main.BLUE_LIGHT);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Main.BLUE, 1),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)));
        return b;
    }

    /** 状态胶囊徽章：圆角底色 + 白字，随状态变色 */
    static class StatusBadge extends JLabel {
        private Color bg = TEST_IDLE;

        StatusBadge() {
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(f(12, Font.BOLD));
            setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
            setText("未测试");
            setPreferredSize(new Dimension(110, 28));
        }

        void setState(String text, Color color) {
            setText(text);
            bg = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 更新状态徽章 + 详情文本 */
    private void setTestStatus(String state, String detail, Color color) {
        testStatusBadge.setState(state, color);
        testDetailLabel.setFont(f(12, Font.PLAIN));
        if (detail == null || detail.isEmpty()) {
            testDetailLabel.setText("");
        } else {
            testDetailLabel.setText(detail);
        }
        testDetailLabel.setForeground(color);
    }

    /** 开始旋转加载动画（禁用按钮，防止重复点击） */
    private void startTestSpinner() {
        testSpinnerAngle = 0;
        if (testSpinnerTimer == null) {
            testSpinnerTimer = new Timer(30, e -> {
                testSpinnerAngle = (testSpinnerAngle + 12) % 360;
                btnTest.setIcon(Icons.spinner(Main.BLUE_DARK, testSpinnerAngle));
            });
        }
        btnTest.setIcon(Icons.spinner(Main.BLUE_DARK, 0));
        testSpinnerTimer.start();
    }

    /** 停止旋转动画，恢复按钮 */
    private void stopTestSpinner() {
        if (testSpinnerTimer != null) {
            testSpinnerTimer.stop();
        }
        btnTest.setIcon(null);
    }

    /** 从完整连接成功消息中提取精简短版本（如 11g） */
    private static String formatVersion(String msg) {
        if (msg == null || msg.isEmpty()) {
            return "连接成功";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+[A-Za-z]*)").matcher(msg);
        if (m.find()) {
            return "连接成功 " + m.group(1);
        }
        return "连接成功";
    }

    /** 把完整异常信息收敛成一行短概要 */
    private static String summarizeError(Throwable cause) {
        String m = cause.getMessage();
        if (m == null || m.isEmpty()) {
            m = String.valueOf(cause);
        }
        m = m.replaceAll("[\\r\\n]+", " ");
        if (m.length() > 60) {
            m = m.substring(0, 60) + "…";
        }
        return m;
    }

    /** 根据异常智能定位到具体错误字段 */
    private void localizeError(Throwable cause) {
        String m = String.valueOf(cause.getMessage()).toLowerCase();
        String cls = cause.getClass().getSimpleName().toLowerCase();
        boolean netErr = cls.contains("connect") || m.contains("connection refused")
                || m.contains("unknownhost") || m.contains("无法连接") || m.contains("通信链路")
                || m.contains("connection reset") || m.contains("connect timed out");
        boolean authErr = m.contains("ora-01017") || m.contains("invalid username")
                || m.contains("01017") || m.contains("登录拒绝") || m.contains("ora-28000")
                || m.contains("ora-28001") || m.contains("account is locked");
        boolean badUrl = m.contains("unknown host") || m.contains("invalid oracle url")
                || m.contains("cannot create poolableconnection") || m.contains("invalid url");

        if (authErr) {
            setFieldError(errPwdHint, "密码错误");
            setFieldError(errUserHint, "请检查用户名");
        } else if (netErr) {
            setFieldError(errUrlHint, "主机不可达，请检查地址 / 端口");
        } else if (badUrl) {
            setFieldError(errUrlHint, "连接串格式不合法");
        } else {
            setFieldError(errUrlHint, summarizeError(cause));
        }
    }

    /* ====================== 数据源管理 ====================== */
    /* ====================== 数据源管理 ====================== */

    private void refreshSidebar() {
        dsListPanel.removeAll();
        if (cfgs.isEmpty()) {
            JPanel empty = new JPanel();
            empty.setBackground(Main.SURFACE);
            empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
            empty.setBorder(BorderFactory.createEmptyBorder(46, 16, 20, 16));
            JLabel ic = new JLabel(Icons.database(Main.LINE));
            ic.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel l1 = new JLabel("暂无数据源");
            l1.setAlignmentX(Component.CENTER_ALIGNMENT);
            l1.setForeground(Main.TEXT_SUB);
            l1.setFont(f(13, Font.BOLD));
            JLabel l2 = new JLabel("点击上方「新增数据源」开始配置");
            l2.setAlignmentX(Component.CENTER_ALIGNMENT);
            l2.setForeground(Main.TEXT_SUB);
            l2.setFont(f(11, Font.PLAIN));
            empty.add(ic);
            empty.add(Box.createVerticalStrut(10));
            empty.add(l1);
            empty.add(Box.createVerticalStrut(6));
            empty.add(l2);
            dsListPanel.add(empty);
        } else {
            for (int i = 0; i < cfgs.size(); i++) {
                DsRow r = new DsRow(i, cfgs.get(i), i == editingIndex);
                dsListPanel.add(r);
                dsListPanel.add(Box.createVerticalStrut(8));
            }
        }
        dsListPanel.setPreferredSize(new Dimension(226, dsListPanel.getPreferredSize().height));
        dsListPanel.revalidate();
        dsListPanel.repaint();
    }

    private class DsRow extends JPanel {
        private boolean selected;
        private boolean hover;

        DsRow(int index, DataSourceCfg cfg, boolean sel) {
            this.selected = sel;
            setOpaque(false);
            setLayout(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
            setPreferredSize(new Dimension(0, 64));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel db = new JLabel(Icons.database(selected ? Main.BLUE : Main.TEXT_SUB));
            JLabel nm = new JLabel(cfg.name == null || cfg.name.isEmpty() ? "(未命名)" : cfg.name);
            nm.setFont(f(13, Font.BOLD));
            nm.setForeground(selected ? Main.BLUE_DARK : Main.TEXT);
            JLabel rm = new JLabel(shortUrl(cfg.jdbcUrl));
            rm.setFont(f(11, Font.PLAIN));
            rm.setForeground(Main.TEXT_SUB);

            JPanel info = new JPanel();
            info.setOpaque(false);
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.add(nm);
            info.add(Box.createVerticalStrut(2));
            info.add(rm);

            JButton del = new JButton(Icons.trash(Main.TEXT_SUB));
            del.setBorderPainted(false);
            del.setContentAreaFilled(false);
            del.setFocusPainted(false);
            del.setToolTipText("删除该数据源");
            del.addActionListener(e -> delCfg(index));

            add(db, BorderLayout.WEST);
            add(info, BorderLayout.CENTER);
            add(del, BorderLayout.EAST);

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    selectDs(index);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            };
            addMouseListener(ma);
            hook(ma, db, nm, rm, info);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                g2.setColor(Main.BLUE_LIGHT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Main.BLUE);
                g2.fillRoundRect(0, 7, 3, getHeight() - 14, 3, 3);
            } else if (hover) {
                g2.setColor(Main.BLUE_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            g2.dispose();
            super.paintComponent(g);
        }

    }

    private static String shortUrl(String u) {
        if (u == null || u.trim().isEmpty()) {
            return "未配置 JDBC URL";
        }
        String s = u.trim();
        int i = s.lastIndexOf('@');
        if (i >= 0) {
            s = s.substring(i + 1);
        }
        return s.length() > 32 ? s.substring(0, 32) + "…" : s;
    }

    private void newDs() {
        editingIndex = -1;
        fillForm(new DataSourceCfg());
        refreshSidebar();
        statusLabel.setText("新增数据源：填写信息后点击「保存配置」");
        nameField.requestFocusInWindow();
    }

    private void saveCfg() {
        DataSourceCfg cfg = readForm();
        if (cfg.name.isEmpty() || cfg.jdbcUrl.isEmpty()) {
            showToast("请填写数据源名称和 JDBC URL", false);
            return;
        }
        if (editingIndex >= 0 && editingIndex < cfgs.size()) {
            cfgs.set(editingIndex, cfg);
        } else {
            cfgs.add(cfg);
            editingIndex = cfgs.size() - 1;
        }
        ConfigStore.saveAll(cfgs);
        refreshSidebar();
        statusLabel.setText("已保存数据源：" + cfg.name + "（密码已 AES 加密本地存储）");
        showToast("已保存数据源：" + cfg.name, true);
    }

    private void delCfg(int index) {
        if (index < 0 || index >= cfgs.size()) {
            return;
        }
        String nm = cfgs.get(index).name;
        int r = JOptionPane.showConfirmDialog(this, "确定删除数据源：\n" + nm + " ？", "删除数据源",
                JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) {
            return;
        }
        cfgs.remove(index);
        if (editingIndex > index) {
            editingIndex--;
        } else if (editingIndex == index) {
            editingIndex = -1;
            fillForm(new DataSourceCfg());
        }
        ConfigStore.saveAll(cfgs);
        refreshSidebar();
        if (editingIndex < 0 && cfgs.size() > 0) {
            selectDs(0);
        }
        statusLabel.setText("已删除数据源：" + nm);
    }

    private void selectDs(int index) {
        if (index >= 0 && index < cfgs.size()) {
            editingIndex = index;
            fillForm(cfgs.get(index));
            statusLabel.setText("当前数据源：" + cfgs.get(index).name);
        } else {
            editingIndex = -1;
            fillForm(new DataSourceCfg());
            statusLabel.setText("当前：新增数据源");
        }
        refreshSidebar();
    }

    private void fillForm(DataSourceCfg cfg) {
        nameField.setText(cfg.name);
        urlField.setText(cfg.jdbcUrl);
        userField.setText(cfg.user);
        pwdField.setText(cfg.password == null ? "" : cfg.password);
        remarkField.setText(cfg.remark == null ? "" : cfg.remark);
        selectDriver(cfg.driverClass);
    }

    /** 根据驱动类名选中/填好驱动下拉框 */
    private void selectDriver(String driverClass) {
        String dc = (driverClass == null || driverClass.trim().isEmpty())
                ? DictLoader.DEFAULT_DRIVER : driverClass.trim();
        for (int i = 0; i < driverCombo.getItemCount(); i++) {
            if (driverCombo.getItemAt(i).startsWith(dc + "（") || driverCombo.getItemAt(i).equals(dc)) {
                driverCombo.setSelectedIndex(i);
                return;
            }
        }
        driverCombo.setSelectedItem(dc);
    }

    private DataSourceCfg readForm() {
        DataSourceCfg cfg = new DataSourceCfg();
        cfg.name = nameField.getText().trim();
        cfg.jdbcUrl = urlField.getText().trim();
        cfg.user = userField.getText().trim();
        char[] pw = pwdField.getPassword();
        cfg.password = pw == null ? "" : new String(pw);
        cfg.driverClass = readDriverClass();
        cfg.remark = remarkField.getText().trim();
        return cfg;
    }

    /** 从驱动下拉框解析实际驱动类名（去掉中文说明后缀） */
    private String readDriverClass() {
        Object sel = driverCombo.getSelectedItem();
        String s = sel == null ? "" : sel.toString().trim();
        if (s.isEmpty()) {
            return DictLoader.DEFAULT_DRIVER;
        }
        int cut = s.indexOf('（');
        return (cut > 0 ? s.substring(0, cut) : s).trim();
    }
    /* ====================== 测试 / 读取模块 ====================== */

    private void doTest() {
        final DataSourceCfg cfg = readForm();
        if (cfg.jdbcUrl.isEmpty()) {
            clearFieldErrors();
            setFieldError(errUrlHint, "请先填写 JDBC URL");
            setTestStatus("连接失败", "请先填写 JDBC URL", DANGER_RED);
            showToast("请先填写 JDBC URL", false);
            return;
        }
        clearFieldErrors();
        btnTest.setEnabled(false);
        btnTest.setText("连接中…");
        startTestSpinner();
        setTestStatus("连接中", "正在连接数据库，请稍候…", TEST_LOADING);
        statusLabel.setText("正在测试连接…");
        log("正在连接数据库" + urlHost(cfg.jdbcUrl) + "…");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try {
                    final String msg = new DictLoader().testConnection(cfg);
                    SwingUtilities.invokeLater(() -> finishTest(msg, null));
                    return msg;
                } catch (Throwable e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    final Throwable c = cause;
                    SwingUtilities.invokeLater(() -> finishTest(null, c));
                    throw e;
                }
            }

            @Override
            protected void done() {
                // 本系统下 done()/process() 可能不派发；实际复位在 finishTest（invokeLater）内完成
            }
        }.execute();
    }

    /** 测试连接完成复位（EDT，由 invokeLater 必然派发，不依赖 done()） */
    private void finishTest(String msg, Throwable err) {
        btnTest.setEnabled(true);
        btnTest.setText("测试连接");
        stopTestSpinner();
        if (err == null) {
            setFieldError(errUrlHint, "");
            setTestStatus("连接成功", "✅ " + formatVersion(msg), SUCCESS_GREEN);
            showToast("连接成功：" + msg, true);
            statusLabel.setText(msg);
            log("测试连接成功：" + msg);
            loadScopeAsync();
        } else {
            String shortErr = summarizeError(err);
            setTestStatus("连接失败", "❌ " + shortErr, DANGER_RED);
            localizeError(err);
            showToast("连接失败：" + shortErr, false);
            statusLabel.setText("连接失败：" + shortErr);
            log("连接失败: " + err);
        }
    }

    private void loadScopeAsync() {
        final DataSourceCfg cfg = readForm();
        if (cfg.jdbcUrl.isEmpty()) {
            return;
        }
        statusLabel.setText("正在读取模块列表…");
        log("正在读取模块列表…");
        setAllScopes(true);
        setBusy(true);
        gradientBar.beginIndeterminate();
        new SwingWorker<List<DictLoader.ScopeModule>, Void>() {
            @Override
            protected List<DictLoader.ScopeModule> doInBackground() throws Exception {
                try {
                    final List<DictLoader.ScopeModule> mods = new DictLoader().loadScope(cfg);
                    SwingUtilities.invokeLater(() -> finishLoadScope(mods, null));
                    return mods;
                } catch (Throwable e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    final Throwable c = cause;
                    SwingUtilities.invokeLater(() -> finishLoadScope(null, c));
                    throw e;
                }
            }

            @Override
            protected void done() {
                // 本系统下 done()/process() 可能不派发；实际复位在 finishLoadScope（invokeLater）内完成
            }
        }.execute();
    }

    /** 读取模块完成复位（EDT，由 invokeLater 必然派发，不依赖 done()） */
    private void finishLoadScope(List<DictLoader.ScopeModule> mods, Throwable err) {
        setBusy(false);      // 恢复 保存配置/测试连接/导出 按钮
        gradientBar.hideBar();
        if (err != null) {
            showToast("读取模块列表失败：" + err.getMessage(), false);
            statusLabel.setText("读取模块列表失败");
            log("读取模块列表失败: " + err);
            return;
        }
        scopeRows.clear();
        for (DictLoader.ScopeModule m : mods) {
            ScopeModuleRow row = new ScopeModuleRow();
            row.id = m.id;
            row.name = m.name;
            String disp = m.displayname == null || m.displayname.isEmpty() ? m.name : m.displayname;
            JCheckBox modBox = new JCheckBox("<html><b>" + esc(disp) + "</b>（" + m.comps.size() + " 组件）</html>");
            modBox.setSelected(true);
            modBox.addActionListener(e -> {
                for (JCheckBox cb : row.compBoxes) {
                    cb.setSelected(modBox.isSelected());
                }
                updateScopeSummary();
            });
            row.modBox = modBox;
            scopeListPanel.add(modBox);
            for (DictLoader.ScopeComp comp : m.comps) {
                String cdisp = comp.displayname == null || comp.displayname.isEmpty()
                        ? comp.name : comp.displayname + " [" + comp.name + "]";
                JCheckBox cb = new JCheckBox("　" + cdisp);
                cb.setSelected(true);
                cb.addActionListener(e -> updateScopeSummary());
                row.compBoxes.add(cb);
                row.compIds.add(comp.id);
                scopeListPanel.add(cb);
            }
            scopeRows.add(row);
        }
        renderScopeList("");
        statusLabel.setText("模块列表读取完成：" + mods.size() + " 个模块，默认全选（全库）");
        log("读取模块列表完成：" + mods.size() + " 个模块");
    }

    private void setAllScopes(boolean on) {
        for (ScopeModuleRow row : scopeRows) {
            row.modBox.setSelected(on);
            for (JCheckBox cb : row.compBoxes) {
                cb.setSelected(on);
            }
        }
        renderScopeList(scopeFilter.getText());
        updateScopeSummary();
    }

    private void updateScopeSummary() {
        int mods = 0, comps = 0;
        for (ScopeModuleRow row : scopeRows) {
            boolean any = false;
            for (JCheckBox cb : row.compBoxes) {
                if (cb.isSelected()) {
                    any = true;
                    comps++;
                }
            }
            if (any) {
                mods++;
            }
        }
        if (scopeRows.isEmpty()) {
            scopeSummary.setText("尚未读取模块列表（未勾选组件 = 全库导出）");
        } else {
            scopeSummary.setText("将导出 模块 " + mods + " 个 / 组件 " + comps + " 个");
        }
    }

    /** 按关键字重建范围列表，精准定位导出范围 */
    private void renderScopeList(String kw) {
        scopeListPanel.removeAll();
        visibleBoxes.clear();
        String f = kw == null ? "" : kw.trim().toLowerCase();
        int shownMods = 0, shownComps = 0;
        for (ScopeModuleRow row : scopeRows) {
            String modDisp = row.modBox.getText();
            boolean modMatch = f.isEmpty() || modDisp.toLowerCase().contains(f)
                    || (row.name != null && row.name.toLowerCase().contains(f));
            List<JCheckBox> shown = new ArrayList<JCheckBox>();
            for (JCheckBox cb : row.compBoxes) {
                String cDisp = cb.getText();
                if (f.isEmpty() || cDisp.toLowerCase().contains(f)) {
                    shown.add(cb);
                }
            }
            boolean showModule = modMatch || !shown.isEmpty();
            if (!showModule) {
                continue;
            }
            scopeListPanel.add(row.modBox);
            visibleBoxes.add(row.modBox);
            shownMods++;
            if (shown.isEmpty()) {
                for (JCheckBox cb : row.compBoxes) {
                    scopeListPanel.add(cb);
                    visibleBoxes.add(cb);
                    shownComps++;
                }
            } else {
                for (JCheckBox cb : shown) {
                    scopeListPanel.add(cb);
                    visibleBoxes.add(cb);
                    shownComps++;
                }
            }
        }
        scopeListPanel.revalidate();
        scopeListPanel.repaint();
    }

    /* ====================== 导出 ====================== */

    private void doExport() {
        final DataSourceCfg cfg = readForm();
        if (cfg.jdbcUrl.isEmpty()) {
            showToast("请先填写 JDBC URL", false);
            return;
        }
        String dir = outDirField.getText().trim();
        final File outDir;
        if (dir.isEmpty()) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("选择导出目录");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outDir = fc.getSelectedFile();
                outDirField.setText(outDir.getAbsolutePath());
            } else {
                return;
            }
        } else {
            outDir = new File(dir);
        }

        final Set<String> scope = new HashSet<String>();
        for (ScopeModuleRow row : scopeRows) {
            for (int i = 0; i < row.compBoxes.size(); i++) {
                if (row.compBoxes.get(i).isSelected()) {
                    scope.add(row.compIds.get(i));
                }
            }
        }

        setLogExpanded(true);
        log("开始导出（" + (scope.isEmpty() ? "全库" : "选中 " + scope.size() + " 个组件") + "）…");
        setBusy(true);
        final long start = System.currentTimeMillis();
        final boolean[] completed = {false};
        final javax.swing.Timer[] busyTimer = {null};
        final SwingWorker<String, Object> worker = new SwingWorker<String, Object>() {
            @Override
            protected String doInBackground() throws Exception {
                DictLoader loader = new DictLoader();
                DictLoader.Progress pr = new DictLoader.Progress() {
                    @Override
                    public void step(String m) {
                        publish(new Object[]{"step", m});
                    }

                    @Override
                    public void log(String m) {
                        publish(new Object[]{"log", m});
                    }
                };
                DataDictionary dict = loader.load(cfg, scope.isEmpty() ? null : scope, pr);
                publish(new Object[]{"step", "正在生成网页…"});
                String name = "NC数据字典_" + stamp01(dict.ncVersion) + ".html";
                File out = new File(outDir, name);
                HtmlExporter.export(dict, out);
                return out.getAbsolutePath();
            }

            @Override
            protected void process(List<Object> chunks) {
                for (Object o : chunks) {
                    Object[] a = (Object[]) o;
                    String kind = String.valueOf(a[0]);
                    String msg = String.valueOf(a[1]);
                    log(msg);
                    if ("step".equals(kind)) {
                        double pct = stepPct(msg);
                        if (pct >= 0) {
                            gradientBar.setProgress(pct);
                        }
                    }
                }
            }

            @Override
            protected void done() {
                // 复位由自检计时器完成（worker.isDone() 必然被检测到）；此处不做引用 worker 的操作
            }
        };
        busyTimer[0] = new javax.swing.Timer(1000, e -> {
            // 主路径：后台任务一结束，本计时器必然触发，在这里完成全部复位
            if (worker.isDone()) {
                completeExport(worker, completed, busyTimer, "导出成功：", true);
                return;
            }
            long sec = (System.currentTimeMillis() - start) / 1000;
            statusLabel.setText("正在导出，已处理 " + sec + " 秒，请稍候…");
        });
        busyTimer[0].start();
        gradientBar.beginDeterminate();
        worker.execute();
    }

    private void triggerExport() {
        int idx = exportTypeCombo.getSelectedIndex();
        if (idx == 2) {
            doExportDefdoc();
        } else if (idx == 1) {
            doExportSql();
        } else {
            doExport();
        }
    }

    /** SQL 脚本(INSERT)导出：输入功能节点号 -> 递归关联导出相关元数据行 */
    private void doExportSql() {
        final DataSourceCfg cfg = readForm();
        if (cfg.jdbcUrl.isEmpty()) {
            showToast("请先填写 JDBC URL", false);
            return;
        }
        final String funcode = sqlFuncodeField.getText() == null ? "" : sqlFuncodeField.getText().trim();
        if (funcode.isEmpty()) {
            showToast("请先输入功能节点号(funcode)", false);
            sqlFuncodeField.requestFocus();
            return;
        }
        String dir = outDirField.getText().trim();
        final File outDir;
        if (dir.isEmpty()) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("选择导出目录");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outDir = fc.getSelectedFile();
                outDirField.setText(outDir.getAbsolutePath());
            } else {
                return;
            }
        } else {
            outDir = new File(dir);
        }
        final File out = new File(outDir, "nc_script_export_" + stamp01("NC") + ".sql");

        setLogExpanded(true);
        log("开始导出功能节点 [" + funcode + "] 的元数据 SQL 脚本…");
        setBusy(true);
        final long start = System.currentTimeMillis();
        final boolean[] completed = {false};
        final javax.swing.Timer[] busyTimer = {null};
        final SwingWorker<String, Object> worker = new SwingWorker<String, Object>() {
            @Override
            protected String doInBackground() throws Exception {
                com.bjuc.datadict.core.SqlScriptExporter.Progress pr =
                        new com.bjuc.datadict.core.SqlScriptExporter.Progress() {
                            @Override
                            public void step(String m) {
                                publish(new Object[]{"step", m});
                            }

                            @Override
                            public void log(String m) {
                                publish(new Object[]{"log", m});
                            }
                        };
                new com.bjuc.datadict.core.SqlScriptExporter().export(cfg, funcode, out, pr);
                return out.getAbsolutePath();
            }

            @Override
            protected void process(List<Object> chunks) {
                for (Object o : chunks) {
                    Object[] a = (Object[]) o;
                    String kind = String.valueOf(a[0]);
                    String msg = String.valueOf(a[1]);
                    log(msg);
                    if ("step".equals(kind)) {
                        double pct = stepPct(msg);
                        if (pct >= 0) {
                            gradientBar.setProgress(pct);
                        }
                    }
                }
            }

            @Override
            protected void done() {
                log("[UI] SQL 导出后台任务结束，开始复位界面…");
                // 复位由自检计时器完成；此处不直接引用 worker 以避免未初始化引用
            }
        };
        busyTimer[0] = new javax.swing.Timer(1000, e -> {
            // 主路径：后台任务一结束，本计时器必然触发，在这里完成全部复位
            if (worker.isDone()) {
                completeExport(worker, completed, busyTimer, "SQL 脚本导出成功：", false);
                return;
            }
            statusLabel.setText("正在导出 SQL，已处理 " + ((System.currentTimeMillis() - start) / 1000) + " 秒，请稍候…");
        });
        busyTimer[0].start();
        gradientBar.beginDeterminate();
        worker.execute();
    }

    /** 自定义档案 SQL(INSERT)导出：输入档案分类 PK -> 导出其 7 张关联表行 */
    private void doExportDefdoc() {
        final DataSourceCfg cfg = readForm();
        if (cfg.jdbcUrl.isEmpty()) {
            showToast("请先填写 JDBC URL", false);
            return;
        }
        final String pk = defdocPkField.getText() == null ? "" : defdocPkField.getText().trim();
        if (pk.isEmpty()) {
            showToast("请先输入档案分类 PK(pk_defdoclist)", false);
            defdocPkField.requestFocus();
            return;
        }
        String dir = outDirField.getText().trim();
        final File outDir;
        if (dir.isEmpty()) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("选择导出目录");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outDir = fc.getSelectedFile();
                outDirField.setText(outDir.getAbsolutePath());
            } else {
                return;
            }
        } else {
            outDir = new File(dir);
        }
        final File out = new File(outDir, "nc_arch_export_" + stamp01("NC") + ".sql");

        setLogExpanded(true);
        log("开始导出自定义档案 [PK=" + pk + "] 的元数据 SQL 脚本…");
        setBusy(true);
        final long start = System.currentTimeMillis();
        final boolean[] completed = {false};
        final javax.swing.Timer[] busyTimer = {null};
        final SwingWorker<String, Object> worker = new SwingWorker<String, Object>() {
            @Override
            protected String doInBackground() throws Exception {
                com.bjuc.datadict.core.SqlScriptExporter.Progress pr =
                        new com.bjuc.datadict.core.SqlScriptExporter.Progress() {
                            @Override
                            public void step(String m) {
                                publish(new Object[]{"step", m});
                            }

                            @Override
                            public void log(String m) {
                                publish(new Object[]{"log", m});
                            }
                        };
                new com.bjuc.datadict.core.SqlScriptExporter().exportDefdoc(cfg, pk, out, pr);
                return out.getAbsolutePath();
            }

            @Override
            protected void process(List<Object> chunks) {
                for (Object o : chunks) {
                    Object[] a = (Object[]) o;
                    String kind = String.valueOf(a[0]);
                    String msg = String.valueOf(a[1]);
                    log(msg);
                    if ("step".equals(kind)) {
                        double pct = stepPct(msg);
                        if (pct >= 0) {
                            gradientBar.setProgress(pct);
                        }
                    }
                }
            }

            @Override
            protected void done() {
                log("[UI] 自定义档案导出后台任务结束，开始复位界面…");
                // 复位由自检计时器完成；此处不直接引用 worker 以避免未初始化引用
            }
        };
        busyTimer[0] = new javax.swing.Timer(1000, e -> {
            // 主路径：后台任务一结束，本计时器必然触发，在这里完成全部复位
            if (worker.isDone()) {
                completeExport(worker, completed, busyTimer, "自定义档案导出成功：", false);
                return;
            }
            statusLabel.setText("正在导出自定义档案 SQL，已处理 " + ((System.currentTimeMillis() - start) / 1000) + " 秒，请稍候…");
        });
        busyTimer[0].start();
        gradientBar.beginDeterminate();
        worker.execute();
    }

    /**
     * 统一导出完成复位：由「自检计时器」与 done() 冗余触发，completed 保证幂等。
     * 关键：本系统下 SwingWorker.done()/process() 可能不被派发，因此所有复位
     * （恢复按钮、完成进度条、写状态/日志）都必须在必然触发的计时器路径里完成。
     */
    private void completeExport(SwingWorker<String, Object> worker, boolean[] completed,
                                javax.swing.Timer[] timer, String successStatus, boolean openHtml) {
        if (completed[0]) {
            return;
        }
        completed[0] = true;
        if (timer != null && timer[0] != null) {
            timer[0].stop();
        }
        setBusy(false);
        try {
            String path = worker.get(); // isDone 已为 true，不阻塞
            gradientBar.finish();
            statusLabel.setText(successStatus + path);
            log(successStatus + "！文件位于：" + path);
            showToast("导出成功", true);
            if (openHtml && openChk.isSelected()) {
                try {
                    Desktop.getDesktop().open(new File(path));
                    return;
                } catch (Throwable ignore) {
                }
            }
            try {
                Runtime.getRuntime().exec("explorer /select,\"" + path + "\"");
            } catch (Throwable ignore) {
            }
        } catch (Throwable e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            statusLabel.setText("导出失败：" + cause.getMessage());
            log("导出失败: " + cause);
            showToast("导出失败：" + cause.getMessage(), false);
            JOptionPane.showMessageDialog(MainFrame.this, "导出失败：\n" + cause.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            gradientBar.hideBar();
        }
    }

    private void setBusy(boolean b) {
        btnSave.setEnabled(!b);
        btnExport.setEnabled(!b);
        if (btnTest != null) {
            btnTest.setEnabled(!b);
        }
        if (btnLoadScope != null) {
            btnLoadScope.setEnabled(!b);
        }
    }

    /**
     * 从 DictLoader.step 消息中解析 "N/M" 得到 0-100 的百分比，用于驱动确定进度条。
     * 解析失败或消息不是 "x/N" 形式时返回 -1（表示无需更新）。
     */
    private double stepPct(String msg) {
        if (msg == null) {
            return -1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)/(\\d+)").matcher(msg);
        if (m.find()) {
            try {
                int cur = Integer.parseInt(m.group(1));
                int tot = Integer.parseInt(m.group(2));
                if (tot > 0) {
                    double v = cur * 100.0 / tot;
                    return v < 0 ? 0 : (v > 100 ? 100 : v);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        // 收尾阶段（如"正在生成网页…"）给到接近完成
        if (msg.contains("正在生成网页") || msg.contains("生成网页")) {
            return 100;
        }
        return -1;
    }

    private void chooseDir() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outDirField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    /* ====================== 日志 ====================== */

    private void log(String s) {
        String t = new SimpleDateFormat("HH:mm:ss").format(new Date());
        logArea.append("[" + t + "] " + s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void setLogExpanded(boolean on) {
        logVisible = on;
        logScroll.setVisible(on);
        logToggleIcon.setIcon(on ? Icons.chevronDown(Main.TEXT_SUB) : Icons.chevronRight(Main.TEXT_SUB));
        JPanel parent = (JPanel) logScroll.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    /* ====================== Toast ====================== */

    private void showToast(String text, boolean ok) {
        final JWindow w = new JWindow(this);
        final Color bg = ok ? Main.OK : Main.ERR;
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.dispose();
            }
        };
        p.setOpaque(false);
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(Color.WHITE);
        l.setFont(f(13, Font.PLAIN));
        p.setBorder(BorderFactory.createEmptyBorder(9, 15, 9, 15));
        p.add(l, BorderLayout.CENTER);
        w.setContentPane(p);
        w.setBackground(new Color(0, 0, 0, 0));
        Dimension sz = l.getPreferredSize();
        int screenW = Toolkit.getDefaultToolkit().getScreenSize().width;
        int tw = Math.min(sz.width + 44, Math.max(280, screenW - 80));
        if (sz.width + 44 > tw) {
            l.setPreferredSize(new Dimension(tw - 64, sz.height));
        }
        w.setSize(tw, Math.max(sz.height + 20, 36));
        try {
            Point loc = getLocationOnScreen();
            int x = loc.x + (getWidth() - tw) / 2;
            if (x < 0) {
                x = 0;
            }
            w.setLocation(Math.min(x, screenW - tw), loc.y + getHeight() - 130);
        } catch (Throwable ignore) {
        }
        w.setAlwaysOnTop(true);
        w.setVisible(true);
        Timer timer = new Timer(3200, e -> w.dispose());
        timer.setRepeats(false);
        timer.start();
    }

    /* ====================== 工具 ====================== */

    private void bindActions() {
        btnNew.addActionListener(e -> newDs());
        btnSave.addActionListener(e -> saveCfg());
        btnExport.addActionListener(e -> triggerExport());
        btnTest.addActionListener(e -> doTest());
        btnLoadScope.addActionListener(e -> loadScopeAsync());
    }

    private void togglePwd() {
        pwdVisible = !pwdVisible;
        pwdField.setEchoChar(pwdVisible ? (char) 0 : '\u2022');
        btnEye.setIcon(pwdVisible ? Icons.eyeOff(Main.TEXT_SUB) : Icons.eye(Main.TEXT_SUB));
    }

    private void hook(MouseAdapter a, Component... cs) {
        for (Component c : cs) {
            c.addMouseListener(a);
        }
    }

    private static Font f(float size, int style) {
        return UIManager.getFont("Label.font").deriveFont(style, size);
    }

    /** 给某个颜色加透明度（用于顶栏等彩色底上的半透明文字/描边） */
    private static Color tint(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private JButton flatBtn(String t) {
        JButton b = new JButton(t);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setForeground(Main.BLUE_DARK);
        b.setFont(f(12, Font.PLAIN));
        return b;
    }

    private JButton outlineBtn(String t, javax.swing.Icon ic) {
        JButton b = ic == null ? new JButton(t) : new JButton(t, ic);
        b.setBackground(Main.SURFACE);
        b.setForeground(Main.TEXT);
        b.setFocusPainted(false);
        b.setFont(f(13, Font.PLAIN));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Main.LINE),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        return b;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String urlHost(String url) {
        String s = url == null ? "" : url.trim();
        int i = s.lastIndexOf('@');
        if (i >= 0) {
            s = s.substring(i + 1);
        }
        return s.isEmpty() ? "" : "（" + s + "）";
    }

    private static String stamp01(String v) {
        StringBuilder sb = new StringBuilder();
        for (char ch : (v == null ? "" : v).toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '.') {
                sb.append(ch);
            }
        }
        String t = sb.toString();
        return (t.isEmpty() ? "NC65" : t) + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    static class ScopeModuleRow {
        String id;
        String name;
        JCheckBox modBox;
        List<JCheckBox> compBoxes = new ArrayList<JCheckBox>();
        List<String> compIds = new ArrayList<String>();
    }

    /* ====================== 卡片 ====================== */

    static class Card extends JPanel {
        final JPanel body = new JPanel(new GridBagLayout());

        Card() {
            setOpaque(false);
            setLayout(new BorderLayout(0, 10));
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
            body.setBackground(Main.SURFACE);
            add(body, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Color shadow = Main.TEXT;
            g2.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), 16));
            g2.fillRoundRect(0, 4, w - 3, h - 6, 14, 14);
            g2.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), 11));
            g2.fillRoundRect(0, 2, w - 2, h - 4, 14, 14);
            g2.setColor(Main.SURFACE);
            g2.fillRoundRect(0, 0, w - 3, h - 4, 14, 14);
            g2.setColor(Main.LINE);
            g2.drawRoundRect(0, 0, w - 4, h - 4, 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * 渐变色进度条：确定模式（0-100%）与不确定模式（流动滑块）共用。
     * 仅允许在 EDT 调用其方法。同一时刻只应有一个流程在使用它。
     */
    static class GradientBar extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final Color C1 = new Color(0x1E6FFF); // 蓝
        private static final Color C2 = new Color(0x0EA5A4); // 青
        private static final Color C3 = new Color(0x7C3AED); // 紫
        private static final Color TRACK = new Color(0xE8EDF5);
        private boolean indeterminate = false;
        private double progress = 0.0;      // 0..1
        private float flow = 0f;             // 不确定模式滑块相位
        private javax.swing.Timer flowTimer;
        private boolean visible2 = false;
        private int epoch = 0;

        GradientBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(1, 22));
            setMinimumSize(new Dimension(1, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 4 || h <= 4) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int rh = h - 4;
            int y = 2;
            int r = Math.max(rh / 2, 2);
            // 轨道
            g2.setColor(TRACK);
            g2.fillRoundRect(1, y, w - 2, rh, r, r);
            g2.clip(new java.awt.geom.RoundRectangle2D.Float(1, y, w - 2, rh, r, r));
            if (indeterminate) {
                // 流动滑块：一段较宽的渐变块从左到右循环
                int segW = Math.max(w / 3, 60);
                float x = ((flow / 2f) % 1f) * (w + segW) - segW;
                java.awt.GradientPaint gp = new java.awt.GradientPaint(x, 0, C1, x + segW, 0, C3);
                g2.setPaint(gp);
                g2.fillRoundRect((int) x, y, segW, rh, r, r);
            } else {
                int fw = (int) (progress * w);
                if (fw > 0) {
                    java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, C1, w, 0, C2);
                    g2.setPaint(gp);
                    g2.fillRoundRect(1, y, fw, rh, r, r);
                }
            }
            g2.dispose();
        }

        /** 纯推进确定模式进度（0..100），不改变可见性/模式 */
        void setProgress(double pct) {
            if (pct < 0) {
                pct = 0;
            }
            if (pct > 100) {
                pct = 100;
            }
            indeterminate = false;
            progress = pct / 100.0;
            if (visible2) {
                repaint();
            }
        }

        /** 开始一个新的确定模式流程（显示 + 归零；递增代际使在途的完成隐藏失效） */
        void beginDeterminate() {
            epoch++;
            if (flowTimer != null) {
                flowTimer.stop();
            }
            indeterminate = false;
            progress = 0.0;
            ensureVisible();
            repaint();
        }

        /** 切换为不确定模式（流动）并显示；开始新流程 */
        void beginIndeterminate() {
            epoch++;
            scheduleMode(true);
            ensureVisible();
        }

        /** 切换为确定模式并显示，从当前值开始（usually 0） */
        private void scheduleMode(boolean ind) {
            indeterminate = ind;
            if (!ind && flowTimer != null) {
                flowTimer.stop();
            }
            if (ind && flowTimer == null) {
                flowTimer = new javax.swing.Timer(30, e -> {
                    flow += 0.06f;
                    if (visible2) {
                        repaint();
                    }
                });
                flowTimer.start();
            }
        }

        /** 完成：填满进度条，短暂停留后自动隐藏（带代际校验，避免误隐藏新一轮流程） */
        void finish() {
            indeterminate = false;
            progress = 1.0;
            if (flowTimer != null) {
                flowTimer.stop();
            }
            ensureVisible();
            repaint();
            final int e = epoch;
            javax.swing.Timer t = new javax.swing.Timer(1200, ev -> {
                if (e == epoch) {
                    hideBar();
                }
            });
            t.setRepeats(false);
            t.start();
        }

        void hideBar() {
            if (flowTimer != null) {
                flowTimer.stop();
            }
            epoch++;
            indeterminate = false;
            progress = 0.0;
            visible2 = false;
            setVisible(false);
            revalidate();
            repaint();
        }

        private void ensureVisible() {
            visible2 = true;
            if (!isVisible()) {
                setVisible(true);
            }
            revalidate();
            repaint();
        }
    }
}