# ============================================================
# 打包脚本：NC 数据字典导出工具
# 产物：dist/（绿色版 jar+驱动+启动器）  dist-exe/（jpackage 原生 exe）
# ============================================================
$ErrorActionPreference = 'Continue'
$root  = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$JDK   = 'D:\BIP\BIPV5\yds\devkit\jdk21.0.7-win_x64\bin'
$JAVAC = Join-Path $JDK 'javac.exe'
$JAR   = Join-Path $JDK 'jar.exe'
$JPKG  = Join-Path $JDK 'jpackage.exe'
$CP    = 'lib\fastjson-1.2.83.jar;lib\flatlaf-3.5.4.jar'

function Write-File([string]$path, [string[]]$lines) {
    $full = Join-Path $root $path
    $dir = Split-Path -Parent $full
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($full, ($lines -join "`r`n") + "`r`n", (New-Object System.Text.UTF8Encoding($false)))
}

# ---------- 1. 编译主程序 ----------
Write-Host '[1/5] 编译主程序...'
if (Test-Path out\mainclasses) { Remove-Item out\mainclasses -Recurse -Force }
New-Item -ItemType Directory -Force -Path out\mainclasses | Out-Null
$src = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
$out1 = & $JAVAC -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:none -cp $CP -d out\mainclasses $src 2>&1
if ($LASTEXITCODE -ne 0) { $out1 | Select-Object -First 60; Write-Host '编译失败'; exit 1 }
Write-Host '  主程序编译通过'

# ---------- 2. 组装 fat jar ----------
Write-Host '[2/5] 组装可运行 jar...'
if (Test-Path out\fat) { Remove-Item out\fat -Recurse -Force }
New-Item -ItemType Directory -Force -Path out\fat | Out-Null
Copy-Item out\mainclasses\* out\fat -Recurse -Force
Copy-Item resources\template.html out\fat\ -Force
Copy-Item resources\app.png out\fat\ -Force
Push-Location out\fat
& $JAR xf ..\..\lib\fastjson-1.2.83.jar
& $JAR xf ..\..\lib\flatlaf-3.5.4.jar
Pop-Location
if (Test-Path dist) { Remove-Item dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path dist | Out-Null
& $JAR --create --file dist\NCDataDictExporter.jar --main-class com.bjuc.datadict.Main -C out\fat .
if ($LASTEXITCODE -ne 0) { Write-Host 'jar 打包失败'; exit 1 }

# ---------- 3. 复制驱动与启动器到 dist ----------
Write-Host '[3/5] 复制驱动与启动器...'
New-Item -ItemType Directory -Force -Path dist\drivers | Out-Null
Copy-Item drivers\*.jar dist\drivers\ -Force

$runBat = @"
@echo off
chcp 65001 >nul
cd /d "%~dp0"
set JP=java
if defined JAVA_HOME set "JP=%JAVA_HOME%\bin\java.exe"
"%JP%" -Dfile.encoding=UTF-8 -Duser.dir="%~dp0" -jar "NCDataDictExporter.jar"
if errorlevel 1 pause
"@
Write-File 'dist\运行工具(日志).bat' ($runBat -split "`r?`n")

$runNoCon = @"
@echo off
chcp 65001 >nul
cd /d "%~dp0"
set JPN=javaw
if defined JAVA_HOME set "JPN=%JAVA_HOME%\bin\javaw.exe"
start "" "%JPN%" -Dfile.encoding=UTF-8 -Duser.dir="%~dp0" -jar "NCDataDictExporter.jar"
"@
Write-File 'dist\双击启动.bat' ($runNoCon -split "`r?`n")

# ---------- 4. jpackage 原生 exe ----------
Write-Host '[4/5] jpackage 打包原生 exe（内置 JDK21 运行环境）...'
if (Test-Path dist-exe) { Remove-Item dist-exe -Recurse -Force }
$exeOut = & $JPKG --type app-image --name "NC数据字典导出工具" `
    --input dist --main-jar NCDataDictExporter.jar `
    --main-class com.bjuc.datadict.Main `
    --icon resources\app.ico `
    --dest dist-exe --java-options "-Dfile.encoding=UTF-8" 2>&1
$appImage = $null
if ($LASTEXITCODE -eq 0) {
    $appImage = Get-ChildItem dist-exe -Directory | Select-Object -First 1
} else {
    Write-Host 'jpackage 失败，跳过 exe 打包（绿色版仍可用）'
    $exeOut | Select-Object -First 30
}
if ($appImage) {
    New-Item -ItemType Directory -Force -Path (Join-Path $appImage.FullName 'drivers') | Out-Null
    Copy-Item drivers\*.jar (Join-Path $appImage.FullName 'drivers\') -Force
    $exeBat = @"
@echo off
chcp 65001 >nul
cd /d "%~dp0"
start "" "%~dp0NC数据字典导出工具.exe"
"@
    Write-File (Join-Path $appImage.FullName.Substring($root.Length).TrimStart('\') '双击启动.bat') ($exeBat -split "`r?`n")
}

# ---------- 5. 使用说明 ----------
Write-Host '[5/5] 生成使用说明...'
$readme = @"
NC 数据字典导出工具（NC6.5 / Oracle）
================================================

【是什么】
连 NC 6.5 的 Oracle 元数据库，导出成「离线」数据字典网页（单个 HTML 文件），
可以发给任何人用浏览器直接打开查看，无需安装软件、无需联网。

【运行方式】
方式一（绿色版，dist/ 目录）：
  1) 机器上需安装 Java 8 及以上（JDK/JRE 均可）；
  2) 双击 dist/双击启动.bat 即可（想在控制台看日志用 运行工具(日志).bat）。
方式二（原生 exe，dist-exe/ 目录）：
  1) 进入 dist-exe/NC数据字典导出工具/ ，双击 NC数据字典导出工具.exe；
  2) 已内置运行环境，无需安装 Java，可直接拷到其他电脑使用。

