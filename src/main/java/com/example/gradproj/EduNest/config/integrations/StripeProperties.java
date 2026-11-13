package com.example.gradproj.EduNest.config.integrations;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * [ADD-06] / EB-108 / P4-18 — Stripe enrollment-payment configuration (D-28).
 *
 * Binds the {@code STRIPE_*} environment variables (drop-in ready: Omar
 * provisions the Stripe account → test keys land in the env store → flip
 * {@code STRIPE_ENABLED}). No values are ever hardcoded; secrets go to the
 * env store only (never git), rotation per P6-03.
 *
 * Omar-side provisioning checklist (Stripe Dashboard, TEST MODE first — D-28):
 * <ol>
 *   <li>Developers → API keys → copy the <b>Secret key</b> ({@code sk_test_...})
 *       → STRIPE_SECRET_KEY.</li>
 *   <li>Developers → Webhooks → add endpoint {@code <base>/api/v1/payments/webhook}
 *       → copy the <b>Signing secret</b> ({@code whsec_...}) → STRIPE_WEBHOOK_SECRET.
 *       Locally, {@code stripe listen} prints an equivalent temporary secret.</li>
 *   <li>Set STRIPE_SUCCESS_URL / STRIPE_CANCEL_URL to the FE landing routes and
 *       flip STRIPE_ENABLED=true.</li>
 * </ol>
 *
 * Disposition contract (mirrors {@link com.example.gradproj.EduNest.service.integration.zoom.DisabledZoomMeetingClient}):
 * {@code enabled=false} (default) → the mentorship-join flow keeps the legacy
 * direct-enrollment behavior (C decision, 2026-11-12) and the disabled gateway
 * bean makes zero external calls; {@code enabled=true} without credentials is
 * possible during bring-up and the beans fail fast at call time naming exactly
 * who owns the missing prerequisite; {@code enabled=true} + configured swaps in
 * the real SDK client.
 *
 * Free Tier Bypass (Omar, 2026-11-12): the gateway is invoked ONLY when the
 * server-computed effective amount is &gt; 0 — a $0 mentorship joins instantly
 * through the legacy path and never reaches Stripe.
 */
@Component
@ConfigurationProperties(prefix = "app.stripe")
@Getter
@Setter
public class StripeProperties {

    /**
     * Master switch — false keeps the disabled gateway bean active, zero
     * external calls, and the legacy free-join behavior in /join (C decision).
     */
    private boolean enabled = false;

    /** Stripe Secret key (test mode first: sk_test_...). Env store only, never git. */
    private String secretKey = "";

    /** Webhook endpoint signing secret (whsec_...). Env store only, never git. */
    private String webhookSecret = "";

    /**
     * Absolute FE landing URL Stripe redirects to after a successful payment.
     * Required for Checkout Session creation.
     */
    private String successUrl = "";

    /** Absolute FE landing URL Stripe redirects to on cancel/abandon. */
    private String cancelUrl = "";

    /** ISO-4217 currency — hardcoded USD for this phase (E decision, 2026-11-12). */
    private String currency = "USD";

    /** Whether all credentials/URLs the money path needs are present. */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank()
                && webhookSecret != null && !webhookSecret.isBlank()
                && successUrl != null && !successUrl.isBlank()
                && cancelUrl != null && !cancelUrl.isBlank();
    }
}
