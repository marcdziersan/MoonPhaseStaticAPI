import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * MoonPhaseCalculator
 * -------------------
 * Berechnet Mondphasen (New, First Quarter, Full, Last) auf Basis
 * eines einfachen Modells:
 *
 *   - Referenz-Neumond (referenceNewMoon)
 *   - synodische Monatslänge (synodicMonthDays) in Tagen
 *
 * Die Phase wird als Wert im Bereich [0..1) modelliert:
 *   0.00 ~ New Moon
 *   0.25 ~ First Quarter
 *   0.50 ~ Full Moon
 *   0.75 ~ Last Quarter
 *
 * Für eine gegebene Phase (z. B. 0.5) suchen wir in einem Zeitfenster
 * nach Zeitpunkten, bei denen der Phasenwert nahe genug an diesem Ziel
 * liegt (innerhalb einer Toleranz).
 *
 * Das Modell ist bewusst vereinfacht und wurde mit dem
 * MoonPhaseCalibrationTool gegen Referenzdaten 1900–2080 kalibriert.
 * Zielgenauigkeit: auf den Kalendertag genau.
 */
public class MoonPhaseCalculator {

    /**
     * Zeitschritt für die grobe Suche in calculateYear().
     * 3 Stunden ist ein guter Kompromiss aus Genauigkeit und Laufzeit.
     * (Bei Bedarf auf 1 reduzieren für noch feinere Ergebnisse.)
     */
    private static final int STEP_HOURS = 3;

    /**
     * Aufzählung der vier Hauptphasen.
     */
    public enum Phase {
        NEW_MOON(0, 0.0),
        FIRST_QUARTER(1, 0.25),
        FULL_MOON(2, 0.5),
        LAST_QUARTER(3, 0.75);

        private final int id;
        private final double target;

        Phase(int id, double target) {
            this.id = id;
            this.target = target;
        }

        public int getId() {
            return id;
        }

        public double getTarget() {
            return target;
        }
    }

    /**
     * Datenklasse für ein Mondphasen-Ereignis.
     */
    public static class MoonEvent {
        private final LocalDateTime dateTime;
        private final Phase phase;

        public MoonEvent(LocalDateTime dateTime, Phase phase) {
            this.dateTime = dateTime;
            this.phase = phase;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public Phase getPhase() {
            return phase;
        }
    }

    private final LocalDateTime referenceNewMoon;
    private final double synodicMonthDays;
    private final double tolerancePhase;

    /**
     * Konstruktor.
     *
     * @param referenceNewMoon Referenz-Neumond (z. B. nahe Anfang 2000)
     * @param synodicMonthDays Synodische Monatslänge in Tagen
     * @param tolerancePhase   Toleranzbereich für die Phasensuche (0..1),
     *                         z. B. 0.03 ~ ca. +/- 0.9 Tage
     */
    public MoonPhaseCalculator(LocalDateTime referenceNewMoon,
                               double synodicMonthDays,
                               double tolerancePhase) {
        this.referenceNewMoon = referenceNewMoon;
        this.synodicMonthDays = synodicMonthDays;
        this.tolerancePhase = tolerancePhase;
    }

    /**
     * Erzeugt den kalibrierten Standard-Rechner auf Basis der
     * vorherigen Kalibrierung mit Referenzdaten 1900–2080.
     *
     * Kalibrierte Werte (aus MoonPhaseCalibrationTool):
     *   referenceNewMoon   : 2000-01-06T18:14
     *   synodicMonthDays   : ~29.5306
     *   avgAbsError (Tage) : ~0.0033
     *   maxAbsError (Tage) : ~1.0
     */
    public static MoonPhaseCalculator createDefault() {
        // Kalibrierter Referenz-Neumond
        LocalDateTime ref = LocalDateTime.of(2000, 1, 6, 18, 14);
        // Kalibrierte synodische Monatslänge (Tage)
        double synodic = 29.5306;
        // Toleranz im Phasenraum
        double tol = 0.03;
        return new MoonPhaseCalculator(ref, synodic, tol);
    }

