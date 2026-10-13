package com.loomai.inbox.web;

import static com.loomai.inbox.model.InboxContracts.*;

import ai.fabric.chat.channel.ConversationChannelDispatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.inbox.service.BasicInboxService;
import com.loomai.inbox.service.InboxSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversation-channel/v1")
public class ConversationChannelProviderController {

    private final InboxSecurityService security;
    private final BasicInboxService service;
    private final ObjectMapper objectMapper;

    public ConversationChannelProviderController(
        InboxSecurityService security,
        BasicInboxService service,
        ObjectMapper objectMapper
    ) {
        this.security = security;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/dispatches")
    public ResponseEntity<DispatchResponse> dispatch(
        @RequestHeader("X-LoomAI-Inbox-Key") String apiKey,
        @RequestBody String rawBody
    ) throws Exception {
        if (!security.providerAuthorized(apiKey)) {
            throw new InboxSecurityService.InboxSecurityException(
                "PROVIDER_AUTH_INVALID",
                "Conversation-channel provider authentication failed."
            );
        }
        ConversationChannelDispatch dispatch = objectMapper.readValue(rawBody, ConversationChannelDispatch.class);
        return ResponseEntity.accepted().body(service.dispatch(dispatch, rawBody));
    }
}
