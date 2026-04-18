package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.service.PublicConsumerBridgeChatService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/consumers")
public class PublicConsumerBridgeController {

    private final PublicConsumerBridgeChatService publicConsumerBridgeChatService;

    public PublicConsumerBridgeController(PublicConsumerBridgeChatService publicConsumerBridgeChatService) {
        this.publicConsumerBridgeChatService = publicConsumerBridgeChatService;
    }

    @PostMapping("/{consumerId}/bridge/chat/query")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccessConsumer(authentication, #consumerId)")
    public JsonNode query(@PathVariable String consumerId,
                          @RequestBody(required = false) JsonNode request,
                          @RequestHeader(value = PublicConsumerBridgeChatService.SHOPPER_SESSION_HEADER, required = false)
                          String shopperSessionId) {
        return publicConsumerBridgeChatService.query(consumerId, request, shopperSessionId);
    }

    @PostMapping("/{consumerId}/bridge/chat/suggestions")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccessConsumer(authentication, #consumerId)")
    public JsonNode suggestions(@PathVariable String consumerId,
                                @RequestBody(required = false) JsonNode request,
                                @RequestHeader(value = PublicConsumerBridgeChatService.SHOPPER_SESSION_HEADER, required = false)
                                String shopperSessionId) {
        return publicConsumerBridgeChatService.suggestions(consumerId, request, shopperSessionId);
    }
}
