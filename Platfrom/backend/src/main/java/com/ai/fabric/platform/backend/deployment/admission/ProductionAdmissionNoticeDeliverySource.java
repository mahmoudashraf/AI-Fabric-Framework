package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Durable console/email delivery. SMTP PENDING is outcome-unknown and is never silently resent. */
@Component
final class ProductionAdmissionNoticeDeliverySource implements AdmissionNoticeDeliverySource {
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AdmissionOwnerContactResolver owners;
    private final AdmissionLifecycleProperties properties;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AdmissionJson json;
    private final Clock clock;

    @Autowired
    ProductionAdmissionNoticeDeliverySource(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                            AdmissionOwnerContactResolver owners,
                                            AdmissionLifecycleProperties properties,
                                            ObjectProvider<JavaMailSender> mailSender,
                                            AdmissionJson json) {
        this(jdbc, manager, owners, properties, mailSender, json, Clock.systemUTC());
    }

    ProductionAdmissionNoticeDeliverySource(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                            AdmissionOwnerContactResolver owners,
                                            AdmissionLifecycleProperties properties,
                                            ObjectProvider<JavaMailSender> mailSender,
                                            AdmissionJson json, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.owners = owners;
        this.properties = properties;
        this.mailSender = mailSender;
        this.json = json;
        this.clock = clock;
    }

