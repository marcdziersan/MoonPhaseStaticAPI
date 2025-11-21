import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MoonPhaseCalibrationTool
 * ------------------------
 * Hilfsprogramm zur Kalibrierung des MoonPhaseCalculator gegen
 * externe Referenzdaten.
 *
 * Erwartete Referenzstruktur:
 *
 *   <refRoot>/moon-phase-data/<year>/index.json
 *
 * JSON-Format pro Jahr:
 *
 *   [
 *     { "Date": "YYYY-MM-DDTHH:mm:ss", "Phase": 2 },
 *     ...
 *   ]
 *
 * Für die Kalibrierung betrachten wir typischerweise nur Phase 2 (Full Moon)
 * und ermitteln, wie gut ein bestimmter Parametersatz die Vollmond-Daten
 * im Zeitraum [startYear..endYear] trifft.
 */
public class MoonPhaseCalibrationTool {

    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println(" MoonPhaseCalibrationTool – Kalibrierung");
        System.out.println("===========================================\n");

        if (args.length < 3) {
            System.out.println("Verwendung:");
            System.out.println("  java MoonPhaseCalibrationTool <refRoot> <startYear> <endYear>");
            System.out.println();
            System.out.println("Beispiel:");
            System.out.println("  java MoonPhaseCalibrationTool ..\\ref 1900 2080");
            return;
        }

        Path refRoot = Paths.get(args[0]);
        int startYear;
        int endYear;

        try {
            startYear = Integer.parseInt(args[1]);
            endYear = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.out.println("Fehler: startYear und endYear müssen Zahlen sein.");
            return;
        }

        if (endYear < startYear) {
            System.out.println("Fehler: endYear darf nicht kleiner als startYear sein.");
            return;
        }

        System.out.println("Referenzdaten:");
        System.out.println("  Basisordner : " + refRoot.toAbsolutePath());
        System.out.println("  Jahre       : " + startYear + " bis " + endYear);
        System.out.println();

        // Referenz-Vollmonde laden
        Map<Integer, List<LocalDateTime>> refFullMoons =
                loadFullMoonReferences(refRoot, startYear, endYear);

        if (refFullMoons.isEmpty()) {
            System.out.println("Keine Referenzdaten gefunden. Abbruch.");
            return;
        }

        System.out.println("Referenz-Vollmonde geladen.");
        System.out.println();

        // Parameter-Raster definieren
        LocalDateTime baseRef = LocalDateTime.of(2000, 1, 6, 18, 14);
        double baseSynodic = 29.5306;

        double synMin = 29.528;
        double synMax = 29.533;
        double synStep = 0.0002;

        int offsetMinHours = -24;
        int offsetMaxHours = 24;
        int offsetStepHours = 6;

        double tolerancePhase = 0.03;

        System.out.println("Kalibrierungsparameter:");
        System.out.println("  Basis-Ref-Neumond : " + baseRef);
        System.out.println("  SynodicMonthDays  : " + synMin + " .. " + synMax + " (Schritt " + synStep + ")");
        System.out.println("  OffsetHours       : " + offsetMinHours + " .. " + offsetMaxHours + " (Schritt " + offsetStepHours + ")");
        System.out.println("  Tolerance (phase) : " + tolerancePhase);
        System.out.println();

        double bestAvgError = Double.MAX_VALUE;
        double bestSyn = baseSynodic;
        LocalDateTime bestRef = baseRef;

        int tested = 0;

