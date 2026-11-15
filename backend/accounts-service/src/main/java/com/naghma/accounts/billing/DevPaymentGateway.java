package com.naghma.accounts.billing;

import com.naghma.accounts.Tier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Development gateway: checkout returns a local URL that confirms payment immediately.
 * Webhooks are signed with HMAC-SHA256 like most real providers, so the flow and its
 * security check are exercised end to end before a real provider is plugged in.
 */
@ApplicationScoped
public class DevPaymentGateway implements PaymentGateway {

    @ConfigProperty(name = "naghma.billing.webhook-secret")
    String secret;

    @ConfigProperty(name = "naghma.billing.public-url", defaultValue = "http://localhost:8080")
    String publicUrl;

    @Override
    public Checkout createCheckout(UUID userId, Tier tier, String returnUrl) {
        String reference = "dev_" + UUID.randomUUID();
        String body = reference + ":" + userId + ":" + tier.name() + ":paid";
        String url = publicUrl + "/api/billing/dev-pay?payload=" + enc(body) + "&sig=" + sign(body)
                + "&returnUrl=" + enc(returnUrl == null ? "/" : returnUrl);
        return new Checkout(url, reference);
    }

    @Override
    public PaymentEvent parseWebhook(String body, String signatureHeader) {
        if (signatureHeader == null || !MessageDigest.isEqual(
                sign(body).getBytes(StandardCharsets.UTF_8), signatureHeader.getBytes(StandardCharsets.UTF_8))) {
            throw new WebApplicationException("Invalid webhook signature", 401);
        }
        String[] p = body.split(":");
        if (p.length != 4) {
            throw new WebApplicationException("Malformed webhook body", 400);
        }
        return new PaymentEvent(p[0], UUID.fromString(p[1]), Tier.valueOf(p[2]), "paid".equals(p[3]));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
