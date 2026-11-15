package com.naghma.catalog.api;

import com.naghma.catalog.domain.UploadPolicy;
import com.naghma.catalog.jobs.JobService;
import com.naghma.catalog.model.Track;
import com.naghma.catalog.storage.StorageService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Two-step upload: (1) the client asks for presigned URLs, sending each file's SHA-256 so
 * duplicates are caught before any bytes move; (2) after PUTting the bytes it confirms,
 * and the track is queued for analysis.
 */
@Path("/api/uploads")
@RolesAllowed("user")
public class UploadResource {

    @Inject
    StorageService storage;

    @Inject
    JobService jobs;

    @Inject
    JsonWebToken jwt;

    public record FileRequest(String filename, long sizeBytes, String sha256, String title, String artist) {}

    public record UploadRequest(@NotEmpty @Size(max = UploadPolicy.MAX_BATCH) List<@Valid FileRequest> files,
                                boolean keepAudio) {}

    public record UploadSlot(String filename, UUID trackId, boolean duplicate, String uploadUrl, String contentType) {}

    public record UploadResponse(List<UploadSlot> slots, int quotaRemaining) {}

    public record CompleteRequest(@NotEmpty @Size(max = UploadPolicy.MAX_BATCH) List<UUID> trackIds) {}

    public record CompleteResult(UUID trackId, String status, String error) {}

    @POST
    @Transactional
    public UploadResponse start(@Valid UploadRequest req) {
        UploadPolicy.validateBatch(specs(req)).ifPresent(m -> {
            throw new BadRequestException(m);
        });
        for (UploadPolicy.FileSpec f : specs(req)) {
            UploadPolicy.validate(f).ifPresent(m -> {
                throw new BadRequestException(m);
            });
        }
        UUID owner = owner();
        String tier = jwt.getClaim("tier");
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long used = Track.createdSince(owner, monthStart);

        // Slots are returned in request order so the client can match them by index.
        int n = req.files().size();
        UploadSlot[] slots = new UploadSlot[n];
        List<Integer> fresh = new ArrayList<>();
        Set<String> seenInBatch = new HashSet<>();
        for (int i = 0; i < n; i++) {
            FileRequest f = req.files().get(i);
            var existing = Track.byHash(owner, f.sha256());
            if (existing.isPresent() || !seenInBatch.add(f.sha256())) {
                slots[i] = new UploadSlot(f.filename(), existing.map(t -> t.id).orElse(null), true, null, null);
            } else {
                fresh.add(i);
            }
        }
        int allowed = UploadPolicy.allowed(tier, used, fresh.size());
        if (allowed < fresh.size()) {
            throw new ClientErrorException("Monthly analysis quota reached: " + allowed + " of " + fresh.size()
                    + " new tracks fit in your plan this month. Upgrade to analyze more.", 402);
        }
        for (int i : fresh) {
            FileRequest f = req.files().get(i);
            String ext = UploadPolicy.extension(f.filename()).orElseThrow();
            String[] guess = UploadPolicy.guessArtistTitle(f.filename());
            Track t = new Track();
            t.id = UUID.randomUUID();
            t.ownerId = owner;
            t.title = blank(f.title()) ? guess[1] : f.title().trim();
            t.artist = blank(f.artist()) ? guess[0] : f.artist().trim();
            t.originalFilename = f.filename();
            t.contentHash = f.sha256();
            t.sizeBytes = f.sizeBytes();
            t.contentType = UploadPolicy.EXTENSIONS.get(ext);
            t.objectKey = owner + "/" + t.id + "." + ext;
            t.status = Track.Status.AWAITING_UPLOAD;
            t.keepAudio = req.keepAudio();
            t.createdAt = Instant.now();
            t.persist();
            slots[i] = new UploadSlot(f.filename(), t.id, false,
                    storage.presignPut(t.objectKey, t.contentType, t.sizeBytes), t.contentType);
        }
        int quota = UploadPolicy.monthlyQuota(tier);
        int remaining = quota < 0 ? -1 : (int) Math.max(0, quota - used - fresh.size());
        return new UploadResponse(List.of(slots), remaining);
    }

    /** Confirm uploads; each confirmed track is queued for analysis. */
    @POST
    @Path("/complete")
    @Transactional
    public List<CompleteResult> complete(@Valid CompleteRequest req) {
        UUID owner = owner();
        List<CompleteResult> out = new ArrayList<>();
        for (UUID id : req.trackIds()) {
            var track = Track.owned(id, owner);
            if (track.isEmpty()) {
                out.add(new CompleteResult(id, "NOT_FOUND", "Track not found"));
                continue;
            }
            Track t = track.get();
            if (t.status != Track.Status.AWAITING_UPLOAD) {
                out.add(new CompleteResult(id, t.status.name(), null)); // idempotent confirm
                continue;
            }
            long size = storage.size(t.objectKey);
            if (size < 0) {
                out.add(new CompleteResult(id, t.status.name(), "File not uploaded yet"));
                continue;
            }
            if (size != t.sizeBytes) {
                out.add(new CompleteResult(id, t.status.name(), "Uploaded size does not match"));
                continue;
            }
            jobs.enqueue(t);
            out.add(new CompleteResult(id, t.status.name(), null));
        }
        return out;
    }

    private static List<UploadPolicy.FileSpec> specs(UploadRequest req) {
        return req.files() == null ? List.of()
                : req.files().stream().map(f -> new UploadPolicy.FileSpec(f.filename(), f.sizeBytes(), f.sha256())).toList();
    }

    private UUID owner() {
        return UUID.fromString(jwt.getSubject());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
