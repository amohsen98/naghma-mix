package com.naghma.mix.catalog;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Reads track features from the catalog service. The caller's JWT is forwarded, so the
 * catalog only ever returns the caller's own tracks.
 */
@RegisterRestClient(configKey = "catalog")
@Path("/api/tracks")
public interface CatalogClient {

    record EnergyPoint(double start, double end, double energy) {}

    record Features(
            UUID id,
            String title,
            String artist,
            String filename,
            Double durationSec,
            Double bpm,
            Double energy,
            List<EnergyPoint> energyCurve,
            String tonality,
            Integer keyTonicPc,
            String keyMode,
            String camelot,
            String maqam,
            Double tonicCents,
            Boolean maqamUncertain) {}

    /** All analyzed tracks of the caller, or only the given ids (comma-separated). */
    @GET
    @Path("/features")
    List<Features> features(@QueryParam("ids") String ids, @HeaderParam("Authorization") String authorization);
}
