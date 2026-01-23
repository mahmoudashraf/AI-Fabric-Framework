package com.ai.fabric.realapps.chat.reviews.action;

import com.ai.fabric.realapps.chat.reviews.domain.Review;
import com.ai.fabric.realapps.chat.reviews.service.ReviewService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class AddReviewActionHandler implements ActionHandler {

    private final ReviewService reviewService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("add_review")
            .description("Add a review for a product (this is searchable)")
            .category("commerce")
            .parameters(Map.of(
                "productId", "Product id (optional if sku is provided)",
                "sku", "Product sku (optional if productId is provided)",
                "rating", "Rating 1-5 (required)",
                "text", "Review text (required)"
            ))
            .requiredParameters(Set.of("rating", "text"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Submit this review?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        Long productId = longParam(params, "productId");
        String sku = stringParam(params, "sku");
        int rating = requiredInt(params, "rating");
        String text = requiredString(params, "text");

        Review created = reviewService.create(userId, productId, sku, rating, text);

        return ActionResult.builder()
            .success(true)
            .message("Review submitted")
            .data(Map.of(
                "reviewId", created.getId(),
                "rating", created.getRating(),
                "productId", created.getProductId(),
                "sku", created.getSku()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Add review failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to add review: " + e.getMessage())
            .errorCode("ADD_REVIEW_FAILED")
            .build();
    }

    private String requiredString(Map<String, Object> params, String key) {
        String value = stringParam(params, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private int requiredInt(Map<String, Object> params, String key) {
        Integer value = intParam(params, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private String stringParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        return raw != null ? raw.toString() : null;
    }

    private Integer intParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Long longParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw != null) {
            try {
                return Long.parseLong(raw.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}

