package com.naghma.accounts.billing;

import com.naghma.accounts.Tier;
import com.naghma.accounts.User;
import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/billing")
public class BillingResource {

    @Inject
    PaymentGateway gateway;

    @Inject
    JsonWebToken jwt;

    public record CheckoutRequest(@NotNull Tier tier, String returnUrl) {}

    @POST
    @Path("/checkout")
    @RolesAllowed("user")
    public PaymentGateway.Checkout checkout(CheckoutRequest req) {
        if (!req.tier().isPaid()) {
            throw new BadRequestException("Choose a paid tier");
        }
        return gateway.createCheckout(UUID.fromString(jwt.getSubject()), req.tier(), req.returnUrl());
    }

    /** Provider webhook. Idempotent: replaying the same event leaves the same tier. */
    @POST
    @Path("/webhook")
    @PermitAll
    @Transactional
    public Response webhook(String body, @HeaderParam("X-Signature") String signature) {
        PaymentGateway.PaymentEvent event = gateway.parseWebhook(body, signature);
        if (event.paid()) {
            User u = User.findById(event.userId());
            if (u == null) {
                return Response.status(404).build();
            }
            u.tier = event.tier();
            Log.infof("user %s upgraded to %s (ref %s)", u.id, u.tier, event.reference());
        }
        return Response.noContent().build();
    }

    /** Dev-only stand-in for the provider's hosted payment page. */
    @GET
    @Path("/dev-pay")
    @PermitAll
    @Transactional
    public Response devPay(@QueryParam("payload") String payload, @QueryParam("sig") String sig,
                           @QueryParam("returnUrl") String returnUrl) {
        webhook(payload, sig);
        return Response.seeOther(URI.create(returnUrl == null || !returnUrl.startsWith("/") ? "/" : returnUrl)).build();
    }
}
