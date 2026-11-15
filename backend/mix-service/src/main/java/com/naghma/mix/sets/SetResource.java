package com.naghma.mix.sets;

import com.naghma.mix.api.Views;
import com.naghma.mix.catalog.FeatureSource;
import com.naghma.mix.domain.EnergyCurve;
import com.naghma.mix.domain.PlaylistExport;
import com.naghma.mix.domain.SetBuilder;
import com.naghma.mix.domain.TrackFeatures;
import com.naghma.mix.domain.TransitionScorer;
import com.naghma.mix.domain.Weights;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/sets")
@RolesAllowed("user")
public class SetResource {

    @Inject
    FeatureSource features;

    @Inject
    JsonWebToken jwt;

    public record WeightsDto(Double harmony, Double tonic, Double tempo, Double energy) {}

    /**
     * @param locked track id → 0-based position to keep fixed
     * @param keepOrder when true, save the given order as is (manual reordering) instead of optimizing
     */
    public record SetRequest(@NotBlank @Size(max = 100) String name,
                             @NotEmpty @Size(max = SetBuilder.MAX_TRACKS) List<UUID> trackIds,
                             String curve,
                             Map<UUID, Integer> locked,
                             WeightsDto weights,
                             boolean keepOrder) {}

    @GET
    public List<Views.SetListItem> list() {
        return DjSet.forOwner(owner()).stream()
                .map(s -> new Views.SetListItem(s.id, s.name, s.curve, s.trackIds.size(), s.updatedAt.toString()))
                .toList();
    }

    @POST
    @Transactional
    public Response create(@Valid SetRequest req) {
        requireSetBuilder();
        DjSet set = new DjSet();
        set.id = UUID.randomUUID();
        set.ownerId = owner();
        set.createdAt = Instant.now();
        Views.SetView view = apply(set, req);
        set.persist();
        return Response.status(201).entity(view).build();
    }

    @GET
    @Path("/{id}")
    public Views.SetView get(@PathParam("id") UUID id) {
        DjSet set = owned(id);
        List<TrackFeatures> order = present(set.trackIds);
        return view(set, order, new HashSet<>(set.lockedPositions));
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Views.SetView update(@PathParam("id") UUID id, @Valid SetRequest req) {
        requireSetBuilder();
        return apply(owned(id), req);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        owned(id).delete();
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/export")
    public Response export(@PathParam("id") UUID id, @QueryParam("format") String format) {
        DjSet set = owned(id);
        List<TrackFeatures> order = present(set.trackIds);
        String safeName = set.name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        if (safeName.isEmpty()) {
            safeName = "set";
        }
        return switch (format == null ? "" : format) {
            case "m3u" -> file(PlaylistExport.m3u(set.name, order), "audio/x-mpegurl", safeName + ".m3u");
            case "rekordbox" -> file(PlaylistExport.rekordboxXml(set.name, order), "application/xml", safeName + ".xml");
            default -> throw new BadRequestException("format must be m3u or rekordbox");
        };
    }

    private Views.SetView apply(DjSet set, SetRequest req) {
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(req.trackIds()));
        if (ids.size() != req.trackIds().size()) {
            throw new BadRequestException("A track can appear only once in a set");
        }
        EnergyCurve curve = parseCurve(req.curve());
        WeightsDto wd = req.weights() == null ? new WeightsDto(null, null, null, null) : req.weights();
        Weights w = weightsOrBadRequest(wd);
        Map<UUID, Integer> locked = req.locked() == null ? Map.of() : req.locked();

        List<TrackFeatures> tracks = ordered(ids);
        List<TrackFeatures> order;
        if (req.keepOrder()) {
            order = tracks;
        } else {
            try {
                order = new SetBuilder(w, curve).build(tracks, locked).order();
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
        }
        Map<UUID, Integer> positions = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            positions.put(order.get(i).id(), i);
        }
        set.name = req.name().trim();
        set.curve = curve.name();
        set.trackIds = order.stream().map(TrackFeatures::id).toList();
        set.lockedPositions = locked.keySet().stream().map(positions::get).sorted().toList();
        set.wHarmony = w.harmony();
        set.wTonic = w.tonic();
        set.wTempo = w.tempo();
        set.wEnergy = w.energy();
        set.updatedAt = Instant.now();
        return view(set, order, new HashSet<>(set.lockedPositions));
    }

    private Views.SetView view(DjSet set, List<TrackFeatures> order, java.util.Set<Integer> locked) {
        EnergyCurve curve = EnergyCurve.valueOf(set.curve);
        Weights w = new Weights(set.wHarmony, set.wTonic, set.wTempo, set.wEnergy);
        int n = order.size();
        List<Views.SetEntryView> entries = new ArrayList<>(n);
        double sum = 0;
        for (int i = 0; i < n; i++) {
            Views.TransitionView next = null;
            if (i + 1 < n) {
                TransitionScorer.Transition t = TransitionScorer.score(order.get(i), order.get(i + 1),
                        curve.at(i + 1, n) - curve.at(i, n), w);
                sum += t.score();
                next = Views.TransitionView.of(t);
            }
            entries.add(new Views.SetEntryView(i, locked.contains(i), TransitionScorer.round(curve.at(i, n)),
                    Views.TrackSummary.of(order.get(i)), next));
        }
        Map<String, Double> weights = Map.of("harmony", w.harmony(), "tonic", w.tonic(), "tempo", w.tempo(), "energy", w.energy());
        double avg = n > 1 ? TransitionScorer.round(sum / (n - 1)) : 0;
        return new Views.SetView(set.id, set.name, set.curve, weights, TransitionScorer.round(sum), avg, entries,
                set.createdAt == null ? null : set.createdAt.toString(), set.updatedAt.toString());
    }

    /** Features in the order of the given ids. */
    private List<TrackFeatures> ordered(List<UUID> ids) {
        Map<UUID, TrackFeatures> byId = features.byIds(ids).stream()
                .collect(Collectors.toMap(TrackFeatures::id, Function.identity()));
        return ids.stream().map(byId::get).toList();
    }

    /** Saved order, skipping tracks deleted since the set was saved. */
    private List<TrackFeatures> present(List<UUID> ids) {
        Map<UUID, TrackFeatures> byId = features.existing(ids).stream()
                .collect(Collectors.toMap(TrackFeatures::id, Function.identity()));
        return ids.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    private DjSet owned(UUID id) {
        DjSet set = DjSet.findById(id);
        if (set == null || !set.ownerId.equals(owner())) {
            throw new NotFoundException("Set not found");
        }
        return set;
    }

    private UUID owner() {
        return UUID.fromString(jwt.getSubject());
    }

    private void requireSetBuilder() {
        String tier = jwt.getClaim("tier");
        if (tier == null || "FREE".equals(tier)) {
            throw new ForbiddenException("The set builder is part of the DJ and Pro plans");
        }
    }

    private static EnergyCurve parseCurve(String curve) {
        if (curve == null || curve.isBlank()) {
            return EnergyCurve.WEDDING;
        }
        try {
            return EnergyCurve.valueOf(curve);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("curve must be one of WARM_UP, PEAK_TIME, WEDDING, WAVE, FLAT");
        }
    }

    private static Weights weightsOrBadRequest(WeightsDto d) {
        try {
            return Weights.of(d.harmony(), d.tonic(), d.tempo(), d.energy());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static Response file(String body, String type, String filename) {
        return Response.ok(body, type + "; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename*=UTF-8''"
                        + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
                .build();
    }
}
