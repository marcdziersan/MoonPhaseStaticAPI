import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * MoonApiGenerator
 * ----------------
 * Generiert für einen Jahresbereich die Mondphasen-Daten mit Hilfe
 * von MoonPhaseCalculator und schreibt sie als JSON-Dateien.
 *
 * Roh-Ausgabeformat:
 *
 *   <apiRoot>/moon-phase-data/<year>/index.json
 *
 * Jede index.json ist ein Array von Objekten:
 *
 *   [
 *     { "Date": "YYYY-MM-DDTHH:mm:ss", "Phase": 0..3 },
 *     ...
 *   ]
 *
 * Diese Rohstruktur kann anschließend mit dem PowerShell-Skript
 * Convert-MoonApiStructure.ps1 in die finale API-Struktur
 * (api/index.json + api/data/<year>.json) überführt werden.
 */
public class MoonApiGenerator {

    // ISO-Format ohne Zeitzonen-Suffix
    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println(" MoonApiGenerator – statische Mond-JSON-API");
        System.out.println("===========================================\n");

        if (args.length < 3) {
            System.out.println("Verwendung:");
            System.out.println("  java MoonApiGenerator <startJahr> <endJahr> <apiRoot>");
            System.out.println();
            System.out.println("Beispiel:");
            System.out.println("  java MoonApiGenerator 1900 2080 ..\\api");
            return;
        }

        int startYear;
        int endYear;
        String apiRoot = args[2];

        try {
            startYear = Integer.parseInt(args[0]);
            endYear = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Fehler: startJahr und endJahr müssen ganze Zahlen sein.");
            return;
        }

        if (endYear < startYear) {
            System.out.println("Fehler: endJahr darf nicht kleiner als startJahr sein.");
            return;
        }

        System.out.println("Generiere Mondphasen-JSON durch eigene Berechnung:");
        System.out.println("  Von Jahr : " + startYear);
        System.out.println("  Bis Jahr : " + endYear);
        System.out.println("  API-Root : " + apiRoot);
        System.out.println();

        // Kalibrierten Rechner verwenden
        MoonPhaseCalculator calculator = MoonPhaseCalculator.createDefault();

        for (int year = startYear; year <= endYear; year++) {
            try {
                generateYearJson(calculator, year, apiRoot);
            } catch (IOException e) {
                System.out.println("Fehler beim Erzeugen für Jahr " + year + ": " + e.getMessage());
            }
        }

        System.out.println();
        System.out.println("Fertig. Rohdaten liegen unter <apiRoot>/moon-phase-data/<year>/index.json.");
    }

    /**
     * Berechnet die Mondphasen für ein Jahr und schreibt sie als
     * index.json unter:
     *
     *   <apiRoot>/moon-phase-data/<year>/index.json
     *
     * @param calculator konfigurierter MoonPhaseCalculator
     * @param year       Jahr, das erzeugt werden soll
     * @param apiRoot    Basisordner für die API (z. B. \"..\\api\")
     */
    private static void generateYearJson(MoonPhaseCalculator calculator,
                                         int year,
                                         String apiRoot) throws IOException {

        Path baseDir = Paths.get(apiRoot, "moon-phase-data", String.valueOf(year));
        Files.createDirectories(baseDir);

        Path jsonFile = baseDir.resolve("index.json");

        System.out.println("Berechne Daten für Jahr " + year + "...");
        List<MoonPhaseCalculator.MoonEvent> events =
                calculator.calculateYear(year);

        System.out.println("Schreibe: " + jsonFile.toAbsolutePath());

        try (Writer writer = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8)) {
            writer.write("[\n");

            boolean first = true;
            for (MoonPhaseCalculator.MoonEvent event : events) {
                LocalDateTime dt = event.getDateTime();
                int phaseId = event.getPhase().getId();
                String dateStr = ISO_FORMAT.format(dt);

                if (!first) {
                    writer.write(",\n");
                } else {
                    first = false;
                }

                writer.write("  { \"Date\": \"" + dateStr + "\", \"Phase\": " + phaseId + " }");
            }

            writer.write("\n]\n");
        }
    }
}
