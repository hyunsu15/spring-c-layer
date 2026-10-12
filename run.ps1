# repo-facade build & test script (works without Maven/Gradle).
# Downloads dependency jars into lib/ on first run, compiles main + test with javac, runs the JUnit scenarios.
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# 1. Locate a JDK (JAVA_HOME first, then IntelliJ-managed JDKs)
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$jdksDir = Join-Path $env:USERPROFILE ".jdks"
if (Test-Path $jdksDir) {
    $candidates += Get-ChildItem $jdksDir -Directory | Sort-Object Name -Descending | ForEach-Object { $_.FullName }
}
$jdk = $candidates | Where-Object { Test-Path (Join-Path $_ "bin\javac.exe") } | Select-Object -First 1
if (-not $jdk) { Write-Error "No JDK found. Set JAVA_HOME."; exit 1 }
Write-Host "JDK: $jdk"

# 2. Download dependencies (only when missing)
$deps = @(
    "org/springframework/spring-core/6.2.6",
    "org/springframework/spring-jcl/6.2.6",
    "org/springframework/spring-beans/6.2.6",
    "org/springframework/spring-context/6.2.6",
    "org/springframework/spring-aop/6.2.6",
    "org/springframework/spring-expression/6.2.6",
    "org/springframework/spring-tx/6.2.6",
    "org/springframework/spring-jdbc/6.2.6",
    "org/mybatis/mybatis/3.5.16",
    "org/mybatis/mybatis-spring/3.0.4",
    "com/h2database/h2/2.3.232",
    "org/slf4j/slf4j-api/2.0.16",
    "org/slf4j/slf4j-simple/2.0.16",
    "org/junit/platform/junit-platform-console-standalone/6.0.3"
)
New-Item -ItemType Directory -Force lib | Out-Null
foreach ($dep in $deps) {
    $parts = $dep -split "/"
    $artifact = $parts[-2]
    $version = $parts[-1]
    $jar = "lib\$artifact-$version.jar"
    if (-not (Test-Path $jar)) {
        $url = "https://repo1.maven.org/maven2/$dep/$artifact-$version.jar"
        Write-Host "Downloading $artifact-$version.jar"
        Invoke-WebRequest -Uri $url -OutFile $jar -UseBasicParsing
    }
}

# 3. Compile main, then test (relative ASCII paths only, so the Korean parent path never enters the argfile)
if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory -Force out\main, out\test | Out-Null
$javac = Join-Path $jdk "bin\javac.exe"

Get-ChildItem -Recurse "src\main\java" -Filter *.java | ForEach-Object { Resolve-Path -Relative $_.FullName } |
    Set-Content -Encoding ascii "out\main-sources.txt"
& $javac --release 17 -encoding UTF-8 -cp "lib\*" -d out\main "@out\main-sources.txt"
if ($LASTEXITCODE -ne 0) { Write-Error "Main compilation failed"; exit 1 }

Get-ChildItem -Recurse "src\test\java" -Filter *.java | ForEach-Object { Resolve-Path -Relative $_.FullName } |
    Set-Content -Encoding ascii "out\test-sources.txt"
& $javac --release 17 -encoding UTF-8 -cp "out\main;lib\*" -d out\test "@out\test-sources.txt"
if ($LASTEXITCODE -ne 0) { Write-Error "Test compilation failed"; exit 1 }

# 4. Copy test resources onto the test classpath
Copy-Item "src\test\resources\*" out\test -Recurse -Force

# 5. Run the JUnit scenarios (exit code 0 when all pass)
& (Join-Path $jdk "bin\java.exe") "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" `
    -cp "out\main;out\test;lib\*" org.junit.platform.console.ConsoleLauncher execute `
    --disable-banner --details=tree --scan-classpath out\test
exit $LASTEXITCODE
