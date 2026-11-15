package com.naghma.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CatalogDomainTest {

    static final String HASH = "a".repeat(64);

    @ParameterizedTest
    @CsvSource({"x.MP3,100,true", "x.ogg,100,false", "x.wav,32505856,false", "noext,100,false", "x.flac,0,false"})
    void uploadValidation(String name, long size, boolean ok) {
        assertEquals(ok, UploadPolicy.validate(new UploadPolicy.FileSpec(name, size, HASH)).isEmpty());
    }

    @Test
    void badHashIsRejected() {
        assertTrue(UploadPolicy.validate(new UploadPolicy.FileSpec("x.wav", 10, "zz")).isPresent());
    }

    @Test
    void quotas() {
        assertEquals(2, UploadPolicy.allowed("FREE", 48, 5));
        assertEquals(500, UploadPolicy.allowed("PRO", 100_000, 500));
        assertEquals(50, UploadPolicy.allowed("unknown", 0, 60));
        assertEquals(0, UploadPolicy.allowed("DJ", 1_600, 3));
    }

    @Test
    void artistAndTitleFromFilename() {
        assertArrayEquals(new String[] {"Amr Diab", "Tamally Maak"}, UploadPolicy.guessArtistTitle("Amr Diab - Tamally Maak.mp3"));
        assertArrayEquals(new String[] {null, "track 01"}, UploadPolicy.guessArtistTitle("music/track_01.wav"));
    }

    @Test
    void notation() {
        assertEquals(350, Notation.parseNoteCents("E half-flat"));
        assertEquals(600, Notation.parseNoteCents("F#"));
        assertEquals(1000, Notation.parseNoteCents("Bb"));
        assertEquals("8A", Notation.camelot(9, "minor"));
        assertEquals("B half-flat", Notation.quarterToneName(1050));
    }

    static final TagEdit.Tags AM = new TagEdit.Tags("t", "a", 120.0, "WESTERN", 9, "minor", "8A", null, null, null);

    @Test
    void keyAndBpmEditRecomputesCamelotAndRecordsChanges() {
        var r = TagEdit.apply(AM, new TagEdit.Request(null, null, 122.0, null, "C", "major", null, null), Set.of());
        assertEquals("8B", r.tags().camelot());
        assertEquals(122.0, r.tags().bpm());
        assertEquals(2, r.changes().size());
        assertTrue(r.editedFields().containsAll(Set.of("bpm", "key")));
    }

    @Test
    void namingAMaqamMarksTheTrackArabicModal() {
        var r = TagEdit.apply(AM, new TagEdit.Request(null, null, null, null, null, null, "bayati", "D"), Set.of("bpm"));
        assertEquals("Bayati", r.tags().maqam());
        assertEquals("ARABIC_MODAL", r.tags().tonality());
        assertEquals(200.0, r.tags().tonicCents());
        assertTrue(r.editedFields().contains("bpm"));
    }

    @Test
    void noOpEditRecordsNothing() {
        assertTrue(TagEdit.apply(AM, new TagEdit.Request("t", null, 120.0, null, null, null, null, null), Set.of())
                .changes().isEmpty());
    }

    @Test
    void invalidEditsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TagEdit.apply(AM, new TagEdit.Request(null, null, null, null, "E half-flat", "minor", null, null), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> TagEdit.apply(AM, new TagEdit.Request(null, null, null, null, null, null, "Zzz", null), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> TagEdit.apply(AM, new TagEdit.Request(null, null, 400.0, null, null, null, null, null), Set.of()));
    }
}
