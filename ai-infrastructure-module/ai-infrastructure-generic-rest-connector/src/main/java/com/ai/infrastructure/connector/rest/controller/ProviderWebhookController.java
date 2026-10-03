package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.service.ProviderWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class ProviderWebhookController {

    private final RestRoutingConfig config;
    private final ProviderWebhookService service;

    public ProviderWebhookController(RestRoutingConfig config, ProviderWebhookService service) {
        this.config = config;
        this.service = service;
    }

    @RequestMapping(
        path = "/integrations/webhooks/{sourceId}",
        method = {RequestMethod.POST, RequestMethod.PUT}
    )
    public ResponseEntity<ProviderWebhookService.WebhookReceipt> accept(
        @PathVariable String sourceId,
        HttpServletRequest request
    ) throws IOException {
        RestRoutingConfig.WebhookSource source = config.getWebhooks().get(sourceId);
        int maxBodyBytes = source != null ? source.getMaxBodyBytes() : 1024 * 1024;
        byte[] rawBody = request.getInputStream().readNBytes(Math.max(1, maxBodyBytes) + 1);
        String signature = source != null && source.getVerification() != null
            && StringUtils.hasText(source.getVerification().getSignatureHeader())
            ? request.getHeader(source.getVerification().getSignatureHeader())
            : null;
        ProviderWebhookService.WebhookReceipt receipt = service.accept(
            sourceId,
            request.getMethod(),
            request.getContentType(),
            signature,
            rawBody
        );
        if (!receipt.accepted()) {
            return ResponseEntity.status(rejectionStatus(source, receipt.errorClass())).body(receipt);
        }
        RestRoutingConfig.WebhookResponseStatuses statuses = responseStatuses(source);
        return ResponseEntity.status(receipt.duplicate() ? statuses.getDuplicate() : statuses.getAccepted()).body(receipt);
    }

    static HttpStatus rejectionStatus(RestRoutingConfig.WebhookSource source, String errorClass) {
        if ("WEBHOOK_METHOD_NOT_ALLOWED".equals(errorClass)) {
            return HttpStatus.METHOD_NOT_ALLOWED;
        }
        if ("WEBHOOK_CONTENT_TYPE_NOT_ALLOWED".equals(errorClass)) {
            return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        }
        if ("WEBHOOK_BODY_SIZE_INVALID".equals(errorClass)) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        if ("WEBHOOK_RESOURCE_MISMATCH".equals(errorClass)) {
            return HttpStatus.valueOf(responseStatuses(source).getResourceMismatch());
        }
        if ("WEBHOOK_EVENT_ID_CONFLICT".equals(errorClass)) {
            return HttpStatus.CONFLICT;
        }
        if ("WEBHOOK_BODY_MALFORMED".equals(errorClass)
            || "WEBHOOK_EVENT_IDENTITY_MISSING".equals(errorClass)
            || "WEBHOOK_EVENT_IDENTITY_INVALID".equals(errorClass)
            || "WEBHOOK_RECORD_KEY_MISSING".equals(errorClass)
            || "WEBHOOK_RECORD_KEY_INVALID".equals(errorClass)
            || "WEBHOOK_EVENT_TYPE_NOT_ALLOWED".equals(errorClass)) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.UNAUTHORIZED;
    }

    private static RestRoutingConfig.WebhookResponseStatuses responseStatuses(RestRoutingConfig.WebhookSource source) {
        if (source == null || source.getResponseStatuses() == null) {
            return new RestRoutingConfig.WebhookResponseStatuses();
        }
        return source.getResponseStatuses();
    }
}
