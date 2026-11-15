package com.naghma.mix.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MixDomainTest {

    static TrackFeatures western(String title, double bpm, int pc, String mode, double energy) {
        return new TrackFeatures(UUID.randomUUID(), title, "Artist", title + ".mp3", 200.0, bpm, energy,
                List.of(energy, energy), "WESTERN", pc, mode, Camelot.of(pc, mode).toString(), null, null, false);
    }

    static TrackFeatures arabic(String title, double bpm, String maqam, double tonicCents, double energy) {
        return new TrackFeatures(UUID.randomUUID(), title, "Artist", title + ".mp3", 240.0, bpm, energy,
                List.of(energy, energy), "ARABIC_MODAL", null, null, null, maqam, tonicCents, false);
    }

    @ParameterizedTest
    @CsvSource({"0,major,8B", "9,minor,8A", "7,major,9B", "4,minor,9A", "6,major,2B", "1,minor,12A"})
    void camelotNotation(int pc, String mode, String expected) {
        assertEquals(expected, Camelot.of(pc, mode).toString());
        assertEquals(Camelot.of(pc, mode), Camelot.parse(expected));
    }

    @Test
    void camelotWrapsAroundTheWheel() {
        assertEquals(1, Camelot.parse("12A").distance(Camelot.parse("1A")));
    }

    @Test
    void adjacentKeyBeatsTritone() {
        TrackFeatures c = western("c", 124, 0, "major", 0.6);
        double toG = TransitionScorer.score(c, western("g", 125, 7, "major", 0.6), 0, Weights.DEFAULT).score();
        double toFsharp = TransitionScorer.score(c, western("fs", 124, 6, "major", 0.6), 0, Weights.DEFAULT).score();
        assertTrue(toG > toFsharp);
    }

    @Test
    void sameMaqamSameTonicIsAPerfectHarmonicMatch() {
        TrackFeatures a = arabic("a", 100, "Bayati", 200, 0.5);
        TrackFeatures b = arabic("b", 101, "Bayati", 200, 0.5);
        assertEquals(1.0, TransitionScorer.score(a, b, 0, Weights.DEFAULT).components().get("harmony"));
    }

    @Test
    void sharedNotesCountQuarterTonesExactly() {
        TrackFeatures bayatiD = arabic("bayati", 100, "Bayati", 200, 0.5);
        // Rast on C: B half-flat vs Bayati's B-flat -> 6 of 7
        assertEquals(6.0 / 7, TransitionScorer.harmony(bayatiD, arabic("rast", 100, "Rast", 0, 0.5)).score(), 1e-9);
        // D minor: E vs Bayati's E half-flat -> 6 of 7 (the bridge between traditions)
        assertEquals(6.0 / 7, TransitionScorer.harmony(bayatiD, western("dmin", 100, 2, "minor", 0.5)).score(), 1e-9);
        // Hijaz on D: different 2nd and 3rd -> 5 of 7
        assertEquals(5.0 / 7, TransitionScorer.harmony(bayatiD, arabic("hijaz", 100, "Hijaz", 200, 0.5)).score(), 1e-9);
    }

    @Test
    void fifthIsACloseTonicRelation() {
        TrackFeatures c = western("c", 120, 0, "major", 0.5);
        assertEquals(0.8, TransitionScorer.tonicCloseness(c, western("g", 120, 7, "major", 0.5)));
        assertEquals(0.1, TransitionScorer.tonicCloseness(c, western("fs", 120, 6, "major", 0.5)));
    }

    @Test
    void halfTimeIsCompatible() {
        TrackFeatures fast = western("fast", 124, 0, "major", 0.6);
        TrackFeatures slow = western("slow", 62, 0, "major", 0.6);
        assertTrue(TransitionScorer.tempo(fast, slow).score() > 0.9);
    }

    @Test
    void energyCurveInterpolates() {
        assertEquals(0.30, EnergyCurve.WARM_UP.at(0, 11), 1e-9);
        assertEquals(0.65, EnergyCurve.WARM_UP.at(10, 11), 1e-9);
        assertEquals(0.40, EnergyCurve.WEDDING.at(50, 101), 1e-9); // slow-dance dip
    }

    @Test
    void setBuilderKeepsLocksPlacesEveryTrackAndBeatsInputOrder() {
        Random r = new Random(1);
        String[] maqamat = {"Rast", "Bayati", "Hijaz", "Saba", "Nahawand"};
        List<TrackFeatures> lib = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lib.add(i % 3 == 0
                    ? arabic("m" + i, 95 + r.nextInt(40), maqamat[r.nextInt(5)], r.nextInt(24) * 50, r.nextDouble())
                    : western("w" + i, 95 + r.nextInt(40), r.nextInt(12), r.nextBoolean() ? "major" : "minor", r.nextDouble()));
        }
        long t0 = System.nanoTime();
        SetBuilder.Result res = new SetBuilder(Weights.DEFAULT, EnergyCurve.WEDDING).build(lib, Map.of(lib.get(5).id(), 0));
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(lib.get(5).id(), res.order().getFirst().id());
        assertEquals(200, new HashSet<>(res.order()).size());
        assertTrue(ms < 3000, "took " + ms + " ms");

        double built = res.transitions().stream().mapToDouble(TransitionScorer.Transition::score).average().orElseThrow();
        double input = 0;
        for (int i = 0; i + 1 < lib.size(); i++) {
            input += TransitionScorer.score(lib.get(i), lib.get(i + 1), 0, Weights.DEFAULT).score();
        }
        input /= lib.size() - 1;
        assertTrue(built > input + 0.1, "built " + built + " vs input " + input);
    }

    @Test
    void rekordboxXmlIsEscaped() {
        String xml = PlaylistExport.rekordboxXml("Wedding <Fri> & co", List.of(western("a\"b", 120, 9, "minor", 0.5)));
        assertTrue(xml.contains("Name=\"Wedding &lt;Fri&gt; &amp; co\""));
        assertTrue(xml.contains("Name=\"a&quot;b\""));
        assertTrue(xml.contains("Tonality=\"Am\""));
    }

    @Test
    void noteNamesUseHalfFlats() {
        assertEquals("E half-flat", Scales.noteName(350));
        assertEquals("B half-flat", Scales.noteName(1050));
        assertEquals("D", Scales.noteName(200));
    }
}
