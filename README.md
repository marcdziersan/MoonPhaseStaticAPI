# MoonPhaseStaticAPI

Statische Mondphasen-API auf Basis einer eigenen, kalibrierten Berechnung.
Die Daten werden als JSON-Dateien pro Jahr erzeugt und können z. B. über
GitHub Pages als „fertige“ API ausgeliefert werden.

---

## 1. Überblick

Diese API stellt für jeden Tag mit relevanter Mondphase Einträge bereit, die
folgende vier Hauptphasen abbilden:

- **0 – New Moon (Neumond)**
- **1 – First Quarter (Erstes Viertel)**
- **2 – Full Moon (Vollmond)**
- **3 – Last Quarter (Letztes Viertel)**

Die Berechnung erfolgt mit einer eigenen Implementierung (`MoonPhaseCalculator`)
auf Basis eines Referenz-Neumonds und einer kalibrierten synodischen
Monatslänge. Die Ergebnisse wurden gegen Referenzdaten von 1900–2080
kalibriert, so dass die Vorhersage auf den **Kalendertag genau** ist
(typischer Fehler im Minutenbereich, siehe Abschnitt „Genauigkeit“).

Typischer Einsatz:

- Vollmondkalender (z. B. Konsolen-Tool, Desktop-App)
- Kalender- oder Wetter‑Apps mit Mondphaseninformationen
- Lern- und Schulungsprojekte (API-Verbrauch, JSON, Astronomie-Basics)

---

## 2. API-Struktur

Die API ist als reine statische Dateistruktur organisiert:

```text
/api
  ├── index.json          # Metadaten, verfügbare Jahre, Pfadkonvention
  └── data/
      ├── 1900.json
      ├── 1901.json
      ├── ...
      └── 2080.json
```

### 2.1 `index.json` – Metadaten & Schema

Beispiel:

```json
{
  "name": "MoonPhaseStaticAPI",
  "description": "Precomputed moon phases (new, first quarter, full, last) per year.",
  "minYear": 1900,
  "maxYear": 2080,
  "years": [1900, 1901, 1902],
  "version": "1.0.0",
  "generatedAt": "2025-11-21T03:45:12+01:00",
  "dataPath": "data/{year}.json",
  "phases": {
    "0": { "en": "New Moon", "de": "Neumond" },
    "1": { "en": "First Quarter", "de": "Erstes Viertel" },
    "2": { "en": "Full Moon", "de": "Vollmond" },
    "3": { "en": "Last Quarter", "de": "Letztes Viertel" }
  }
}
```

- `minYear`, `maxYear`: kleinste und größte Jahrgangsdatei, die in `data/`
  vorhanden ist.
- `years`: Liste aller verfügbaren Jahre, numerisch sortiert.
- `dataPath`: Pfad-Template. Der Platzhalter `{year}` wird durch das Jahr
  ersetzt, z. B. `data/2025.json`.
- `phases`: Mapping der Phase-IDs auf englische/deutsche Bezeichnungen.

### 2.2 `data/{year}.json` – Jahresdaten

Jede Jahresdatei enthält ein Array von Ereignissen (Events). Jedes Event hat:

- `Date` – Datum/Zeit in UTC, ISO‑8601 ohne Zeitzonen-Suffix
- `Phase` – Integer 0–3 (siehe oben)

Beispiel (`data/2025.json`, Auszug):

```json
[
  { "Date": "2025-01-06T12:00:00", "Phase": 2 },
  { "Date": "2025-01-13T12:00:00", "Phase": 3 },
  { "Date": "2025-01-21T12:00:00", "Phase": 0 },
  { "Date": "2025-01-28T12:00:00", "Phase": 1 }
]
```

---

## 3. Generator & Aufbau des Repos

Dieses Repository ist in zwei Teile aufgeteilt:

```text
MoonPhaseStaticAPI/
  api/           # Fertige, statische API (für GitHub Pages o.ä.)
  generator/     # Java-Tools & Skripte zum Erzeugen/Validieren
```

### 3.1 `generator/src`

- `MoonPhaseCalculator.java`  
  Kernklasse zur Berechnung der Mondphasen auf Basis eines
  Referenz-Neumonds und einer synodischen Monatslänge.

- `MoonApiGenerator.java`  
  Konsolenprogramm, das für einen angegebenen Jahresbereich Rohdaten
  unter `api/moon-phase-data/<year>/index.json` erzeugt.

- `MoonPhaseCalibrationTool.java`  
  Tool zur Kalibrierung des Modells gegen externe Referenzdaten.
  Erwartet eine Struktur wie:
  `ref/moon-phase-data/<year>/index.json` und optimiert
  `referenceNewMoon` und `synodicMonthDays`.

### 3.2 `generator/scripts`

