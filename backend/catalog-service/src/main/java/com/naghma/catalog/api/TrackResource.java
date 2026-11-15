package com.naghma.catalog.api;

import com.naghma.catalog.domain.TagEdit;
import com.naghma.catalog.jobs.JobService;
import com.naghma.catalog.model.Analysis;
import com.naghma.catalog.model.AnalysisJob;
import com.naghma.catalog.model.Correction;
import com.naghma.catalog.model.Track;
import com.naghma.catalog.storage.StorageService;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/tracks")
@RolesAllowed("user")
public class TrackResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    StorageService storage;

    @Inject
    JobService jobs;

    @GET
    public Views.Page<Views.TrackView> list(@QueryParam("status") Track.Status status,
                                            @QueryParam("q") String q,
                                            @QueryParam("tonality") String tonality,
                                            @QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("50") int size) {
        if (size < 1 || size > 200 || page < 0) {
            throw new BadRequestException("page >= 0 and 1 <= size <= 200");
        }
        StringBuilder where = new StringBuilder("ownerId = :owner");
        Parameters p = Parameters.with("owner", owner());
        if (status != null) {
            where.append(" and status = :status");
            p.and("status", status);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (lower(title) like :q or lower(artist) like :q)");
            p.and("q", "%" + q.trim().toLowerCase() + "%");
        }
        if (tonality != null && !tonality.isBlank()) {
            where.append(" and id in (select a.trackId from Analysis a where a.tonality = :tonality)");
            p.and("tonality", tonality);
        }
        var query = Track.<Track>find(where.toString(), Sort.descending("createdAt"), p).page(Page.of(page, size));
        List<Track> tracks = query.list();
        Map<UUID, Analysis> analyses = analysesFor(tracks.stream().map(t -> t.id).toList());
        List<Views.TrackView> items = tracks.stream().map(t -> Views.TrackView.of(t, analyses.get(t.id))).toList();
        return new Views.Page<>(items, query.count(), page, size);
    }

    @GET
    @Path("/{id}")
    public Views.TrackDetail get(@PathParam("id") UUID id) {
        Track t = owned(id);
        Analysis a = Analysis.findById(id);
        return new Views.TrackDetail(Views.TrackView.of(t, a),
                a == null ? List.of() : a.energyCurve,
                a == null ? List.of() : a.maqamCandidates,
                a == null ? null : a.tonicHz,
                a == null ? null : a.tonicCents,
                a == null ? null : a.quarterToneRatio,
                a == null ? List.of() : a.pitchHistogram,
                a == null ? null : a.modelVersion);
    }

    /** Features of analyzed tracks for the mix service; all of them or the given ids. */
    @GET
    @Path("/features")
    public List<Views.Features> features(@QueryParam("ids") String ids) {
        List<Track> tracks;
        if (ids == null || ids.isBlank()) {
            tracks = Track.list("ownerId = ?1 and status = ?2", owner(), Track.Status.DONE);
        } else {
            List<UUID> wanted;
            try {
                wanted = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("ids must be comma-separated UUIDs");
            }
            if (wanted.size() > 500) {
                throw new BadRequestException("at most 500 ids");
            }
            tracks = Track.list("ownerId = ?1 and status = ?2 and id in ?3", owner(), Track.Status.DONE, wanted);
        }
        Map<UUID, Analysis> analyses = analysesFor(tracks.stream().map(t -> t.id).toList());
        return tracks.stream().filter(t -> analyses.containsKey(t.id))
                .map(t -> Views.Features.of(t, analyses.get(t.id))).toList();
    }

    /** Correct tags; every change is stored as feedback for retraining (FR-3.4). */
    @PATCH
    @Path("/{id}/tags")
    @Transactional
    public Views.TrackView editTags(@PathParam("id") UUID id, TagEdit.Request req) {
        Track t = owned(id);
        Analysis a = Analysis.findById(id);
        if (a == null) {
            throw new ClientErrorException("Track is not analyzed yet", 409);
        }
        TagEdit.Tags current = new TagEdit.Tags(t.title, t.artist, a.bpm, a.tonality, a.keyTonicPc, a.keyMode,
                a.camelot, a.maqam, a.tonicCents, a.tonicNote);
        TagEdit.Result r;
        try {
            r = TagEdit.apply(current, req, a.editedFields == null ? Set.of() : a.editedFields);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        TagEdit.Tags n = r.tags();
        t.title = n.title();
        t.artist = n.artist();
        a.bpm = n.bpm();
        a.tonality = n.tonality();
        a.keyTonicPc = n.keyTonicPc();
        a.keyMode = n.keyMode();
        a.camelot = n.camelot();
        a.maqam = n.maqam();
        a.tonicCents = n.tonicCents();
        a.tonicNote = n.tonicNote();
        if (r.editedFields().contains("maqam")) {
            a.maqamUncertain = false; // a person confirmed it
        }
        a.editedFields = r.editedFields();
        a.updatedAt = Instant.now();
        for (TagEdit.Change c : r.changes()) {
            Correction k = new Correction();
            k.id = UUID.randomUUID();
            k.trackId = t.id;
            k.userId = owner();
            k.field = c.field();
            k.oldValue = c.oldValue();
            k.newValue = c.newValue();
            k.modelVersion = a.modelVersion;
            k.createdAt = Instant.now();
            k.persist();
        }
        return Views.TrackView.of(t, a);
    }

    /** Re-run analysis; only possible while the audio is still stored. */
    @POST
    @Path("/{id}/reanalyze")
    @Transactional
    public Views.TrackView reanalyze(@PathParam("id") UUID id) {
        Track t = owned(id);
        if (t.status == Track.Status.QUEUED || t.status == Track.Status.AWAITING_UPLOAD) {
            throw new ClientErrorException("Track is already waiting for analysis", 409);
        }
        if (storage.size(t.objectKey) < 0) {
            throw new ClientErrorException("Audio was deleted after analysis; upload the file again to re-analyze", 409);
        }
        AnalysisJob.update("status = ?1 where trackId = ?2 and status = ?3",
                AnalysisJob.Status.FAILED, t.id, AnalysisJob.Status.PENDING);
        jobs.enqueue(t);
        return Views.TrackView.of(t, Analysis.findById(id));
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        Track t = owned(id);
        Analysis.deleteById(id);
        Correction.delete("trackId", id);
        AnalysisJob.delete("trackId", id);
        t.delete();
        storage.deleteQuietly(t.objectKey);
        return Response.noContent().build();
    }

    @GET
    @Path("/stats")
    public Map<String, Object> stats() {
        UUID owner = owner();
        Map<String, Object> out = new HashMap<>();
        for (Track.Status s : Track.Status.values()) {
            out.put(s.name(), Track.count("ownerId = ?1 and status = ?2", owner, s));
        }
        return out;
    }

    private Map<UUID, Analysis> analysesFor(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Analysis> list = Analysis.list("trackId in ?1", ids);
        return list.stream().collect(Collectors.toMap(a -> a.trackId, a -> a));
    }

    private Track owned(UUID id) {
        return Track.owned(id, owner()).orElseThrow(() -> new NotFoundException("Track not found"));
    }

    private UUID owner() {
        return UUID.fromString(jwt.getSubject());
    }
}
