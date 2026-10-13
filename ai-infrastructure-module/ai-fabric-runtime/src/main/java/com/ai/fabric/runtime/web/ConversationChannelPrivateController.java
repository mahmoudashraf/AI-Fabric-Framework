package com.ai.fabric.runtime.web;

import static com.ai.fabric.runtime.handover.ConversationHandoverContracts.*;

import ai.fabric.chat.channel.ConversationChannelEvent;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/private")
@ConditionalOnProperty(prefix = "loomai.conversation-handover", name = "enabled", havingValue = "true")
public class ConversationChannelPrivateController {

    private final RuntimeRequestAuthResolver authResolver;
    private final RuntimeConversationHandoverService service;
    private final ObjectMapper objectMapper;

    public ConversationChannelPrivateController(
        RuntimeRequestAuthResolver authResolver,
        RuntimeConversationHandoverService service,
        ObjectMapper objectMapper
    ) {
        this.authResolver = authResolver;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/conversation-channel-bindings/{bindingId}/events")
    public Object event(
        @PathVariable String bindingId,
        @RequestHeader("X-LoomAI-Channel-Timestamp") String timestamp,
        @RequestHeader("X-LoomAI-Channel-Nonce") String nonce,
        @RequestHeader("X-LoomAI-Channel-Signature") String signature,
        @RequestBody String rawBody,
        HttpServletRequest request
    ) throws Exception {
        authResolver.requireIntegrationServiceIngress(request, "conversation-channel-event");
        service.verifyChannelCallback(timestamp, nonce, rawBody, signature);
        ConversationChannelEvent event = objectMapper.readValue(rawBody, ConversationChannelEvent.class);
        return service.receiveChannelEvent(bindingId, event, nonce, rawBody);
    }

    @PostMapping("/conversation-channel-dispatches/claim")
    public ResponseEntity<DispatchClaim> claim(
        @RequestBody DispatchClaimRequest body,
        HttpServletRequest request
    ) {
        authResolver.requireIntegrationServiceIngress(request, "conversation-channel-dispatch-claim");
        long leaseSeconds = body == null ? 30 : Math.max(1, body.leaseSeconds());
        DispatchClaim claim = service.claimDispatch(
            body == null ? null : body.workerId(),
            Duration.ofSeconds(leaseSeconds)
        );
        return claim == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(claim);
    }

    @PostMapping("/conversation-channel-dispatches/{dispatchId}/outcomes/{attempt}")
    public ResponseEntity<Void> outcome(
        @PathVariable String dispatchId,
        @PathVariable int attempt,
        @RequestBody DispatchOutcome body,
        HttpServletRequest request
    ) {
        authResolver.requireIntegrationServiceIngress(request, "conversation-channel-dispatch-outcome");
        service.completeDispatch(dispatchId, body, attempt);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
