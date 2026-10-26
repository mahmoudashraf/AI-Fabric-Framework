package com.loomai.desk.web;

import com.loomai.desk.channel.model.InboxContracts.DispatchResponse;
import com.loomai.desk.service.DeskChannelService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConversationChannelProviderController {
  private final DeskChannelService channels;

  public ConversationChannelProviderController(DeskChannelService channels) {
    this.channels = channels;
  }

  @PostMapping(value = "/api/conversation-channel/v1/dispatches", consumes = "application/json")
  public DispatchResponse dispatch(
      @RequestHeader(value = "X-LoomAI-Inbox-Key", required = false) String key,
      @RequestBody String body)
      throws Exception {
    if (body.length() > 2_000_000)
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "Dispatch exceeds transcript limit.");
    return channels.dispatch(key, body);
  }
}