【使用步骤】
  1. 填写数据源：名称、JDBC URL（Oracle 连接串，可只填 host:port/service，自动补全）、用户、密码，点「新增数据源」后「保存配置」；
     JDBC URL 示例：
       老 SID 写法  jdbc:oracle:thin:@10.1.1.10:1521:NC65
       服务名写法  jdbc:oracle:thin:@//10.1.1.10:1521/NC65
  2. 「测试连接」通过后会自动拉取模块列表；
  3. 可按模块/组件勾选要导出的范围（不勾选即全库导出）；
  4. 选择输出目录，点「开始导出」，完成后自动打开生成的网页。
  导出文件默认命名：NC数据字典_<版本>_<时间>.html

【SQL 脚本导出（INSERT）】
  1. 顶部导出类型选「SQL 脚本(INSERT)」；
  2. 输入要导出的「功能节点号」（如 HHHD12），程序会以该节点为起点，
     递归收集其功能节点子树下所有相关配置表（功能、参数、按钮、菜单、
     单据类型、模板、查询/打印模板、业务类、编码规则等）的关联行，
     并生成对应的 INSERT 语句（单文件 .sql）；
  3. 只导出与该节点有关的行，不是全库导出。输入输出目录后点「开始导出」。

【自定义档案导出（INSERT）】
  1. 顶部导出类型选「自定义档案(INSERT)」；
  2. 输入要导出的「档案分类 PK」（bd_mode_all.mdclassid / bd_defdoclist.pk_defdoclist，
     如 1001A3100000003J236Q），程序按该主键导出其 7 张关联表
     （档案分类、档案项、模式×2、唯一性规则及其明细、引用信息）的 INSERT 语句（单文件 .sql）；
  3. 只导出与该档案分类有关的行，不是全库导出。输入输出目录后点「开始导出」。

【多个数据源 / 记住密码】
  可保存多个数据源配置；密码用 AES 加密后保存在
  %APPDATA%\BJUC-DataDict\config.json（本机密钥，不随文件外泄）。

【Oracle 老库（8i/9i）说明】
  驱动自动适配为 oracle.jdbc.OracleDriver，无需手动选择。
  若连的是 8i/9i 老库，程序会自动回退使用旧驱动 oracle.jdbc.driver.OracleDriver。
  并把对应的老 JDBC 驱动包（如 classes12.jar、ojdbc14.jar）放入程序的 drivers/ 目录后重启程序即可。

【驱动目录】
  drivers/ 目录下的所有 *.jar 会被自动扫描注册为 JDBC 驱动，新增驱动无需改代码。

【日报留痕】
  网页页眉/页脚备注取自导出配置，可自行填写。
"@
$readmeGreen = $readme + @"

------------------------------------
绿色版目录说明：
  NCDataDictExporter.jar   主程序（内含模板与依赖）
  drivers/                 JDBC 驱动（ojdbc6/8/11，可自行增减）
  双击启动.bat             无控制台启动
  运行工具(日志).bat       带控制台启动，便于看日志
"@
Write-File 'dist\使用说明.txt' ($readmeGreen -split "`r?`n")
if ($appImage) {
    $readmeExe = $readme + @"

------------------------------------
exe 版目录说明：
  NC数据字典导出工具.exe   主程序（已内置 JDK21 运行环境）
  drivers/                 JDBC 驱动（可自行增减）
  双击启动.bat             启动 exe
"@
    Write-File (Join-Path $appImage.FullName.Substring($root.Length).TrimStart('\') '使用说明.txt') ($readmeExe -split "`r?`n")
}

Write-Host ''
Write-Host '打包完成。'
Get-ChildItem dist | Select-Object Name,Length
