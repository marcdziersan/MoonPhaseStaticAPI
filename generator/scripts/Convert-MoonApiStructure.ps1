param(
    # Wurzelordner der API-Struktur relativ zum aktuellen Pfad oder absolut
    [string]$ApiRoot = ".\api",

    # Wenn gesetzt: alte Struktur "moon-phase-data\<year>\index.json"
    # nach erfolgreichem Kopieren löschen
    [switch]$CleanupOld
)

Write-Host "Convert Moon API structure..." -ForegroundColor Cyan
Write-Host "ApiRoot    : $ApiRoot" -ForegroundColor Cyan
Write-Host "CleanupOld : $CleanupOld" -ForegroundColor Cyan
Write-Host ""

# Pfade auflösen
$apiRootPath       = (Resolve-Path $ApiRoot).Path
$moonPhaseDataRoot = Join-Path $apiRootPath "moon-phase-data"
$dataDir           = Join-Path $apiRootPath "data"
$indexJsonPath     = Join-Path $apiRootPath "index.json"

Write-Host "API Root          : $apiRootPath"
Write-Host "moon-phase-data   : $moonPhaseDataRoot"
Write-Host "data              : $dataDir"
Write-Host "index.json (neu)  : $indexJsonPath"
Write-Host ""

if (-not (Test-Path $moonPhaseDataRoot)) {
    Write-Host "Fehler: Ordner 'moon-phase-data' wurde nicht gefunden unter $moonPhaseDataRoot" -ForegroundColor Red
    exit 1
}

# Zielordner data anlegen
if (-not (Test-Path $dataDir)) {
    Write-Host "Erzeuge data-Ordner: $dataDir"
    New-Item -ItemType Directory -Path $dataDir | Out-Null
}

$years  = @()
$errors = @()

# Alle Unterordner (Jahr) in moon-phase-data
$yearDirs = Get-ChildItem -Path $moonPhaseDataRoot -Directory -ErrorAction SilentlyContinue

foreach ($dir in $yearDirs) {
    $yearName = $dir.Name

    # nur vierstellige Zahl als Jahr akzeptieren
    if ($yearName -notmatch '^\d{4}$') {
        Write-Host "Überspringe Ordner (kein Jahr): $yearName" -ForegroundColor DarkGray
        continue
    }

    $srcJson = Join-Path $dir.FullName "index.json"
    if (-not (Test-Path $srcJson)) {
        Write-Host "Warnung: Keine index.json in $($dir.FullName), überspringe..." -ForegroundColor Yellow
        continue
    }

    $dstJson = Join-Path $dataDir ($yearName + ".json")

    Write-Host "Kopiere $srcJson -> $dstJson"
    try {
        Copy-Item -Path $srcJson -Destination $dstJson -Force
        $years += [int]$yearName
    }
    catch {
        Write-Host "Fehler beim Kopieren von ${srcJson}: $($_.Exception.Message)" -ForegroundColor Red
        $errors += $yearName
    }
}

if ($years.Count -eq 0) {
    Write-Host "Keine gültigen Jahresdateien gefunden. Abbruch." -ForegroundColor Red
    exit 1
}

$yearsSorted = $years | Sort-Object
$minYear     = ($yearsSorted | Measure-Object -Minimum).Minimum
$maxYear     = ($yearsSorted | Measure-Object -Maximum).Maximum

Write-Host ""
Write-Host "Erzeugte data-Dateien für Jahre: $($yearsSorted -join ', ')"
Write-Host "Jahr-Spanne: $minYear .. $maxYear"
if ($errors.Count -gt 0) {
    Write-Host "Fehler bei Jahren: $($errors -join ', ')" -ForegroundColor Yellow
}
Write-Host ""

# index.json im API-Root erzeugen
$indexObject = [ordered]@{
    name        = "MoonPhaseStaticAPI"
    description = "Precomputed moon phases (new, first quarter, full, last) per year."
    minYear     = $minYear
    maxYear     = $maxYear
    years       = $yearsSorted
    version     = "1.0.0"
    generatedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    dataPath    = "data/{year}.json"
    phases      = @{
        "0" = @{ en = "New Moon";      de = "Neumond" }
        "1" = @{ en = "First Quarter"; de = "Erstes Viertel" }
        "2" = @{ en = "Full Moon";     de = "Vollmond" }
        "3" = @{ en = "Last Quarter";  de = "Letztes Viertel" }
    }
}

Write-Host "Schreibe Index-Datei: $indexJsonPath"
$indexObject | ConvertTo-Json -Depth 5 | Out-File -FilePath $indexJsonPath -Encoding UTF8

# optional: alte Struktur aufräumen
if ($CleanupOld) {
    Write-Host ""
    Write-Host "Bereinige alte Struktur 'moon-phase-data'..." -ForegroundColor Yellow
    try {
        Remove-Item -Path $moonPhaseDataRoot -Recurse -Force
        Write-Host "Ordner 'moon-phase-data' wurde gelöscht." -ForegroundColor Green
    }
    catch {
        Write-Host "Fehler beim Löschen von 'moon-phase-data': $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Konvertierung abgeschlossen." -ForegroundColor Green
