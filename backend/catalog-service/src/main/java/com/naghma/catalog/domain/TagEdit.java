package com.naghma.catalog.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies a DJ's tag correction (FR-3.4) to the current tags and reports exactly what
 * changed, so every change can be stored as training feedback and protected from being
 * overwritten by a later re-analysis.
 */
public final class TagEdit {

    /** The editable tags of a track. */
    public record Tags(String title, String artist, Double bpm, String tonality, Integer keyTonicPc, String keyMode,
                       String camelot, String maqam, Double tonicCents, String tonicNote) {}

    /** A requested change; null fields are left alone. */
    public record Request(String title, String artist, Double bpm, String tonality, String keyTonic, String keyMode,
                          String maqam, String tonicNote) {}

    public record Change(String field, String oldValue, String newValue) {}

    public record Result(Tags tags, List<Change> changes, Set<String> editedFields) {}

    private TagEdit() {}

    public static Result apply(Tags cur, Request req, Set<String> previouslyEdited) {
        List<Change> changes = new ArrayList<>();
        Set<String> edited = new LinkedHashSet<>(previouslyEdited);

        String title = change("title", cur.title(), blankToNull(req.title()), changes, edited);
        String artist = change("artist", cur.artist(), blankToNull(req.artist()), changes, edited);

        Double bpm = cur.bpm();
        if (req.bpm() != null) {
            if (req.bpm() < 40 || req.bpm() > 250) {
                throw new IllegalArgumentException("bpm must be between 40 and 250");
            }
            bpm = changeD("bpm", cur.bpm(), Math.round(req.bpm() * 100.0) / 100.0, changes, edited);
        }

        String tonality = cur.tonality();
        if (req.tonality() != null) {
            if (!req.tonality().equals("WESTERN") && !req.tonality().equals("ARABIC_MODAL")) {
                throw new IllegalArgumentException("tonality must be WESTERN or ARABIC_MODAL");
            }
            tonality = change("tonality", cur.tonality(), req.tonality(), changes, edited);
        }

        Integer keyPc = cur.keyTonicPc();
        String keyMode = cur.keyMode();
        if (req.keyTonic() != null || req.keyMode() != null) {
            Integer newPc = keyPc;
            if (req.keyTonic() != null) {
                int cents = Notation.parseNoteCents(req.keyTonic());
                if (cents % 100 != 0) {
                    throw new IllegalArgumentException("A Western key tonic cannot be a quarter tone");
                }
                newPc = cents / 100;
            }
            String newMode = req.keyMode() == null ? keyMode : req.keyMode();
            if (!"major".equals(newMode) && !"minor".equals(newMode)) {
                throw new IllegalArgumentException("keyMode must be major or minor");
            }
            if (newPc == null) {
                throw new IllegalArgumentException("keyTonic is required");
            }
            String oldKey = keyLabel(keyPc, keyMode);
            String newKey = keyLabel(newPc, newMode);
            if (!Objects.equals(oldKey, newKey)) {
                changes.add(new Change("key", oldKey, newKey));
                edited.add("key");
            }
            keyPc = newPc;
            keyMode = newMode;
        }
        String camelot = keyPc == null || keyMode == null ? cur.camelot() : Notation.camelot(keyPc, keyMode);

        String maqam = cur.maqam();
        if (req.maqam() != null) {
            maqam = change("maqam", cur.maqam(), Notation.normalizeMaqam(req.maqam()), changes, edited);
        }

        Double tonicCents = cur.tonicCents();
        String tonicNote = cur.tonicNote();
        if (req.tonicNote() != null) {
            double cents = Notation.parseNoteCents(req.tonicNote());
            String name = Notation.quarterToneName(cents);
            if (!Objects.equals(name, cur.tonicNote())) {
                changes.add(new Change("tonic", cur.tonicNote(), name));
                edited.add("tonic");
            }
            tonicCents = cents;
            tonicNote = name;
        }
        if (req.maqam() != null && !"ARABIC_MODAL".equals(tonality) && req.tonality() == null) {
            // naming a maqam means the DJ hears it as Arabic-modal
            changes.add(new Change("tonality", tonality, "ARABIC_MODAL"));
            edited.add("tonality");
            tonality = "ARABIC_MODAL";
        }

        Tags tags = new Tags(title, artist, bpm, tonality, keyPc, keyMode, camelot, maqam, tonicCents, tonicNote);
        return new Result(tags, changes, edited);
    }

    private static String keyLabel(Integer pc, String mode) {
        return pc == null || mode == null ? null : Notation.noteName(pc) + " " + mode;
    }

    private static String change(String field, String old, String requested, List<Change> changes, Set<String> edited) {
        if (requested == null || requested.equals(old)) {
            return old;
        }
        changes.add(new Change(field, old, requested));
        edited.add(field);
        return requested;
    }

    private static Double changeD(String field, Double old, Double requested, List<Change> changes, Set<String> edited) {
        if (Objects.equals(old, requested)) {
            return old;
        }
        changes.add(new Change(field, old == null ? null : old.toString(), requested.toString()));
        edited.add(field);
        return requested;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