    /**
     * Berechnet alle vier Hauptphasen für ein Jahr.
     *
     * Es wird mit einem groben Raster von STEP_HOURS Stunden durch das Jahr
     * (inkl. Puffer von ±2 Tagen) gegangen. Treffer im Toleranzbereich werden
     * mit stündlicher Feinsuche verfeinert und anschließend „dedupliziert“.
     *
     * @param year Jahr, z. B. 2025
     * @return Liste von Mondereignissen (sortiert nach Datum)
     */
    public List<MoonEvent> calculateYear(int year) {
        List<MoonEvent> result = new ArrayList<>();

        // Zeitraum des Zieljahres
        LocalDateTime yearStart = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime yearEnd   = LocalDateTime.of(year + 1, 1, 1, 0, 0);

        // Kleiner Puffer links/rechts: so erwischen wir Phasen, die knapp
        // vor/nach der Jahresgrenze liegen, aber kalendarisch noch ins Jahr fallen.
        LocalDateTime t    = yearStart.minusDays(2)
                                      .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime tEnd = yearEnd.plusDays(2);

        while (!t.isAfter(tEnd)) {
            // Für jede Hauptphase prüfen
            checkAndAdd(result, t, Phase.NEW_MOON,       Phase.NEW_MOON.getTarget(),       tolerancePhase);
            checkAndAdd(result, t, Phase.FIRST_QUARTER,  Phase.FIRST_QUARTER.getTarget(),  tolerancePhase);
            checkAndAdd(result, t, Phase.FULL_MOON,      Phase.FULL_MOON.getTarget(),      tolerancePhase);
            checkAndAdd(result, t, Phase.LAST_QUARTER,   Phase.LAST_QUARTER.getTarget(),   tolerancePhase);

            // Grober Schritt
            t = t.plusHours(STEP_HOURS);
        }

        // Ergebnisse sortieren und zeitlich sehr nahe Treffer derselben Phase zusammenfassen
        result.sort((a, b) -> a.getDateTime().compareTo(b.getDateTime()));
        List<MoonEvent> cleaned = new ArrayList<>();

        for (MoonEvent ev : result) {
            if (cleaned.isEmpty()) {
                cleaned.add(ev);
            } else {
                MoonEvent last = cleaned.get(cleaned.size() - 1);
                long diffHours = ChronoUnit.HOURS.between(last.getDateTime(), ev.getDateTime());

                if (ev.getPhase() == last.getPhase() && Math.abs(diffHours) < 6) {
                    // Sehr nah beieinander: wir wählen den Mittelwert der Zeitpunkte
                    long diffSeconds = ChronoUnit.SECONDS.between(last.getDateTime(), ev.getDateTime());
                    LocalDateTime mid = last.getDateTime().plusSeconds(diffSeconds / 2);
                    cleaned.set(cleaned.size() - 1, new MoonEvent(mid, ev.getPhase()));
                } else {
                    cleaned.add(ev);
                }
            }
        }

        return cleaned;
    }

    /**
     * Prüft an einem groben Rasterzeitpunkt dt, ob die Mondphase in der Nähe
     * der gewünschten Phase liegt. Falls ja, wird in einem kleineren Umfeld
     * mit stündlichen Schritten nach dem Minimum der Phasenabweichung gesucht.
     */
    private void checkAndAdd(List<MoonEvent> result,
                             LocalDateTime dt,
                             Phase phase,
                             double targetPhase,
                             double tol) {

        double phaseValue = phaseValue(dt);
        double diff = phaseDistance(phaseValue, targetPhase);

        if (diff <= tol) {
            // Feinsuche in einem kleineren Zeitfenster, z. B. +/- 1 Tag um dt
            LocalDateTime best = refinePhaseTime(dt.minusDays(1), dt.plusDays(1), targetPhase);

            if (best != null) {
                result.add(new MoonEvent(best, phase));
            }
        }
    }

    /**
     * Verfeinert die Suche innerhalb eines Zeitfensters, indem mit
     * 1-Stunden-Schritten das Minimum der Phasenabweichung gesucht wird.
     *
     * @param from        Start des Zeitfensters
     * @param to          Ende des Zeitfensters
     * @param targetPhase Zielphasenwert (0.0, 0.25, 0.5, 0.75)
     * @return bester gefundener Zeitpunkt oder null, wenn kein Treffer im
     *         Toleranzbereich gefunden wurde
     */
    private LocalDateTime refinePhaseTime(LocalDateTime from,
                                          LocalDateTime to,
                                          double targetPhase) {

        LocalDateTime bestTime = null;
        double bestDiff = Double.MAX_VALUE;

        for (LocalDateTime t = from; !t.isAfter(to); t = t.plusHours(1)) {
            double p = phaseValue(t);
            double d = phaseDistance(p, targetPhase);

            if (d < bestDiff) {
                bestDiff = d;
                bestTime = t;
            }
        }

        if (bestTime != null && bestDiff <= tolerancePhase) {
            return bestTime;
        }
        return null;
    }

    /**
     * Berechnet den Phasenwert im Bereich [0..1) für den Zeitpunkt t.
     *
     * 0   = Neumond, 0.5 = Vollmond, etc.
     */
    private double phaseValue(LocalDateTime t) {
        double minutes = ChronoUnit.MINUTES.between(referenceNewMoon, t);
        double daysSinceRef = minutes / (60.0 * 24.0);
        double cycles = daysSinceRef / synodicMonthDays;
        double frac = cycles - Math.floor(cycles);

        if (frac < 0) {
            frac += 1.0;
        }
        return frac;
    }

    /**
     * Berechnet die "kreisförmige" Distanz zwischen zwei Phasenwerten
     * im Bereich [0..1).
     */
    private double phaseDistance(double a, double b) {
        double diff = Math.abs(a - b);
        return Math.min(diff, 1.0 - diff);
    }
}
