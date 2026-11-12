package com.example.gradproj.EduNest.service.integration.stripe;

import com.example.gradproj.EduNest.config.integrations.StripeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link StripePaymentGateway} bean while Stripe is disabled
 * ({@code app.stripe.enabled=false} or absent — the {@code C} decision keeps
 * the legacy free-join behavior on /join in this state).
 *
 * Boot-safety contract: constructing this bean NEVER fails and performs ZERO
 * network calls (the app builds with STRIPE_ENABLED absent entirely).
 * Call-time contract: both methods fail fast with a message naming exactly who
 * owns the missing prerequisite. If the join flow is wired correctly this bean
 * is never called while disabled — hitting it is a wiring regression.
 */
@Component
@ConditionalOnProperty(prefix = "app.stripe", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledStripePaymentGateway implements StripePaymentGateway {

    private final StripeProperties props;

    @Autowired
    public DisabledStripePaymentGateway(StripeProperties props) {
        this.props = props;
    }

    @Override
    public StripeCheckoutSession createCheckoutSession(StripeCheckoutRequest request) {
        throw guard("checkout session create");
    }

    @Override
    public StripeEventEnvelope verifyAndParseWebhook(String rawPayload, String signatureHeader) {
        throw guard("webhook verification");
    }

    private IllegalStateException guard(String operation) {
        if (!props.isEnabled()) {
            return new IllegalStateException(
                    "Stripe integration is disabled (app.stripe.enabled=false). Provision the "
                            + "Stripe test keys per [ADD-06]/EB-108 (checklist on StripeProperties), "
                            + "then flip STRIPE_ENABLED=true. (attempted operation: stripe "
                            + operation + ")");
        }
        return new IllegalStateException(
                "Stripe gateway regression: integration is enabled but the disabled bean is wired "
                        + "- the StripePaymentGateway bean must have been swapped for the SDK client. "
                        + "(attempted operation: stripe " + operation + ")");
    }
}
