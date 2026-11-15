package com.naghma.accounts;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.mindrot.jbcrypt.BCrypt;

@Path("/api/auth")
public class AuthResource {

    @Inject
    TokenService tokens;

    @Inject
    JsonWebToken jwt;

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 80) String displayName,
            @Pattern(regexp = "en|ar") String locale) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record ProfileUpdate(@Size(max = 80) String displayName, @Pattern(regexp = "en|ar") String locale) {}

    public record UserView(UUID id, String email, String displayName, Tier tier, String locale,
                           int monthlyTrackQuota, boolean setBuilder) {
        static UserView of(User u) {
            return new UserView(u.id, u.email, u.displayName, u.tier, u.locale,
                    u.tier.monthlyTrackQuota(), u.tier.setBuilder());
        }
    }

    public record AuthResponse(String token, UserView user) {}

    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(@Valid RegisterRequest req) {
        String email = User.normalize(req.email());
        if (User.findByEmail(email).isPresent()) {
            throw new WebApplicationException("An account with this email already exists", 409);
        }
        User u = new User();
        u.id = UUID.randomUUID();
        u.email = email;
        u.passwordHash = BCrypt.hashpw(req.password(), BCrypt.gensalt(12));
        u.displayName = req.displayName().trim();
        u.locale = req.locale() == null ? "en" : req.locale();
        u.createdAt = Instant.now();
        u.persist();
        return Response.status(201).entity(new AuthResponse(tokens.issue(u), UserView.of(u))).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    public AuthResponse login(@Valid LoginRequest req) {
        User u = User.findByEmail(req.email())
                .filter(user -> BCrypt.checkpw(req.password(), user.passwordHash))
                // same message for unknown email and wrong password
                .orElseThrow(() -> new WebApplicationException("Invalid email or password", 401));
        return new AuthResponse(tokens.issue(u), UserView.of(u));
    }

    @GET
    @Path("/me")
    @RolesAllowed("user")
    public UserView me() {
        return UserView.of(current());
    }

    /** New token with fresh claims, e.g. after an upgrade changed the tier. */
    @POST
    @Path("/refresh")
    @RolesAllowed("user")
    public AuthResponse refresh() {
        User u = current();
        return new AuthResponse(tokens.issue(u), UserView.of(u));
    }

    @PUT
    @Path("/me")
    @RolesAllowed("user")
    @Transactional
    public AuthResponse updateProfile(@Valid ProfileUpdate req) {
        User u = current();
        if (req.displayName() != null && !req.displayName().isBlank()) {
            u.displayName = req.displayName().trim();
        }
        if (req.locale() != null) {
            u.locale = req.locale();
        }
        return new AuthResponse(tokens.issue(u), UserView.of(u));
    }

    private User current() {
        User u = User.findById(UUID.fromString(jwt.getSubject()));
        if (u == null) {
            throw new NotFoundException("User not found");
        }
        return u;
    }
}
