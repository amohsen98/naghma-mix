package com.naghma.catalog.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Upload rules from FR-1.1 and the tier quotas. Pure logic, no framework types. */
public final class UploadPolicy {

    public static final long MAX_FILE_BYTES = 30L * 1024 * 1024;
    public static final int MAX_BATCH = 500;
    public static final Map<String, String> EXTENSIONS = Map.of(
            "mp3", "audio/mpeg",
            "wav", "audio/wav",
            "flac", "audio/flac",
            "m4a", "audio/mp4");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** Monthly analysis quota per tier; -1 = unlimited. Mirrors accounts-service Tier. */
    private static final Map<String, Integer> QUOTA = Map.of("FREE", 50, "DJ", 1_500, "PRO", -1, "AGENCY", -1);

    private UploadPolicy() {}

    public record FileSpec(String filename, long sizeBytes, String sha256) {}

    /** @return an error message, or empty when the file is acceptable */
    public static Optional<String> validate(FileSpec f) {
        if (f.filename() == null || f.filename().isBlank()) {
            return Optional.of("filename is required");
        }
        if (extension(f.filename()).isEmpty()) {
            return Optional.of(f.filename() + ": only MP3, WAV, FLAC and M4A are supported");
        }
        if (f.sizeBytes() <= 0 || f.sizeBytes() > MAX_FILE_BYTES) {
            return Optional.of(f.filename() + ": files must be between 1 byte and 30 MB");
        }
        if (f.sha256() == null || !SHA256.matcher(f.sha256()).matches()) {
            return Optional.of(f.filename() + ": sha256 must be 64 lowercase hex characters");
        }
        return Optional.empty();
    }

    public static Optional<String> validateBatch(List<FileSpec> files) {
        if (files == null || files.isEmpty()) {
            return Optional.of("Add at least one file");
        }
        if (files.size() > MAX_BATCH) {
            return Optional.of("Upload at most " + MAX_BATCH + " files at a time");
        }
        return Optional.empty();
    }

    public static Optional<String> extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXTENSIONS.containsKey(ext) ? Optional.of(ext) : Optional.empty();
    }

    /** -1 when unlimited. Unknown tiers get the free quota. */
    public static int monthlyQuota(String tier) {
        return QUOTA.getOrDefault(tier == null ? "FREE" : tier, QUOTA.get("FREE"));
    }

    /** How many of {@code requested} new tracks fit in the remaining quota. */
    public static int allowed(String tier, long usedThisMonth, int requested) {
        int quota = monthlyQuota(tier);
        if (quota < 0) {
            return requested;
        }
        return (int) Math.max(0, Math.min(requested, quota - usedThisMonth));
    }

    /** "Ahmed - Song (Live).mp3" → title guess "Song (Live)", artist "Ahmed". */
    public static String[] guessArtistTitle(String filename) {
        String base = filename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replace('_', ' ').trim();
        int sep = base.indexOf(" - ");
        if (sep > 0) {
            return new String[] {base.substring(0, sep).trim(), base.substring(sep + 3).trim()};
        }
        return new String[] {null, base};
    }
}
