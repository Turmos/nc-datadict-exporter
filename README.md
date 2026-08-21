# NC 数据字典导出工具

独立 Java 桌面应用：连接 NC6.5 的 Oracle 元数据库，导出为离线数据字典网页（单 HTML 文件），可发给任何人用浏览器直接查看；也可按功能节点号 / 自定义档案 PK 递归导出 SQL(INSERT) 脚本，支持 10 套程序界面主题。开源、MIT 许可。

## 产物
| 目录 | 说明 |
| --- | --- |
| `src/` | 主程序源码（纯 Swing + FlatLaf，JDK8+ 兼容） |
| `demo/` | 演示数据导出（离线生成示例数据字典网页，无需连库） |
| `resources/template.html` | 数据字典网页模板（城建绿风格） |
| `drivers/` | JDBC 驱动（ojdbc6/8/11，可自行增减，运行时自动扫描加载） |
| `lib/` | fastjson、flatlaf 依赖 |
| `dist/` | 绿色版分发：可运行 jar + 驱动 + 启动批处理（需本机 Java 8+） |
| `dist-exe/` | 原生 exe 版（jpackage 内置 JDK21 运行环境，无需装 Java） |

## 快速使用
双击 `dist/双击启动.bat`（或 `dist-exe/NC数据字典导出工具/NC数据字典导出工具.exe`）。
填写 JDBC URL / 用户名 / 密码 → 保存配置 → 测试连接（自动拉取模块列表）→
勾选要导出的模块/组件（不勾选=全库）→ 选择输出目录 → 开始导出，完成后自动打开网页。

JDBC URL 示例：
```
jdbc:oracle:thin:@10.1.1.10:1521:NC65        （SID）
jdbc:oracle:thin:@//10.1.1.10:1521/NC65      （服务名）
```

Oracle 8i/9i 老库：驱动下拉选「oracle.jdbc.driver.OracleDriver（老驱动 8i/9i）」，
并把对应老驱动包（classes12.jar / ojdbc14.jar）放入 `drivers/` 后重启程序。

## 构建 / 打包
- 编译 + 跑演示：`powershell -ExecutionPolicy Bypass -File build.ps1`（产物在 `demo_out/`）
- 完整打包（fat jar + 原生 exe）：`powershell -ExecutionPolicy Bypass -File package.ps1`
- 需要 JDK 8+ 编译运行；打包 exe 需要 JDK 14+（本机用 JDK 21）。

## 说明
- 多数据源配置、密码 AES 加密保存在 `%APPDATA%\BJUC-DataDict\config.json`。
- 导出网页为单文件
- 核心查询封装在 `core/DictLoader.java`（全库 / 按组件集导出、测试连接、模块列表）。