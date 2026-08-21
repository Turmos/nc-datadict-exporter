$JDK = 'D:\BIP\BIPV5\yds\devkit\jdk21.0.7-win_x64\bin'
$JAVAC = Join-Path $JDK 'javac.exe'
$JAVA  = Join-Path $JDK 'java.exe'
$CP = 'lib\fastjson-1.2.83.jar;lib\flatlaf-3.5.4.jar'
New-Item -ItemType Directory -Force -Path out\mainclasses | Out-Null
$src = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
$out1 = & $JAVAC -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:none -cp $CP -d out\mainclasses $src 2>&1
$c1 = $LASTEXITCODE
$out1 | Select-Object -First 60
Write-Output ("MAIN_COMPILE_EXIT=" + $c1)
if ($c1 -ne 0) { exit 1 }
Copy-Item resources\template.html out\mainclasses\template.html -Force
New-Item -ItemType Directory -Force -Path out\democlasses | Out-Null
$dsrc = Get-ChildItem demo\src -Recurse -Filter *.java | ForEach-Object FullName
$out2 = & $JAVAC -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:none -cp "out\mainclasses;$CP" -d out\democlasses $dsrc 2>&1
$c2 = $LASTEXITCODE
$out2 | Select-Object -First 30
Write-Output ("DEMO_COMPILE_EXIT=" + $c2)
if ($c2 -ne 0) { exit 1 }
$out3 = & $JAVA -cp "out\democlasses;out\mainclasses;$CP" com.bjuc.datadict.demo.DemoMain 2>&1
$c3 = $LASTEXITCODE
$out3
Write-Output ("DEMO_RUN_EXIT=" + $c3)