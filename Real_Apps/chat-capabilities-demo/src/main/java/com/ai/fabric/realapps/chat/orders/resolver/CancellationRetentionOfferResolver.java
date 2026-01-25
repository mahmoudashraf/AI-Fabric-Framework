package com.ai.fabric.realapps.chat.orders.resolver;

import com.ai.fabric.realapps.chat.orders.action.OfferOrderDiscountActionHandler;
import com.ai.infrastructure.chat.resolver.ConfirmationResolverSupport;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.action.PendingAction;
import com.ai.infrastructure.intent.action.PendingActionStore;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * App-level interception example:
 * when user confirms cancelling an order, offer a retention discount first.
 *
 * <p>This is intentionally app-specific policy (not a framework default).</p>
 */
@Component
public class CancellationRetentionOfferResolver extends ConfirmationResolverSupport {

    private static final String INTERCEPT_ACTION = "cancel_purchase_order";
    private static final String OFFER_ACTION = OfferOrderDiscountActionHandler.ACTION_NAME;
    private static final String PARAM_RETENTION_OFFERED = "_retentionOfferOffered";

    public CancellationRetentionOfferResolver(PendingActionStore pendingActionStore) {
        super(pendingActionStore);
    }

    @Override
    public boolean canResolve(MultiIntentResponse intentResponse,
                             Map<String, Object> sessionMetadata,
                             PipelineContext context) {
        if (context == null || context.getOrchestrationContext() == null || !context.getOrchestrationContext().hasConversation()) {
            return false;
        }
        if (intentResponse == null || intentResponse.getIntents() == null || intentResponse.getIntents().isEmpty()) {
            return false;
        }
        if (intentResponse.isCompound() || intentResponse.getIntents().size() != 1) {
            return false;
        }

        PendingAction pending = peekPending(context);
        if (pending == null || !StringUtils.hasText(pending.action())) {
            return false;
        }

        Intent only = intentResponse.getIntents().getFirst();
        if (only == null || only.getType() == null) {
            return false;
        }

        // Intercept: user confirmed cancel_purchase_order -> offer discount (only once).
        if (only.getType() == IntentType.CONFIRMATION_POSITIVE && INTERCEPT_ACTION.equalsIgnoreCase(pending.action())) {
            return !wasOfferAlreadyAttempted(pending);
        }

        // Accept offer: user confirmed offer_order_discount -> execute offer and clear original cancel.
        if (only.getType() == IntentType.CONFIRMATION_POSITIVE && OFFER_ACTION.equalsIgnoreCase(pending.action())) {
            return true;
        }

        // Restore: user rejected offer -> remove offer pending and prompt about original cancellation.
        if (only.getType() == IntentType.CONFIRMATION_NEGATIVE && OFFER_ACTION.equalsIgnoreCase(pending.action())) {
            return true;
        }

        return false;
    }