- `Convert-MoonApiStructure.ps1`  
  Konvertiert die Rohstruktur `api/moon-phase-data/<year>/index.json` in
  das finale Format:

  - `api/data/<year>.json`
  - `api/index.json`

- `Check-MoonApiData.ps1`  
  Einfaches Test-/Validierungsskript:

  - Prüft, ob alle Dateien in `api/data/*.json` gültiges JSON sind.
  - Optional: Vergleich ausgewählter Jahre mit Referenzdaten.

---

## 4. Generierungs-Workflow

### 4.1 Java-Code kompilieren

```powershell
cd .\generator

mkdir out -ErrorAction SilentlyContinue
javac -d out src\*.java
```

### 4.2 Rohdaten erzeugen (pro Jahr)

Beispiel: Jahre 1900–2080 unter `api\moon-phase-data` erzeugen:

```powershell
cd .\generator

java -cp out MoonApiGenerator 1900 2080 ..pi
```

Ergebnis:

```text
api  moon-phase-data    1900\index.json
    1901\index.json
    ...
    2080\index.json
```

### 4.3 In finale API-Struktur konvertieren

```powershell
cd .\generator

# 1. Konvertieren (alte Struktur bleibt)
.\scripts\Convert-MoonApiStructure.ps1 -ApiRoot "..pi"

# 2. Optional: alte Struktur aufräumen
.\scripts\Convert-MoonApiStructure.ps1 -ApiRoot "..pi" -CleanupOld
```

Ergebnis:

```text
api  index.json
  data    1900.json
    ...
    2080.json
```

---

## 5. Kalibrierung gegen Referenzdaten

Die Kalibrierung wurde mit einem separaten Satz Referenzdaten durchgeführt
(z. B. aus einer bestehenden Mondphasen-API). Erwartete Struktur:

```text
ref/
  moon-phase-data/
    1900/index.json
    ...
    2080/index.json
```

Aufruf:

```powershell
cd .\generator

java -cp out MoonPhaseCalibrationTool "..ef" 1900 2080
```

Die Ausgabe enthält u. a.:

- Bestes `referenceNewMoon`
- Bestes `synodicMonthDays`
- Durchschnittliche und maximale Abweichung in Tagen

Diese Werte wurden in `MoonPhaseCalculator.createDefault()` eingetragen.

---

## 6. Genauigkeit & Limitierungen

- Kalibrierung auf Zeitraum 1900–2080:
  - `avgAbsError` (mittlere Abweichung) im Bereich weniger Minuten
  - `maxAbsError` (maximale Abweichung) ca. 1 Tag

- Der Fokus liegt auf:
  - korrekter **Phase** (0/1/2/3) pro Tag
  - korrekter **Kalendertag** der Vollmonde etc.

- Nicht Ziel:
  - hochpräzise astronomische Zeiten im Sekundenbereich
  - Berücksichtigung von Parallaxen, Relativität etc.

Für Kalender‑ und Display-Zwecke ist die Genauigkeit in der Regel
vollkommen ausreichend.

---

## 7. API nutzen – Beispiel

### 7.1 Java-Client (JDK 11+)

```java
// Beispiel: Vollmonde für ein Jahr auslesen
HttpClient client = HttpClient.newHttpClient();
int year = 2025;
String url = "https://<DEIN_USER>.github.io/MoonPhaseStaticAPI/api/data/" + year + ".json";

HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

if (resp.statusCode() == 200) {
    String body = resp.body();
    // JSON parsen (z.B. mit einer Bibliothek oder einem einfachen Parser)
}
```

### 7.2 PowerShell-Beispiel

```powershell
$year = 2025
$baseUrl = "https://<DEIN_USER>.github.io/MoonPhaseStaticAPI/api"
$url = "$baseUrl/data/$year.json"

$data = Invoke-RestMethod -Uri $url
$fullMoons = $data | Where-Object { $_.Phase -eq 2 }

$fullMoons | ForEach-Object {
    "{0}  Phase={1}" -f $_.Date, $_.Phase
}
```

---

## 8. Hosting über GitHub Pages

1. Dieses Repo bei GitHub anlegen, z. B. `MoonPhaseStaticAPI`.
2. Unter **Settings → Pages**:
   - Source: `Deploy from a branch`
   - Branch: `main`
   - Folder: `/api`
3. Danach sind die Dateien z. B. unter:

   - `https://<DEIN_USER>.github.io/MoonPhaseStaticAPI/api/index.json`
   - `https://<DEIN_USER>.github.io/MoonPhaseStaticAPI/api/data/2025.json`

erreichbar.

---

## 9. Lizenz

Siehe `LICENSE` (MIT-Lizenz). Du kannst die Daten, den Code und die API
frei nutzen, ändern und weitergeben, solange der Copyright-Hinweis
erhalten bleibt.
