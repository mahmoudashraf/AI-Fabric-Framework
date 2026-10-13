package com.loomai.demo.dealership.handover;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/human-conversations")
public class StaffHumanConversationController {

    private final HumanInboxClient inbox;

    public StaffHumanConversationController(HumanInboxClient inbox) {
        this.inbox = inbox;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        if (!inbox.enabled()) {
            return Map.of("enabled", false, "message", "Human conversation inbox is not configured.");
        }
        return Map.of("enabled", true, "operator", inbox.operator());
    }

    @GetMapping
    public Map<String, Object> queue(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return Map.of("items", inbox.queue(status, limit));
    }

    @GetMapping("/{handoverId}")
    public Map<String, Object> detail(@PathVariable String handoverId) {
        return Map.of("item", inbox.detail(handoverId));
    }

    @PostMapping("/{handoverId}/claim")
    public Map<String, Object> claim(
        @PathVariable String handoverId,
        @Valid @RequestBody CommandRequest request
    ) {
        return Map.of("result", inbox.claim(handoverId, request.idempotencyKey()));
    }

    @PostMapping("/{handoverId}/messages")
    public Map<String, Object> reply(
        @PathVariable String handoverId,
        @Valid @RequestBody ReplyRequest request
    ) {
        return Map.of("result", inbox.reply(handoverId, request.text(), request.idempotencyKey()));
    }

    @PostMapping("/{handoverId}/return-to-ai")
    public Map<String, Object> returnToAi(
        @PathVariable String handoverId,
        @Valid @RequestBody CommandRequest request
    ) {
        return Map.of("result", inbox.returnToAi(handoverId, request.idempotencyKey()));
    }

    @PostMapping("/{handoverId}/close")
    public Map<String, Object> close(
        @PathVariable String handoverId,
        @Valid @RequestBody CommandRequest request
    ) {
        return Map.of("result", inbox.close(handoverId, request.idempotencyKey()));
    }

    public record CommandRequest(
        @NotBlank @Size(max = 200) String idempotencyKey
    ) {}

    public record ReplyRequest(
        @NotBlank @Size(max = 20_000) String text,
        @NotBlank @Size(max = 200) String idempotencyKey
    ) {}
}