    @Override
    public PipelineContext resolve(MultiIntentResponse intentResponse,
                                   Map<String, Object> sessionMetadata,
                                   PipelineContext context) {
        PendingAction pending = peekPending(context);
        if (pending == null) {
            return context;
        }

        Intent only = intentResponse.getIntents().getFirst();
        if (only == null || only.getType() == null) {
            return context;
        }

        if (only.getType() == IntentType.CONFIRMATION_POSITIVE && INTERCEPT_ACTION.equalsIgnoreCase(pending.action())) {
            // Mark the original pending action so we don't re-offer in a loop if user declines.
            PendingAction updated = markOfferAttempted(pending);
            popPending(context);
            pushPending(context, updated);

            Intent offer = Intent.builder()
                .type(IntentType.ACTION)
                .action(OFFER_ACTION)
                .actionParams(buildOfferParams(pending))
                .confidence(1.0d)
                .build();

            MultiIntentResponse updatedResponse = MultiIntentResponse.builder()
                .intents(List.of(offer))
                .compound(false)
                .orchestrationStrategy(intentResponse.getOrchestrationStrategy())
                .metadata(intentResponse.getMetadata())
                .build();

            return context.toBuilder()
                .intentResponse(updatedResponse)
                .build();
        }

        if (only.getType() == IntentType.CONFIRMATION_POSITIVE && OFFER_ACTION.equalsIgnoreCase(pending.action())) {
            PendingAction offer = popPending(context);
            if (offer == null) {
                return context;
            }

            // If the previous pending action is the original cancellation, clear it.
            PendingAction previous = peekPending(context);
            if (previous != null && INTERCEPT_ACTION.equalsIgnoreCase(previous.action())) {
                popPending(context);
            }

            Intent offerAction = Intent.builder()
                .type(IntentType.ACTION)
                .action(OFFER_ACTION)
                .actionParams(offer.actionParams() != null ? offer.actionParams() : Map.of())
                .confidence(1.0d)
                .build();

            MultiIntentResponse updatedResponse = MultiIntentResponse.builder()
                .intents(List.of(offerAction))
                .compound(false)
                .orchestrationStrategy(intentResponse.getOrchestrationStrategy())
                .metadata(intentResponse.getMetadata())
                .build();

            return markConfirmed(context, OFFER_ACTION).toBuilder()
                .intentResponse(updatedResponse)
                .build();
        }

        if (only.getType() == IntentType.CONFIRMATION_NEGATIVE && OFFER_ACTION.equalsIgnoreCase(pending.action())) {
            // Remove the offer.
            popPending(context);

            // If the previous pending action is the original cancellation, execute it now (already confirmed).
            PendingAction cancel = peekPending(context);
            if (cancel != null && INTERCEPT_ACTION.equalsIgnoreCase(cancel.action())) {
                popPending(context);

                Intent cancelAction = Intent.builder()
                    .type(IntentType.ACTION)
                    .action(cancel.action())
                    .actionParams(cancel.actionParams() != null ? cancel.actionParams() : Map.of())
                    .confidence(1.0d)
                    .build();

                MultiIntentResponse updatedResponse = MultiIntentResponse.builder()
                    .intents(List.of(cancelAction))
                    .compound(false)
                    .orchestrationStrategy(intentResponse.getOrchestrationStrategy())
                    .metadata(intentResponse.getMetadata())
                    .build();

                return markConfirmed(context, INTERCEPT_ACTION).toBuilder()
                    .intentResponse(updatedResponse)
                    .build();
            }

            Intent info = Intent.builder()
                .type(IntentType.INFORMATION)
                .requiresRetrieval(false)
                .requiresGeneration(false)
                .directAnswer("Okay — I won't apply the discount. What would you like to do next?")
                .confidence(1.0d)
                .build();

            MultiIntentResponse updatedResponse = MultiIntentResponse.builder()
                .intents(List.of(info))
                .compound(false)
                .orchestrationStrategy(intentResponse.getOrchestrationStrategy())
                .metadata(intentResponse.getMetadata())
                .build();

            return context.toBuilder()
                .intentResponse(updatedResponse)
                .build();
        }

        return context;
    }

    @Override
    public int getPriority() {
        // Run before CompoundConfirmationResolver(10) and SingleConfirmationPositiveResolver(50).
        return 8;
    }

    @Override
    public String getResolverName() {
        return "CancellationRetentionOfferResolver";
    }

    private boolean wasOfferAlreadyAttempted(PendingAction pendingAction) {
        Map<String, Object> params = pendingAction.actionParams();
        return params != null && Boolean.TRUE.equals(params.get(PARAM_RETENTION_OFFERED));
    }

    private PendingAction markOfferAttempted(PendingAction pendingAction) {
        Map<String, Object> params = pendingAction.actionParams();
        Map<String, Object> next = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
        next.put(PARAM_RETENTION_OFFERED, true);
        return new PendingAction(pendingAction.action(), next, pendingAction.description(), pendingAction.createdAt());
    }

    private Map<String, Object> buildOfferParams(PendingAction pendingAction) {
        Map<String, Object> params = pendingAction.actionParams();
        Map<String, Object> offerParams = new LinkedHashMap<>();
        if (params != null) {
            if (params.get("orderNumber") != null) {
                offerParams.put("orderNumber", params.get("orderNumber"));
            }
            if (params.get("orderId") != null) {
                offerParams.put("orderId", params.get("orderId"));
            }
        }
        offerParams.put("discountPercent", 10);
        return offerParams;
    }
}