        for (int offset = offsetMinHours; offset <= offsetMaxHours; offset += offsetStepHours) {
            LocalDateTime refCandidate = baseRef.plusHours(offset);

            for (double syn = synMin; syn <= synMax + 1e-9; syn += synStep) {
                MoonPhaseCalculator calc =
                        new MoonPhaseCalculator(refCandidate, syn, tolerancePhase);

                double totalErrorDays = 0.0;
                int count = 0;

                for (int year = startYear; year <= endYear; year++) {
                    List<LocalDateTime> refList = refFullMoons.get(year);
                    if (refList == null || refList.isEmpty()) {
                        continue;
                    }

                    List<MoonPhaseCalculator.MoonEvent> events =
                            calc.calculateYear(year);

                    List<LocalDateTime> calcFull = new ArrayList<>();
                    for (MoonPhaseCalculator.MoonEvent ev : events) {
                        if (ev.getPhase() == MoonPhaseCalculator.Phase.FULL_MOON) {
                            calcFull.add(ev.getDateTime());
                        }
                    }

                    if (calcFull.size() != refList.size()) {
                        continue;
                    }

                    for (int i = 0; i < refList.size(); i++) {
                        LocalDateTime r = refList.get(i);
                        LocalDateTime c = calcFull.get(i);
                        double diffDays = Math.abs(java.time.Duration.between(r, c).toMinutes()) / (60.0 * 24.0);
                        totalErrorDays += diffDays;
                        count++;
                    }
                }

                if (count == 0) {
                    continue;
                }

                double avgError = totalErrorDays / count;
                if (avgError < bestAvgError) {
                    bestAvgError = avgError;
                    bestSyn = syn;
                    bestRef = refCandidate;
                }

                tested++;
            }
        }

        System.out.println("Getestete Kombinationen: " + tested);
        System.out.println();
        System.out.println("Beste gefundene Parameter:");
        System.out.println("  referenceNewMoon   : " + bestRef);
        System.out.println("  synodicMonthDays   : " + bestSyn);
        System.out.println("  avgAbsError (Tage) : " + bestAvgError);
        System.out.println();
        System.out.println("Diese Werte kannst du in MoonPhaseCalculator.createDefault()");
        System.out.println("eintragen, um dein Modell grob zu kalibrieren.");
    }

    /**
     * Lädt alle Referenz-Vollmonde (Phase==2) aus der Ref-Struktur.
     */
    private static Map<Integer, List<LocalDateTime>> loadFullMoonReferences(Path refRoot,
                                                                            int startYear,
                                                                            int endYear) {
        Map<Integer, List<LocalDateTime>> result = new HashMap<>();

        for (int year = startYear; year <= endYear; year++) {
            Path jsonFile = refRoot.resolve(Paths.get("moon-phase-data",
                    String.valueOf(year), "index.json"));
            if (!Files.exists(jsonFile)) {
                continue;
            }

            List<LocalDateTime> fulls = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String json = sb.toString();
                parseFullMoonsFromJson(json, fulls);
            } catch (IOException e) {
                System.out.println("Fehler beim Lesen von " + jsonFile + ": " + e.getMessage());
                continue;
            }

            if (!fulls.isEmpty()) {
                result.put(year, fulls);
            }
        }

        return result;
    }

    /**
     * Sehr einfacher JSON-Parser für das erwartete Format, extrahiert
     * nur Einträge mit Phase==2 (Full Moon).
     */
    private static void parseFullMoonsFromJson(String json, List<LocalDateTime> target) {
        String[] parts = json.split("\\{");
        for (String part : parts) {
            if (!part.contains("Date") || !part.contains("Phase")) {
                continue;
            }

            String date = extractField(part, "Date");
            String phaseStr = extractField(part, "Phase");
            if (date == null || phaseStr == null) {
                continue;
            }

            int phase;
            try {
                phase = Integer.parseInt(phaseStr);
            } catch (NumberFormatException e) {
                continue;
            }

            if (phase == 2) {
                try {
                    LocalDateTime dt = LocalDateTime.parse(date, ISO_FORMAT);
                    target.add(dt);
                } catch (Exception ignored) {
                }
            }
        }

        target.sort(LocalDateTime::compareTo);
    }

    private static String extractField(String jsonPart, String fieldName) {
        String key = "\"" + fieldName + "\"";
        int idx = jsonPart.indexOf(key);
        if (idx < 0) {
            return null;
        }
        int colon = jsonPart.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        String rest = jsonPart.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            int second = rest.indexOf('"', 1);
            if (second > 1) {
                return rest.substring(1, second);
            }
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            String val = sb.toString().trim();
            if (!val.isEmpty()) {
                return val;
            }
        }
        return null;
    }
}