    void deliver(Reservation reservation, NoticeWork work) {
        validate(reservation, work);
        var owner = owners.resolve(reservation.customerId(),
            reservation.prerequisiteReadback().ownerContactEvidenceReference());
        DeliveryClaim claim = transactions.execute(status -> claim(work, owner, reservation));
        if (claim.disposition() == DeliveryDisposition.ALREADY_SENT) return;
        if (claim.disposition() == DeliveryDisposition.OUTCOME_UNKNOWN) {
            throw failure(PRECONDITION_FAILED, "NOTICE_DELIVERY_OUTCOME_UNKNOWN");
        }
        if (claim.disposition() == DeliveryDisposition.PUBLISHED) {
            markConsoleSent(work);
            return;
        }
        if (work.channel() == NoticeChannel.CONSOLE) {
            publishConsole(work, claim.token());
            markConsoleSent(work);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!properties.ownerEmailEnabled() || properties.ownerEmailFrom().isBlank() || sender == null) {
            markFailed(work, claim.token(), "NOTICE_EMAIL_TRANSPORT_UNAVAILABLE");
            throw failure(SERVICE_UNAVAILABLE, "NOTICE_EMAIL_TRANSPORT_UNAVAILABLE");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.ownerEmailFrom());
            message.setTo(owner.email());
            message.setSubject("LoomAI customer staging expiry notice");
            message.setText("Customer staging deployment " + reservation.deploymentId()
                + " is scheduled to expire at " + work.expiresAt()
                + ". Extend or keep it through the authenticated LoomAI Platform workflow before expiry.");
            sender.send(message);
            markEmailSent(work, claim.token(), "smtp:" + hash(work.deliveryKey()));
        } catch (MailException ex) {
            markOutcomeUnknown(work, claim.token(), "NOTICE_EMAIL_OUTCOME_UNKNOWN");
            throw failure(SERVICE_UNAVAILABLE, "NOTICE_DELIVERY_OUTCOME_UNKNOWN");
        }
    }

    @Override
    public NoticeDelivery read(NoticeWork work) {
        if (work == null) throw failure(PRECONDITION_FAILED, "NOTICE_WORK_REQUIRED");
        var rows = jdbc.query("""
            SELECT state,transport_reference,provider_reference,delivered_at
              FROM deployment_admission_notice_deliveries
             WHERE delivery_key=? AND reservation_id=? AND notice_cycle=? AND channel=?
            """, (rs, row) -> new DeliveryRow(rs.getString("state"), rs.getString("transport_reference"),
            rs.getString("provider_reference"), rs.getTimestamp("delivered_at") == null
                ? null : rs.getTimestamp("delivered_at").toInstant()),
            work.deliveryKey(), work.fence().reservationId(), work.noticeCycle(), work.channel().name());
        if (rows.size() != 1 || !"SENT".equals(rows.getFirst().state())
            || !work.transportReference().equals(rows.getFirst().transportReference())
            || rows.getFirst().providerReference() == null || rows.getFirst().deliveredAt() == null) {
            throw failure(PRECONDITION_FAILED, "NOTICE_DELIVERY_UNPROVEN");
        }
        String reference = "admission-notice:" + hash(work.deliveryKey() + ":" + rows.getFirst().providerReference());
        return new NoticeDelivery(work.deliveryKey(), reference, rows.getFirst().deliveredAt());
    }

    private DeliveryClaim claim(NoticeWork work, AdmissionOwnerContactResolver.OwnerContact owner,
                                Reservation reservation) {
        var rows = jdbc.query("""
            SELECT state,claim_token,claim_expires_at
              FROM deployment_admission_notice_deliveries WHERE delivery_key=? FOR UPDATE
            """, (rs, row) -> new ClaimRow(rs.getString(1), rs.getString(2),
                rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()), work.deliveryKey());
        Instant now = clock.instant();
        String token = UUID.randomUUID().toString();
        if (rows.isEmpty()) {
            jdbc.update("""
                INSERT INTO deployment_admission_notice_deliveries
                  (delivery_key,reservation_id,notice_cycle,channel,transport_reference,recipient_reference,
                   recipient_hash,state,attempt_count,safe_payload_json,claim_token,claim_expires_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, work.deliveryKey(), reservation.id(), work.noticeCycle(), work.channel().name(),
                work.transportReference(), owner.evidenceReference(), hash(work.deliveryKey() + ":" + owner.email()), "PENDING", 1,
                json.write(Map.of("deploymentId", reservation.deploymentId(), "targetProfileId", reservation.targetProfileId(),
                    "noticeCycle", work.noticeCycle(), "channel", work.channel().name(), "expiresAt", work.expiresAt())),
                token, java.sql.Timestamp.from(now.plus(CLAIM_LEASE)), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            return new DeliveryClaim(DeliveryDisposition.SEND, token);
        }
        ClaimRow row = rows.getFirst();
        if ("SENT".equals(row.state())) return new DeliveryClaim(DeliveryDisposition.ALREADY_SENT, null);
        if ("PUBLISHED".equals(row.state()) && work.channel() == NoticeChannel.CONSOLE) {
            return new DeliveryClaim(DeliveryDisposition.PUBLISHED, row.token());
        }
        if ("OUTCOME_UNKNOWN".equals(row.state())) return new DeliveryClaim(DeliveryDisposition.OUTCOME_UNKNOWN, null);
        if ("PENDING".equals(row.state())) {
            if (row.expiresAt() != null && row.expiresAt().isAfter(now)) {
                return new DeliveryClaim(DeliveryDisposition.OUTCOME_UNKNOWN, null);
            }
            if (work.channel() == NoticeChannel.OWNER_EMAIL) {
                jdbc.update("""
                    UPDATE deployment_admission_notice_deliveries
                       SET state='OUTCOME_UNKNOWN',failure_code='NOTICE_EMAIL_OUTCOME_UNKNOWN',updated_at=?
                     WHERE delivery_key=? AND state='PENDING'
                    """, java.sql.Timestamp.from(now), work.deliveryKey());
                return new DeliveryClaim(DeliveryDisposition.OUTCOME_UNKNOWN, null);
            }
        }
        int changed = jdbc.update("""
            UPDATE deployment_admission_notice_deliveries
               SET state='PENDING',attempt_count=attempt_count+1,failure_code=NULL,claim_token=?,claim_expires_at=?,updated_at=?
             WHERE delivery_key=? AND state IN ('PENDING','FAILED')
            """, token, java.sql.Timestamp.from(now.plus(CLAIM_LEASE)), java.sql.Timestamp.from(now), work.deliveryKey());
        if (changed != 1) return new DeliveryClaim(DeliveryDisposition.OUTCOME_UNKNOWN, null);
        return new DeliveryClaim(DeliveryDisposition.SEND, token);
    }

    private void publishConsole(NoticeWork work, String token) {
        Instant now = clock.instant();
        int changed = transactions.execute(status -> jdbc.update("""
            UPDATE deployment_admission_notice_deliveries
               SET state='PUBLISHED',provider_reference=?,published_at=?,failure_code=NULL,updated_at=?
             WHERE delivery_key=? AND state='PENDING' AND claim_token=?
            """, "console:" + work.deliveryKey(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now),
            work.deliveryKey(), token));
        if (changed != 1) throw failure(PRECONDITION_FAILED, "NOTICE_DELIVERY_OUTCOME_UNKNOWN");
    }

    private void markConsoleSent(NoticeWork work) {
        Instant now = clock.instant();
        int changed = transactions.execute(status -> jdbc.update("""
            UPDATE deployment_admission_notice_deliveries
               SET state='SENT',delivered_at=?,claim_token=NULL,claim_expires_at=NULL,updated_at=?
             WHERE delivery_key=? AND channel='CONSOLE' AND state='PUBLISHED' AND published_at IS NOT NULL
            """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), work.deliveryKey()));
        if (changed != 1) throw failure(PRECONDITION_FAILED, "NOTICE_DELIVERY_OUTCOME_UNKNOWN");
    }

    private void markEmailSent(NoticeWork work, String token, String providerReference) {
        Instant now = clock.instant();
        int changed = transactions.execute(status -> jdbc.update("""
            UPDATE deployment_admission_notice_deliveries
               SET state='SENT',provider_reference=?,failure_code=NULL,delivered_at=?,claim_token=NULL,
                   claim_expires_at=NULL,updated_at=?
             WHERE delivery_key=? AND channel='OWNER_EMAIL' AND state='PENDING' AND claim_token=?
            """, providerReference, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), work.deliveryKey(), token));
        if (changed != 1) throw failure(PRECONDITION_FAILED, "NOTICE_DELIVERY_OUTCOME_UNKNOWN");
    }

    private void markFailed(NoticeWork work, String token, String code) {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> jdbc.update("""
            UPDATE deployment_admission_notice_deliveries
               SET state='FAILED',failure_code=?,claim_token=NULL,claim_expires_at=NULL,updated_at=?
             WHERE delivery_key=? AND state='PENDING' AND claim_token=?
            """, code, java.sql.Timestamp.from(now), work.deliveryKey(), token));
    }

    private void markOutcomeUnknown(NoticeWork work, String token, String code) {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> jdbc.update("""
            UPDATE deployment_admission_notice_deliveries
               SET state='OUTCOME_UNKNOWN',failure_code=?,claim_expires_at=NULL,updated_at=?
             WHERE delivery_key=? AND state='PENDING' AND claim_token=?
            """, code, java.sql.Timestamp.from(now), work.deliveryKey(), token));
    }

    private void validate(Reservation reservation, NoticeWork work) {
        if (reservation == null || work == null || work.fence() == null || work.channel() == null
            || reservation.prerequisiteReadback() == null
            || !reservation.id().equals(work.fence().reservationId())
            || !reservation.generation().equals(work.fence().generation())
            || !reservation.noticeCycle().equals(work.noticeCycle())
            || !reservation.expiresAt().equals(work.expiresAt())
            || !properties.noticeTransportReference().equals(work.transportReference())
            || (work.channel() == NoticeChannel.CONSOLE && !properties.consoleEnabled())) {
            throw failure(PRECONDITION_FAILED, "STALE_NOTICE_CYCLE");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("NOTICE_FINGERPRINT_UNAVAILABLE", ex);
        }
    }

    private AdmissionFailure failure(org.springframework.http.HttpStatus status, String code) {
        return new AdmissionFailure(status, code);
    }

    Resolution resolveUnknown(String deliveryKey, boolean delivered, String evidenceReference) {
        if (deliveryKey == null || deliveryKey.isBlank() || deliveryKey.length() > 160
            || evidenceReference == null || evidenceReference.isBlank() || evidenceReference.length() > 255) {
            throw failure(PRECONDITION_FAILED, "NOTICE_RESOLUTION_INVALID");
        }
        return transactions.execute(status -> {
            var rows = jdbc.query("""
                SELECT reservation_id,notice_cycle,channel,state,resolution_reference,updated_at
                  FROM deployment_admission_notice_deliveries WHERE delivery_key=? FOR UPDATE
                """, (rs, row) -> new ResolutionRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getTimestamp(6).toInstant()),
                deliveryKey);
            if (rows.size() != 1 || !"OWNER_EMAIL".equals(rows.getFirst().channel())) {
                throw failure(PRECONDITION_FAILED, "NOTICE_RESOLUTION_NOT_ALLOWED");
            }
            ResolutionRow row = rows.getFirst();
            String expectedState = delivered ? "SENT" : "FAILED";
            if (expectedState.equals(row.state()) && evidenceReference.equals(row.resolutionReference())) {
                return new Resolution(deliveryKey, row.reservationId(), row.noticeCycle(), row.state(),
                    evidenceReference, row.updatedAt());
            }
            if (!"OUTCOME_UNKNOWN".equals(row.state())) throw failure(PRECONDITION_FAILED, "NOTICE_RESOLUTION_NOT_ALLOWED");
            Instant now = clock.instant();
            String nextState = expectedState;
            String provider = delivered ? "operator:" + hash(evidenceReference) : null;
            String failureCode = delivered ? null : "OPERATOR_CONFIRMED_NOT_DELIVERED";
            int changed = jdbc.update("""
                UPDATE deployment_admission_notice_deliveries
                   SET state=?,provider_reference=?,failure_code=?,resolution_reference=?,delivered_at=?,
                       claim_token=NULL,claim_expires_at=NULL,updated_at=?
                 WHERE delivery_key=? AND state='OUTCOME_UNKNOWN'
                """, nextState, provider, failureCode, evidenceReference,
                delivered ? java.sql.Timestamp.from(now) : null, java.sql.Timestamp.from(now), deliveryKey);
            if (changed != 1) throw failure(PRECONDITION_FAILED, "NOTICE_RESOLUTION_NOT_ALLOWED");
            return new Resolution(deliveryKey, row.reservationId(), row.noticeCycle(),
                nextState, evidenceReference, now);
        });
    }

    List<ConsoleNotice> consoleNotices(String customerId, String recipientReference, int requestedLimit) {
        if (customerId == null || customerId.isBlank() || recipientReference == null || recipientReference.isBlank()) {
            throw failure(PRECONDITION_FAILED, "NOTICE_CUSTOMER_SCOPE_REQUIRED");
        }
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return jdbc.query("""
            SELECT d.delivery_key,d.reservation_id,d.notice_cycle,d.safe_payload_json,
                   d.published_at,d.delivered_at
              FROM deployment_admission_notice_deliveries d
              JOIN deployment_capacity_reservations r ON r.id=d.reservation_id
             WHERE r.customer_id=? AND d.recipient_reference=? AND d.channel='CONSOLE'
               AND d.state IN ('PUBLISHED','SENT') AND d.published_at IS NOT NULL
             ORDER BY COALESCE(d.delivered_at,d.published_at) DESC,d.delivery_key DESC
             LIMIT ?
            """, (rs, row) -> new ConsoleNotice(rs.getString(1), rs.getString(2), rs.getString(3),
                json.read(rs.getString(4), JsonNode.class), rs.getTimestamp(5).toInstant(),
                rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()),
            customerId, recipientReference, limit);
    }

    record Resolution(String deliveryKey, String reservationId, String noticeCycle, String state,
                      String evidenceReference, Instant resolvedAt) { }
    record ConsoleNotice(String deliveryKey, String reservationId, String noticeCycle, JsonNode payload,
                         Instant publishedAt, Instant deliveredAt) { }
    private enum DeliveryDisposition { SEND, PUBLISHED, ALREADY_SENT, OUTCOME_UNKNOWN }
    private record DeliveryClaim(DeliveryDisposition disposition, String token) { }
    private record ClaimRow(String state, String token, Instant expiresAt) { }
    private record ResolutionRow(String reservationId, String noticeCycle, String channel, String state,
                                 String resolutionReference, Instant updatedAt) { }
    private record DeliveryRow(String state, String transportReference, String providerReference, Instant deliveredAt) { }
}
