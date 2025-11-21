param(
    # API-Root (enthaelt index.json und data\*.json)
    [string]$ApiRoot = "..\api"
)

Write-Host "Check Moon API data..." -ForegroundColor Cyan
Write-Host ("ApiRoot : " + $ApiRoot) -ForegroundColor Cyan
Write-Host ""

# API-Root und data-Ordner ermitteln
$apiRootPath = (Resolve-Path $ApiRoot).Path
$dataDir     = Join-Path $apiRootPath "data"

if (-not (Test-Path $dataDir)) {
    Write-Host ("Fehler: data-Ordner nicht gefunden: " + $dataDir) -ForegroundColor Red
    exit 1
}

Write-Host ("1) JSON-Format-Pruefung in " + $dataDir) -ForegroundColor Yellow

# Alle JSON-Dateien im data-Ordner einsammeln
$dataFiles = Get-ChildItem -Path $dataDir -Filter "*.json" -File -ErrorAction SilentlyContinue

if (-not $dataFiles -or $dataFiles.Count -eq 0) {
    Write-Host "Keine JSON-Dateien in data gefunden." -ForegroundColor Red
    exit 1
}

$formatErrors = 0

foreach ($file in $dataFiles) {
    try {
        # Versuch, die Datei als JSON zu parsen
        $null = Get-Content $file.FullName -Raw | ConvertFrom-Json
        Write-Host ("OK   : " + $file.Name) -ForegroundColor Green
    }
    catch {
        Write-Host ("FAIL : " + $file.Name + " - " + $_.Exception.Message) -ForegroundColor Red
        $formatErrors++
    }
}

Write-Host ""
Write-Host ("JSON-Format-Check abgeschlossen. Fehler: " + $formatErrors)

if ($formatErrors -gt 0) {
    Write-Host "Warnung: Es gab Formatfehler. Bitte zuerst beheben." -ForegroundColor Yellow
}
