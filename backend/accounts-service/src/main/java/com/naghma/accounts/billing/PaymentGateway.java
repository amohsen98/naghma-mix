package com.naghma.accounts.billing;

import com.naghma.accounts.Tier;
import java.util.UUID;

/**
 * Payment provider boundary. The production implementation should be a gateway that
 * supports Egyptian cards and wallets plus Gulf currencies (open question in the PRD);
 * card data must never reach our servers, so checkout always redirects to the provider.
 */
public interface PaymentGateway {

    record Checkout(String checkoutUrl, String reference) {}

    record PaymentEvent(String reference, UUID userId, Tier tier, boolean paid) {}

    Checkout createCheckout(UUID userId, Tier tier, String returnUrl);

    /** Verify the provider's signature and parse the webhook body. */
    PaymentEvent parseWebhook(String body, String signatureHeader);
}
