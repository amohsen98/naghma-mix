package com.naghma.catalog.api;

import com.naghma.catalog.model.Correction;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.UUID;

/** Review queue for DJ corrections before they become training labels. */
@Path("/api/admin/corrections")
@RolesAllowed("admin")
public class AdminResource {

    @GET
    public List<Correction> list(@QueryParam("reviewed") @DefaultValue("false") boolean reviewed,
                                 @QueryParam("field") String field) {
        if (field != null && !field.isBlank()) {
            return Correction.list("reviewed = ?1 and field = ?2", Sort.descending("createdAt"), reviewed, field);
        }
        return Correction.list("reviewed", Sort.descending("createdAt"), reviewed);
    }

    @PUT
    @Path("/{id}/review")
    @Transactional
    public Correction review(@PathParam("id") UUID id) {
        Correction c = Correction.findById(id);
        if (c == null) {
            throw new NotFoundException();
        }
        c.reviewed = true;
        return c;
    }
}
