package com.loomai.desk.ticket;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/desk/requester/v1/spaces/{spaceId}/notifications")
public class TicketNotificationController {
  private final TicketNotifications notifications;
  private final RequesterIdentityService identities;

  public TicketNotificationController(
      TicketNotifications notifications, RequesterIdentityService identities) {
    this.notifications = notifications;
    this.identities = identities;
  }

  @GetMapping
  public TicketNotifications.NotificationView get(HttpServletRequest request) {
    return notifications.view(
        RequesterTicketController.space(request), RequesterTicketController.requester(request));
  }

  @PutMapping
  public TicketNotifications.NotificationView put(
      HttpServletRequest request, @RequestBody java.util.Map<String, Object> body) {
    // Only consent and navigation are client inputs; a recipient can never be supplied here.
    if (!java.util.Set.of("enabled", "returnUrl").containsAll(body.keySet())
        || !(body.get("enabled") instanceof Boolean enabled)
        || body.get("returnUrl") != null && !(body.get("returnUrl") instanceof String))
      throw TicketException.invalid("notification preferences");
    var space = RequesterTicketController.space(request);
    String email =
        enabled ? identities.verifiedEmail(space, request.getHeader("Authorization")) : null;
    return notifications.put(
        space,
        RequesterTicketController.requester(request),
        enabled,
        (String) body.get("returnUrl"),
        email);
  }
}
