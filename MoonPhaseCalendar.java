import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MoonPhaseCalendar
 * -----------------
 *
 * Konsolenanwendung:
 *  - Ruft Mondphasen-Daten fuer ein Jahr per HTTP ab (JSON, kein API-Key)
 *  - Nutzt DEINE statische API auf GitHub Pages (MoonPhaseStaticAPI)
 *  - Kann anzeigen:
 *      - Vollmonde fuer aktuelles Jahr
 *      - Vollmonde fuer beliebiges Jahr
 *      - Vollmonde fuer Jahr als CSV exportieren
 *      - Alle Mondphasen fuer ein Jahr
 *      - Alle Mondphasen fuer einen Monat eines Jahres
 *      - Aktuelle Mondphase (Text + ASCII)
 *
 * Datenquelle (deine API, Beispiel):
 *   https://marcdziersan.github.io/MoonPhaseStaticAPI/api/data/2025.json
 *
 * JSON-Format (vereinfacht):
 *   [
 *     { "Date": "2025-01-13T22:27:00", "Phase": 2 },
 *     ...
 *   ]
 *
 * Phase-Codierung:
 *   0 = Neumond
 *   1 = Erstes Viertel
 *   2 = Vollmond
 *   3 = Letztes Viertel
 */
public class MoonPhaseCalendar {

    // DEINE eigene statische API auf GitHub Pages:
    // Jahresdaten liegen unter:
    //   https://marcdziersan.github.io/MoonPhaseStaticAPI/api/data/<year>.json
    private static final String BASE_URL =
            "https://marcdziersan.github.io/MoonPhaseStaticAPI/api/data/";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // Nur Datum für Konsolen-Ausgabe: DD/MM/YYYY
    private static final DateTimeFormatter DATE_ONLY_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Scanner SCANNER = new Scanner(System.in);

    // Regex, um Date und Phase aus jedem JSON-Objekt zu extrahieren
    // Beispiel:
    // { "Date": "2025-01-13T22:27:00", "Phase": 2 }
    private static final Pattern EVENT_PATTERN = Pattern.compile(
            "\\{[^}]*\"Date\"\\s*:\\s*\"(.*?)\"[^}]*\"Phase\"\\s*:\\s*(\\d+)[^}]*\\}"
    );

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println(" MoonPhaseCalendar – Vollmond- und Mondphasen-Kalender");
        System.out.println(" Datenquelle: MoonPhaseStaticAPI (GitHub Pages, kein API-Key)");
        System.out.println(" Basis-URL  : " + BASE_URL);
        System.out.println("===========================================");
        System.out.println();

        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("Hauptmenü");
            System.out.println("---------");
            System.out.println("1) Vollmonde für aktuelles Jahr anzeigen");
            System.out.println("2) Vollmonde für bestimmtes Jahr anzeigen");
            System.out.println("3) Vollmonde für bestimmtes Jahr als CSV exportieren");
            System.out.println("4) Alle Mondphasen für bestimmtes Jahr anzeigen");
            System.out.println("5) Alle Mondphasen für bestimmten Monat anzeigen");
            System.out.println("6) Aktuelle Mondphase anzeigen (Text + ASCII)");
            System.out.println("0) Beenden");
            System.out.print("Ihre Wahl: ");

            String choice = SCANNER.nextLine().trim();

