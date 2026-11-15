# ============================================================================
#  AI Rhythm Guardian - build helper
#  Called by start.bat. Builds classpath and javac argument file.
#
#  Two compiler flags are REQUIRED:
#    --release 17   produce Java 17 bytecode (Spring Boot 3.2 ASM cannot read 21+)
#    -parameters    keep method parameter names (else @RequestParam APIs return 500)
# ============================================================================

param(
    [Parameter(Mandatory = $true)][string]$M2Repo,
    [Parameter(Mandatory = $true)][string]$SrcDir,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [Parameter(Mandatory = $true)][string]$ResDir,
    [Parameter(Mandatory = $true)][string]$LombokJar,
    [Parameter(Mandatory = $true)][string]$TempDir
)

$ErrorActionPreference = 'Stop'

# ---------- 1. collect dependency jars ----------
#
# Prefer the classpath that Maven already resolved into target\cp.txt.
#
# Why: scanning .m2 for every jar pulls in unrelated artifacts. Specifically
# commons-logging.jar lands on the classpath and Spring Boot's Logback setup
# then aborts at startup with Assert.isInstanceOf failure. Maven's own list
# contains only the declared dependencies and their transitive closure.
$cpFile = Join-Path (Split-Path $OutDir -Parent) 'cp.txt'
$cp = $null

if (Test-Path $cpFile) {
    $raw = [System.IO.File]::ReadAllText($cpFile).Trim()
    if ($raw.Length -gt 0) {
        $cp = $raw
        $n = ($raw -split ';').Count
        Write-Host "      classpath: Maven resolved ($n entries)"
    }
}

if (-not $cp) {
    # Fallback: scan the local repository, but skip the jars that are known
    # to collide with Spring Boot's logging stack.
    $jars = Get-ChildItem $M2Repo -Recurse -File -Filter *.jar -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -notmatch 'sources|javadoc' -and
            $_.Name -notmatch '^commons-logging' -and
            $_.Name -notmatch '^log4j-to-slf4j' -and
            $_.Name -notmatch '^slf4j-log4j12' -and
            $_.Name -notmatch '^logback-classic.*test'
        }

    if ($jars.Count -eq 0) {
        Write-Host "      [ERROR] no jar found in: $M2Repo"
        exit 1
    }

    $cp = ($jars | ForEach-Object { $_.FullName }) -join ';'
    Write-Host "      classpath: scanned .m2 ($($jars.Count) jars)"
}

# ---------- 2. collect sources ----------
$srcs = Get-ChildItem $SrcDir -Recurse -File -Filter *.java -ErrorAction SilentlyContinue |
    ForEach-Object { $_.FullName }

if ($srcs.Count -eq 0) {
    Write-Host "      [ERROR] no source found in: $SrcDir"
    exit 1
}

Write-Host ("      sources: " + $srcs.Count)

# ---------- 2b. copy resources into target\classes ----------
#
# The target\classes directory sits first on the runtime classpath. If a stale
# application.yml is left there it shadows the edited copy under
# src\main\resources, so config changes silently do not take effect.
if (Test-Path $ResDir) {
    if (-not (Test-Path $OutDir)) {
        New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    }
    $items = Get-ChildItem -Path $ResDir -Force
    foreach ($item in $items) {
        Copy-Item -Path $item.FullName -Destination $OutDir -Recurse -Force
    }
    $rc = (Get-ChildItem $ResDir -Recurse -File).Count
    Write-Host "      resources synced: $rc file(s)"
}

# ---------- 3. write javac argument file ----------
# Paths use forward slashes to avoid backslash being treated as escape char.
function To-Slash([string]$p) { return ($p -replace '\\', '/') }

$lines = New-Object System.Collections.Generic.List[string]
foreach ($a in @('-encoding', 'UTF-8', '-nowarn', '--release', '17', '-parameters')) {
    $lines.Add($a)
}
$lines.Add('-cp')
$lines.Add('"' + (To-Slash $cp) + '"')
$lines.Add('-processorpath')
$lines.Add('"' + (To-Slash $LombokJar) + '"')
$lines.Add('-d')
$lines.Add('"' + (To-Slash $OutDir) + '"')
foreach ($s in $srcs) {
    $lines.Add('"' + (To-Slash $s) + '"')
}

$javacFile = Join-Path $TempDir 'guardian_javac.txt'
[System.IO.File]::WriteAllLines($javacFile, $lines, (New-Object System.Text.UTF8Encoding($false)))

# ---------- 4. write runtime classpath ----------
$runCp = $OutDir + ';' + $ResDir + ';' + $cp
$cpFile = Join-Path $TempDir 'guardian_cp.txt'
[System.IO.File]::WriteAllText($cpFile, $runCp, [System.Text.Encoding]::UTF8)

# ---------- 5. write JVM argfile ----------
# The runtime classpath is ~14 KB. Reading it with `set /p` in a batch file
# truncates around 8 KB and mangles special characters, which surfaces as
# ClassNotFoundException. So we hand the JVM an @argfile instead.
#
# JVM argfile rules:
#   - one argument per line
#   - an argument containing spaces must be wrapped in double quotes
#   - backslashes act as escape characters, so paths use forward slashes
#
# IMPORTANT: the JVM reads an @argfile using the platform default encoding,
# NOT UTF-8. Writing UTF-8 here corrupts any non-ASCII character in the path
# (this project lives under a Chinese-named folder) and the classpath silently
# becomes invalid. Write it as ANSI / Default.
$runLines = New-Object System.Collections.Generic.List[string]
$runLines.Add('-cp')
$runLines.Add('"' + (To-Slash $runCp) + '"')
$runLines.Add('com.guardian.GuardianApplication')

$runFile = Join-Path $TempDir 'guardian_run.txt'
[System.IO.File]::WriteAllLines($runFile, $runLines, [System.Text.Encoding]::Default)

Write-Host "      classpath ready"
exit 0
