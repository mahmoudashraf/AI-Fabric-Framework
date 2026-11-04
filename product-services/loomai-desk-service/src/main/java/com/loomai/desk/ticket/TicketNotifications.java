package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TicketNotifications {
  private static final Logger LOG = LoggerFactory.getLogger(TicketNotifications.class);
  static final int MAX_ATTEMPTS = 4;
  static final int COLLECT_LIMIT = 25;
  static final int MAX_PENDING_PER_REQUESTER = 50;
  static final Duration LEASE = Duration.ofMinutes(2);
  private final SupportSpaces spaces;
  private final JdbcTemplate jdbc;
  private final RequesterIdentityService identities;
  private final TicketNotificationEmail email;
  private final TransactionTemplate tx;
  private final AtomicBoolean running = new AtomicBoolean();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          Thread.ofPlatform().name("desk-ticket-email-", 0).factory());

  public TicketNotifications(
      SupportSpaces spaces,
      RequesterIdentityService identities,
      TicketNotificationEmail email,
      PlatformTransactionManager manager) {
    this.spaces = spaces;
    this.jdbc = spaces.jdbc();
    this.identities = identities;
    this.email = email;
    this.tx = new TransactionTemplate(manager);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NotificationView(
      boolean available,
      boolean enabled,
      String deliveryStatus,
      String lastFailureCode,
      Instant updatedAt) {}

  private record Subscription(
      String space,
      String requester,
      boolean enabled,
      String recipient,
      String recipientHash,
      String returnUrl,
      String consent,
      Instant updatedAt) {}

  private record Event(
      String space,
      String ticket,
      String requester,
      String event,
      String recipientHash,
      String consent,
      String reference) {}

  private record Claim(
      String id,
      String space,
      String requester,
      String consent,
      String reference,
      String owner,
      int attempt) {}

  public NotificationView view(Space space, Requester requester) {
    boolean available = email.available() && identities.emailVerificationAvailable(space);
    Subscription subscription = subscription(space.view().spaceId(), requester.id());
    if (subscription == null)
      return new NotificationView(available, false, available ? "OFF" : "UNAVAILABLE", null, null);
    String status = !available ? "UNAVAILABLE" : !subscription.enabled() ? "OFF" : "IDLE";
    var rows =
        jdbc.query(
            "SELECT status,failure_code,updated_at FROM desk_support_notification_outbox WHERE"
                + " space_id=? AND requester_id=? AND consent_id=? ORDER BY CASE status WHEN"
                + " 'FAILED' THEN 0 WHEN 'RETRY' THEN 1 WHEN 'LEASED' THEN 2 WHEN 'PENDING' THEN 3"
                + " ELSE 4 END, updated_at DESC,notification_id LIMIT 1",
            (r, n) ->
                new NotificationView(
                    available,
                    subscription.enabled(),
                    available && subscription.enabled() ? r.getString("status") : status,
                    r.getString("failure_code"),
                    r.getTimestamp("updated_at").toInstant()),
            subscription.space(),
            subscription.requester(),
            subscription.consent());
    return rows.isEmpty()
        ? new NotificationView(
            available, subscription.enabled(), status, null, subscription.updatedAt())
        : rows.getFirst();
  }

  public NotificationView put(
      Space space, Requester requester, boolean enabled, String returnUrl, String verifiedEmail) {
    if (enabled && (!email.available() || !identities.emailVerificationAvailable(space)))
      throw new TicketException(
          422, "NOTIFICATIONS_UNAVAILABLE", "Email notifications are not available.");
    String safeUrl = enabled ? returnUrl(space, returnUrl) : null;
    if (enabled && (verifiedEmail == null || verifiedEmail.isBlank()))
      throw new TicketException(
          403, "NOTIFICATION_EMAIL_UNVERIFIED", "Verified email is required.");
    return tx.execute(
        ignored -> {
          spaces.lock(space.view().spaceId());
          Space current = spaces.available(space.view().spaceId());
          if (!current.view().enabled())
            throw new TicketException(409, "SUPPORT_SPACE_DISABLED", "Support is disabled.");
          if (enabled) returnUrl(current, safeUrl);
          spaces.register(current, requester);
          Subscription previous = subscription(current.view().spaceId(), requester.id());
          String hash =
              enabled ? spaces.secrets().sha256(verifiedEmail.toLowerCase(Locale.ROOT)) : null;
          if (previous != null
              && previous.enabled() == enabled
              && (!enabled
                  || hash.equals(previous.recipientHash()) && safeUrl.equals(previous.returnUrl())))
            return view(current, requester);
          Instant now = Instant.now();
          cancelPending(current.view().spaceId(), requester.id(), now);
          String consent = UUID.randomUUID().toString();
          Object[] values = {
            enabled,
            enabled ? spaces.secrets().encrypt(verifiedEmail) : null,
            hash,
            safeUrl,
            consent,
            Timestamp.from(now),
            Timestamp.from(now),
            current.view().spaceId(),
            requester.id()
          };
          if (previous == null)
            jdbc.update(
                "INSERT INTO"
                    + " desk_support_notification_subscription(enabled,protected_recipient,recipient_hash,return_url,consent_id,consented_at,updated_at,space_id,requester_id)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                values);
          else
            jdbc.update(
                "UPDATE desk_support_notification_subscription SET"
                    + " enabled=?,protected_recipient=?,recipient_hash=?,return_url=?,consent_id=?,consented_at=?,updated_at=?"
                    + " WHERE space_id=? AND requester_id=?",
                values);
          return view(current, requester);
        });
  }

  private String returnUrl(Space space, String value) {
    try {
      if (value == null || value.length() > 2048 || value.chars().anyMatch(c -> c < 32 || c == 127))
        throw new IllegalArgumentException();
      URI uri = URI.create(value);
      String origin =
          "https://"
              + uri.getHost()
              + (uri.getPort() == -1 || uri.getPort() == 443 ? "" : ":" + uri.getPort());
      if (!"https".equals(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !uri.equals(uri.normalize())
          || !space.view().allowedOrigins().contains(origin)
          || value.contains("\\")) throw new IllegalArgumentException();
      // No redirects, URL fetches, query tokens, fragments or client-supplied recipient fields.
      return uri.toASCIIString();
    } catch (IllegalArgumentException invalid) {
      throw TicketException.invalid(
          "registered HTTPS notification returnUrl without query or fragment");
    }
  }

  private Subscription subscription(String space, String requester) {
    return jdbc
        .query(
            "SELECT * FROM desk_support_notification_subscription WHERE space_id=? AND"
                + " requester_id=?",
            (r, n) ->
                new Subscription(
                    space,
                    requester,
                    r.getBoolean("enabled"),
                    r.getString("protected_recipient"),
                    r.getString("recipient_hash"),
                    r.getString("return_url"),
                    r.getString("consent_id"),
                    r.getTimestamp("updated_at").toInstant()),
            space,
            requester)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private void cancelPending(String space, String requester, Instant now) {
    jdbc.update(
        "UPDATE desk_support_notification_outbox SET"
            + " status='CANCELLED',lease_owner=NULL,lease_until=NULL,updated_at=? WHERE space_id=?"
            + " AND requester_id=? AND status IN ('PENDING','RETRY','LEASED')",
        Timestamp.from(now),
        space,
        requester);
  }

  @Scheduled(fixedDelayString = "${DESK_EMAIL_POLL_MS:5000}")
  public void poll() {
    if (!email.available() || !running.compareAndSet(false, true)) return;
    try {
      // Never block the existing callback scheduler on SMTP or accumulate scheduled mail jobs.
      worker.execute(
          () -> {
            try {
              collect();
              for (int i = 0; i < 5; i++) if (!deliverNext()) break;
            } catch (Exception failure) {
              // Mail/HTTP/SQL exception messages may contain addresses, credentials or URLs.
              LOG.warn(
                  "Desk ticket notification poll failed ({})", failure.getClass().getSimpleName());
            } finally {
              running.set(false);
            }
          });
    } catch (java.util.concurrent.RejectedExecutionException stopped) {
      running.set(false);
    }
  }

  @PreDestroy
  public void stop() {
    worker.shutdown();
    try {
      if (!worker.awaitTermination(30, TimeUnit.SECONDS)) worker.shutdownNow();
    } catch (InterruptedException interrupted) {
      worker.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public void collect() {
    if (!email.available()) return;
    String joins =
        " JOIN desk_support_request m ON m.space_id=e.space_id AND m.request_id=e.request_id JOIN"
            + " desk_support_ticket t ON t.space_id=m.space_id AND t.request_id=m.request_id JOIN"
            + " desk_support_notification_subscription n ON n.space_id=m.space_id AND"
            + " n.requester_id=m.requester_id JOIN desk_support_space s ON s.space_id=m.space_id"
            + " JOIN desk_binding b ON b.binding_id=s.binding_id";
    String eligibility =
        " AND n.enabled=TRUE AND s.enabled=TRUE AND b.enabled=TRUE AND NOT EXISTS(SELECT 1 FROM"
            + " desk_support_notification_outbox o WHERE o.space_id=m.space_id AND"
            + " o.request_id=m.request_id AND o.event_id=";
    String projection =
        "SELECT m.space_id,m.request_id,m.requester_id,n.recipient_hash,n.consent_id,t.reference,";
    collect(
        jdbc.query(
            projection
                + "'status:' || e.event_id AS event_key FROM desk_support_ticket_event e"
                + joins
                + " WHERE e.event_type IN ('RESOLVE','CANCEL','REOPEN','AGENT_RESOLVE') AND"
                + " e.created_at>n.consented_at"
                + eligibility
                + "'status:' || e.event_id AND o.recipient_hash=n.recipient_hash) ORDER BY"
                + " e.created_at LIMIT "
                + COLLECT_LIMIT,
            (r, n) -> event(r)));
    // COMPLETED is set only after a successful, correlated normal runtime callback acknowledgement.
    collect(
        jdbc.query(
            projection
                + "'human:' || c.callback_id AS event_key FROM inbox_callback_outbox c JOIN"
                + " inbox_conversation h ON h.binding_id=c.binding_id AND"
                + " h.handover_id=c.handover_id JOIN desk_support_ticket_episode e ON"
                + " e.conversation_id=h.conversation_id"
                + joins
                + " WHERE c.event_type='HUMAN_MESSAGE' AND c.status='COMPLETED' AND c.completed_at"
                + " IS NOT NULL AND c.binding_id=s.binding_id AND h.owner_ref=m.runtime_principal"
                + " AND h.tenant_id=b.tenant_id AND h.deployment_id=b.deployment_id AND"
                + " s.human_enabled=TRUE AND c.created_at>n.consented_at AND"
                + " c.completed_at>n.consented_at"
                + eligibility
                + "'human:' || c.callback_id AND o.recipient_hash=n.recipient_hash) ORDER BY"
                + " c.completed_at LIMIT "
                + COLLECT_LIMIT,
            (r, n) -> event(r)));
  }

  private Event event(ResultSet r) throws SQLException {
    return new Event(
        r.getString("space_id"),
        r.getString("request_id"),
        r.getString("requester_id"),
        r.getString("event_key"),
        r.getString("recipient_hash"),
        r.getString("consent_id"),
        r.getString("reference"));
  }

  private void collect(List<Event> events) {
    for (Event event : events)
      tx.executeWithoutResult(
          ignored -> {
            spaces.lock(event.space());
            Space space = spaces.available(event.space());
            Subscription subscription = subscription(event.space(), event.requester());
            if (!space.view().enabled()
                || subscription == null
                || !subscription.enabled()
                || !subscription.consent().equals(event.consent())) return;
            String id =
                spaces
                    .secrets()
                    .sha256(
                        spaces.write(
                            List.of(
                                event.space(),
                                event.ticket(),
                                event.event(),
                                event.recipientHash())));
            long pending =
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM desk_support_notification_outbox WHERE space_id=? AND"
                        + " requester_id=? AND status IN ('PENDING','RETRY','LEASED')",
                    Long.class,
                    event.space(),
                    event.requester());
            boolean full = pending >= MAX_PENDING_PER_REQUESTER;
            Instant now = Instant.now();
            jdbc.update(
                "INSERT INTO"
                    + " desk_support_notification_outbox(notification_id,space_id,request_id,requester_id,event_id,recipient_hash,consent_id,reference,status,attempt_count,next_attempt_at,failure_code,created_at,updated_at)"
                    + " SELECT ?,?,?,?,?,?,?,?,?,0,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM"
                    + " desk_support_notification_outbox WHERE notification_id=?)",
                id,
                event.space(),
                event.ticket(),
                event.requester(),
                event.event(),
                event.recipientHash(),
                event.consent(),
                event.reference(),
                full ? "FAILED" : "PENDING",
                Timestamp.from(now),
                full ? "NOTIFICATION_BACKLOG_LIMIT" : null,
                Timestamp.from(now),
                Timestamp.from(now),
                id);
          });
  }

  public boolean deliverNext() {
    if (!email.available()) return false;
    Claim claim =
        tx.execute(
            ignored -> {
              Instant now = Instant.now();
              jdbc.update(
                  "UPDATE desk_support_notification_outbox SET"
                      + " status='FAILED',failure_code='EMAIL_DELIVERY_UNCERTAIN',lease_owner=NULL,lease_until=NULL,updated_at=?"
                      + " WHERE status='LEASED' AND lease_until<? AND attempt_count>=?",
                  Timestamp.from(now),
                  Timestamp.from(now),
                  MAX_ATTEMPTS);
              List<String> ids =
                  jdbc.query(
                      "SELECT notification_id FROM desk_support_notification_outbox WHERE"
                          + " attempt_count<? AND ((status IN ('PENDING','RETRY') AND"
                          + " next_attempt_at<=?) OR (status='LEASED' AND lease_until<?)) ORDER BY"
                          + " created_at,notification_id LIMIT 1",
                      (r, n) -> r.getString(1),
                      MAX_ATTEMPTS,
                      Timestamp.from(now),
                      Timestamp.from(now));
              if (ids.isEmpty()) return null;
              String owner = UUID.randomUUID().toString();
              int changed =
                  jdbc.update(
                      "UPDATE desk_support_notification_outbox SET"
                          + " status='LEASED',lease_owner=?,lease_until=?,attempt_count=attempt_count+1,updated_at=?"
                          + " WHERE notification_id=? AND attempt_count<? AND ((status IN"
                          + " ('PENDING','RETRY') AND next_attempt_at<=?) OR (status='LEASED' AND"
                          + " lease_until<?))",
                      owner,
                      Timestamp.from(now.plus(LEASE)),
                      Timestamp.from(now),
                      ids.getFirst(),
                      MAX_ATTEMPTS,
                      Timestamp.from(now),
                      Timestamp.from(now));
              if (changed != 1) return null;
              return jdbc.queryForObject(
                  "SELECT * FROM desk_support_notification_outbox WHERE notification_id=?",
                  (r, n) ->
                      new Claim(
                          r.getString("notification_id"),
                          r.getString("space_id"),
                          r.getString("requester_id"),
                          r.getString("consent_id"),
                          r.getString("reference"),
                          owner,
                          r.getInt("attempt_count")),
                  ids.getFirst());
            });
    if (claim == null) return false;
    tx.executeWithoutResult(ignored -> send(claim));
    return true;
  }

  private void send(Claim claim) {
    String binding = spaces.get(claim.space()).view().bindingId();
    // Match support-space administration's binding -> space lock order.
    boolean bindingEnabled =
        jdbc.queryForObject(
            "SELECT enabled FROM desk_binding WHERE binding_id=? FOR UPDATE",
            Boolean.class,
            binding);
    spaces.lock(claim.space());
    Space space = spaces.get(claim.space());
    Subscription subscription = subscription(claim.space(), claim.requester());
    var owns =
        jdbc.query(
            "SELECT notification_id FROM desk_support_notification_outbox WHERE notification_id=?"
                + " AND status='LEASED' AND lease_owner=? FOR UPDATE",
            (r, n) -> r.getString(1),
            claim.id(),
            claim.owner());
    if (owns.isEmpty()) return;
    String status;
    String failure = null;
    if (!space.view().enabled()
        || !bindingEnabled
        || subscription == null
        || !subscription.enabled()
        || !subscription.consent().equals(claim.consent())
        || !identities.emailVerificationAvailable(space)) {
      status = "CANCELLED";
    } else {
      try {
        String url = returnUrl(space, subscription.returnUrl());
        if (!claim.reference().matches("[A-Za-z0-9._-]{1,160}")) throw new IllegalStateException();
        email.send(
            claim.id(), spaces.secrets().decrypt(subscription.recipient()), claim.reference(), url);
        status = "SENT";
      } catch (Exception deliveryFailure) {
        status = claim.attempt() >= MAX_ATTEMPTS ? "FAILED" : "RETRY";
        failure = "EMAIL_DELIVERY_FAILED";
      }
    }
    Instant now = Instant.now();
    jdbc.update(
        "UPDATE desk_support_notification_outbox SET"
            + " status=?,failure_code=?,next_attempt_at=?,lease_owner=NULL,lease_until=NULL,updated_at=?"
            + " WHERE notification_id=? AND status='LEASED' AND lease_owner=?",
        status,
        failure,
        Timestamp.from(now.plusSeconds(30L * claim.attempt())),
        Timestamp.from(now),
        claim.id(),
        claim.owner());
  }
}