            switch (choice) {
                case "1":
                    handleShowCurrentYearFullMoons();
                    break;
                case "2":
                    handleShowSpecificYearFullMoons();
                    break;
                case "3":
                    handleExportCsvFullMoons();
                    break;
                case "4":
                    handleShowAllPhasesYear();
                    break;
                case "5":
                    handleShowAllPhasesMonth();
                    break;
                case "6":
                    handleShowCurrentPhase();
                    break;
                case "0":
                    running = false;
                    System.out.println("Auf Wiedersehen!");
                    break;
                default:
                    System.out.println("Ungültige Eingabe. Bitte 0, 1, 2, 3, 4, 5 oder 6 wählen.");
            }
        }
    }

    // ===================== Menü-Handler =====================

    private static void handleShowCurrentYearFullMoons() {
        int year = LocalDate.now().getYear();
        System.out.println();
        System.out.println("Vollmonde für das aktuelle Jahr " + year + ":");
        showFullMoonsForYear(year);
    }

    private static void handleShowSpecificYearFullMoons() {
        int year = readYearFromUser();
        if (year <= 0) {
            return;
        }
        System.out.println();
        System.out.println("Vollmonde für das Jahr " + year + ":");
        showFullMoonsForYear(year);
    }

    private static void handleExportCsvFullMoons() {
        int year = readYearFromUser();
        if (year <= 0) {
            return;
        }

        List<MoonEvent> events;
        try {
            events = fetchEventsForYear(year);
        } catch (IOException | InterruptedException e) {
            System.out.println("Fehler beim Abruf der Mondphasen: " + e.getMessage());
            return;
        }

        List<MoonEvent> fullMoons = filterByPhase(events, 2); // 2 = Vollmond

        if (fullMoons.isEmpty()) {
            System.out.println("Keine Vollmonde für das Jahr " + year + " gefunden.");
            return;
        }

        String fileName = "fullmoons_" + year + ".csv";
        try (PrintWriter pw = new PrintWriter(fileName, java.nio.charset.StandardCharsets.UTF_8)) {
            // Kopfzeile
            pw.println("DateTimeUtc,Phase");

            for (MoonEvent event : fullMoons) {
                // Für CSV behalten wir den exakten ISO-Zeitstempel
                pw.println(event.date.toString() + "," + event.phase);
            }
        } catch (IOException e) {
            System.out.println("Fehler beim Schreiben der CSV-Datei: " + e.getMessage());
            return;
        }

        System.out.println("CSV exportiert: " + fileName);
    }

    private static void handleShowAllPhasesYear() {
        int year = readYearFromUser();
        if (year <= 0) {
            return;
        }
        System.out.println();
        System.out.println("Alle Mondphasen für das Jahr " + year + ":");
        showAllPhasesForYear(year);
    }

    private static void handleShowAllPhasesMonth() {
        int year = readYearFromUser();
        if (year <= 0) {
            return;
        }
        int month = readMonthFromUser();
        if (month <= 0) {
            return;
        }
        System.out.println();
        System.out.println("Alle Mondphasen für " + String.format("%02d/%d", month, year) + ":");
        showAllPhasesForMonth(year, month);
    }

    /**
     * Menü-Handler: Aktuelle Mondphase anzeigen (Text + ASCII).
     * Näherung auf Basis deiner API-Daten, in UTC.
     */
    private static void handleShowCurrentPhase() {
        // "Jetzt" als UTC-Zeitpunkt
        Instant nowInstant = Instant.now();
        LocalDateTime nowUtc = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC);
        int year = nowUtc.getYear();

        List<MoonEvent> allEvents = new ArrayList<>();

        try {
            // aktuelles Jahr
            allEvents.addAll(fetchEventsForYear(year));
        } catch (Exception e) {
            System.out.println("Fehler beim Abruf der Mondphasen für Jahr " + year + ": " + e.getMessage());
        }

        // Falls nötig, Vorjahr/Nachjahr dazuziehen (Randbereich um Neujahr)
        try {
            if (year > 1900) {
                allEvents.addAll(fetchEventsForYear(year - 1));
            }
        } catch (Exception ignored) {
        }
        try {
            if (year < 3000) {
                allEvents.addAll(fetchEventsForYear(year + 1));
            }
        } catch (Exception ignored) {
        }

        if (allEvents.isEmpty()) {
            System.out.println("Keine Mondphasen-Daten im Bereich um das aktuelle Jahr gefunden.");
            return;
        }

        // nach Datum sortieren
        Collections.sort(allEvents, Comparator.comparing(e -> e.date));

        MoonEvent last = null;
        MoonEvent next = null;

        for (MoonEvent e : allEvents) {
            if (!e.date.isAfter(nowUtc)) {
                last = e;
            } else {
                next = e;
                break;
            }
        }

        if (last == null && next == null) {
            System.out.println("Keine geeigneten Referenzphasen zur Bestimmung gefunden.");
            return;
        }

        // Distanz in Tagen zu letzter/nächster Hauptphase
        double diffToLastDays = Double.MAX_VALUE;
        double diffToNextDays = Double.MAX_VALUE;

        if (last != null) {
            diffToLastDays = Math.abs(Duration.between(last.date, nowUtc).toHours() / 24.0);
        }
        if (next != null) {
            diffToNextDays = Math.abs(Duration.between(next.date, nowUtc).toHours() / 24.0);
        }

        // Wir nehmen die zeitlich nächstgelegene Hauptphase als "repräsentative Phase"
        MoonEvent nearest;
        if (diffToLastDays <= diffToNextDays) {
            nearest = last;
        } else {
            nearest = next;
        }

        String todayStr = nowUtc.toLocalDate().format(DATE_ONLY_FORMAT);

        System.out.println();
        System.out.println("Aktuelle Mondphase (UTC-basierte Näherung):");
        System.out.println("-------------------------------------------");
        System.out.println("Heute (UTC): " + todayStr);

        if (last != null) {
            System.out.println("Letzte Hauptphase : "
                    + last.date.format(DATE_ONLY_FORMAT)
                    + " – " + phaseToName(last.phase)
                    + " (Phase " + last.phase + ")");
        }
        if (next != null) {
            System.out.println("Nächste Hauptphase: "
                    + next.date.format(DATE_ONLY_FORMAT)
                    + " – " + phaseToName(next.phase)
                    + " (Phase " + next.phase + ")");
        }

        System.out.println();

        int phase = nearest.phase;
        String phaseName = phaseToName(phase);
        String ascii = asciiForPhase(phase);

        System.out.println("Abgeleitete aktuelle Phase (nahe nächster/letzter Hauptphase):");
        System.out.println("  " + ascii + "  " + phaseName + " (Phase " + phase + ")");

        if (next != null && diffToNextDays != Double.MAX_VALUE) {
            System.out.printf("  (nächste Hauptphase in ca. %.1f Tagen)%n", diffToNextDays);
        }
    }

    // ===================== Eingabe-Hilfsmethoden =====================

    private static int readYearFromUser() {
        System.out.print("Bitte Jahr eingeben (z. B. 2025, Abbruch mit leerer Eingabe): ");
        String input = SCANNER.nextLine().trim();

        if (input.isEmpty()) {
            System.out.println("Abgebrochen.");
            return -1;
        }

        try {
            int year = Integer.parseInt(input);
            if (year < 1 || year > 3000) {
                System.out.println("Ungültiges Jahr. Bitte zwischen 1 und 3000 wählen.");
                return -1;
            }
            return year;
        } catch (NumberFormatException e) {
            System.out.println("Das ist keine gültige Jahreszahl.");
            return -1;
        }
    }

    private static int readMonthFromUser() {
        System.out.print("Bitte Monat eingeben (1–12, Abbruch mit leerer Eingabe): ");
        String input = SCANNER.nextLine().trim();

        if (input.isEmpty()) {
            System.out.println("Abgebrochen.");
            return -1;
        }

        try {
            int month = Integer.parseInt(input);
            if (month < 1 || month > 12) {
                System.out.println("Ungültiger Monat. Bitte Wert zwischen 1 und 12 eingeben.");
                return -1;
            }
            return month;
        } catch (NumberFormatException e) {
            System.out.println("Das ist keine gültige Monatszahl.");
            return -1;
        }
    }

    // ===================== Anzeige-Funktionen =====================

    private static void showFullMoonsForYear(int year) {
        List<MoonEvent> events;
        try {
            events = fetchEventsForYear(year);
        } catch (IOException | InterruptedException e) {
            System.out.println("Fehler beim Abruf der Mondphasen: " + e.getMessage());
            return;
        }

        List<MoonEvent> fullMoons = filterByPhase(events, 2); // 2 = Vollmond

        if (fullMoons.isEmpty()) {
            System.out.println("Keine Vollmonde für das Jahr " + year + " gefunden.");
            return;
        }

        System.out.println();
        System.out.println("Vollmonde im Jahr " + year + " (Datum in UTC, ohne Uhrzeit):");
        System.out.println("-------------------------------------------------------------");
        for (MoonEvent event : fullMoons) {
            String formattedDate = event.date.format(DATE_ONLY_FORMAT);
            System.out.println(formattedDate + " - Vollmond (Phase 2)");
        }
    }

    private static void showAllPhasesForYear(int year) {
        List<MoonEvent> events;
        try {
            events = fetchEventsForYear(year);
        } catch (IOException | InterruptedException e) {
            System.out.println("Fehler beim Abruf der Mondphasen: " + e.getMessage());
            return;
        }

        if (events.isEmpty()) {
            System.out.println("Keine Mondphasen-Einträge für das Jahr " + year + " gefunden.");
            return;
        }

        System.out.println();
        System.out.println("Mondphasen im Jahr " + year + " (Datum in UTC, ohne Uhrzeit):");
        System.out.println("----------------------------------------------------------------");
        for (MoonEvent event : events) {
            String formattedDate = event.date.format(DATE_ONLY_FORMAT);
            String phaseName = phaseToName(event.phase);
            System.out.println(formattedDate + " - " + phaseName + " (Phase " + event.phase + ")");
        }
    }

    private static void showAllPhasesForMonth(int year, int month) {
        List<MoonEvent> events;
        try {
            events = fetchEventsForYear(year);
        } catch (IOException | InterruptedException e) {
            System.out.println("Fehler beim Abruf der Mondphasen: " + e.getMessage());
            return;
        }

        List<MoonEvent> filtered = new ArrayList<>();
        for (MoonEvent event : events) {
            if (event.date.getYear() == year && event.date.getMonthValue() == month) {
                filtered.add(event);
            }
        }

        if (filtered.isEmpty()) {
            System.out.println("Keine Mondphasen-Einträge für " +
                    String.format("%02d/%d", month, year) + " gefunden.");
            return;
        }

        System.out.println();
        System.out.println("Mondphasen für " + String.format("%02d/%d", month, year) +
                " (Datum in UTC, ohne Uhrzeit):");
        System.out.println("----------------------------------------------------------------");
        for (MoonEvent event : filtered) {
            String formattedDate = event.date.format(DATE_ONLY_FORMAT);
            String phaseName = phaseToName(event.phase);
            System.out.println(formattedDate + " - " + phaseName + " (Phase " + event.phase + ")");
        }
    }

    // ===================== HTTP / JSON =====================

    private static List<MoonEvent> fetchEventsForYear(int year)
            throws IOException, InterruptedException {

        String url = buildYearUrl(year);
        System.out.println();
        System.out.println("Hole Mondphasen-Daten von:");
        System.out.println("  " + url);
        System.out.println();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response =
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP-Status " + response.statusCode() +
                    " beim Abrufen von " + url);
        }

        String json = response.body();
        return parseEventsFromJson(json);
    }

    /**
     * Baut die Jahres-URL auf Basis deiner statischen API:
     *   BASE_URL + "<year>.json"
     * Beispiel:
     *   https://marcdziersan.github.io/MoonPhaseStaticAPI/api/data/2025.json
     */
    private static String buildYearUrl(int year) {
        return BASE_URL + year + ".json";
    }

    private static List<MoonEvent> parseEventsFromJson(String json) {
        List<MoonEvent> events = new ArrayList<>();
        Matcher m = EVENT_PATTERN.matcher(json);

        while (m.find()) {
            String dateStr = m.group(1);
            String phaseStr = m.group(2);

            LocalDateTime dt;
            try {
                dt = LocalDateTime.parse(dateStr);
            } catch (Exception e) {
                continue;
            }

            int phase;
            try {
                phase = Integer.parseInt(phaseStr);
            } catch (NumberFormatException e) {
                continue;
            }

            events.add(new MoonEvent(dt, phase));
        }

        return events;
    }

    // ===================== Hilfsfunktionen =====================

    private static List<MoonEvent> filterByPhase(List<MoonEvent> events, int phase) {
        List<MoonEvent> result = new ArrayList<>();
        for (MoonEvent e : events) {
            if (e.phase == phase) {
                result.add(e);
            }
        }
        return result;
    }

    private static String phaseToName(int phase) {
        switch (phase) {
            case 0:
                return "Neumond";
            case 1:
                return "Erstes Viertel";
            case 2:
                return "Vollmond";
            case 3:
                return "Letztes Viertel";
            default:
                return "Unbekannte Phase";
        }
    }

    /**
     * ASCII-„Symbol“ für die 4 Hauptphasen.
     * Sehr simpel, bewusst ohne Unicode.
     */
    private static String asciiForPhase(int phase) {
        switch (phase) {
            case 0:
                return "[   ]";  // Neumond
            case 1:
                return "[=  ]";  // Erstes Viertel
            case 2:
                return "[###]"; // Vollmond
            case 3:
                return "[  =]"; // Letztes Viertel
            default:
                return "[ ? ]";
        }
    }

    private static class MoonEvent {
        final LocalDateTime date;
        final int phase;

        MoonEvent(LocalDateTime date, int phase) {
            this.date = date;
            this.phase = phase;
        }
    }
}
