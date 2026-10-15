package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionAdmissionNoticeDeliverySourceTest extends AdmissionFixture {
    private final AdmissionOwnerContactResolver owners = mock(AdmissionOwnerContactResolver.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private ProductionAdmissionNoticeDeliverySource source;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void deliverySource() {
        new ResourceDatabasePopulator(new ClassPathResource(
            "db/test-migration/V120_18__admission_notice_delivery_outbox_test.sql")).execute(dataSource);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        var properties = new AdmissionLifecycleProperties(true, Duration.ofSeconds(1), Duration.ofSeconds(1),
            8, "approved-mail", true, true, "notices@loomai.test", true);
        source = new ProductionAdmissionNoticeDeliverySource(jdbc, new DataSourceTransactionManager(dataSource),
            owners, properties, provider, json, Clock.fixed(NOW, ZoneOffset.UTC));
        when(owners.resolve("customer", "verified-owner")).thenReturn(
            new AdmissionOwnerContactResolver.OwnerContact("owner-1", "owner@example.test", "platform-user:owner-1"));
    }

    @Test
    void consoleAndEmailReceiptsAreDurableIdempotentAndDoNotPersistRecipientAddress() {
        Reservation reservation = one();
        NoticeWork console = work(reservation, NoticeChannel.CONSOLE);
        NoticeWork email = work(reservation, NoticeChannel.OWNER_EMAIL);

        source.deliver(reservation, console);
        source.deliver(reservation, email);
        source.deliver(reservation, email);

        assertThat(source.read(console).deliveryKey()).isEqualTo(console.deliveryKey());
        assertThat(source.read(email).deliveryKey()).isEqualTo(email.deliveryKey());
        verify(sender, times(1)).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_notice_deliveries WHERE state='SENT'", Long.class))
            .isEqualTo(2);
        String stored = jdbc.queryForObject("""
            SELECT safe_payload_json || recipient_reference || recipient_hash
              FROM deployment_admission_notice_deliveries WHERE delivery_key=?
            """, String.class, email.deliveryKey());
        assertThat(stored).doesNotContain("owner@example.test");
        List<ProductionAdmissionNoticeDeliverySource.ConsoleNotice> inbox =
            source.consoleNotices("customer", "platform-user:owner-1", 10);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.getFirst().deliveryKey()).isEqualTo(console.deliveryKey());
        assertThat(inbox.getFirst().payload().path("deploymentId").asText()).isEqualTo(reservation.deploymentId());
        assertThat(inbox.getFirst().publishedAt()).isEqualTo(NOW);
    }

    @Test
    void restartDoesNotResendAnEmailWhoseRemoteOutcomeIsUnknown() {
        Reservation reservation = one();
        NoticeWork email = work(reservation, NoticeChannel.OWNER_EMAIL);
        jdbc.update("""
            INSERT INTO deployment_admission_notice_deliveries
              (delivery_key,reservation_id,notice_cycle,channel,transport_reference,recipient_reference,
               recipient_hash,state,attempt_count,safe_payload_json,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """, email.deliveryKey(), reservation.id(), email.noticeCycle(), email.channel().name(),
            email.transportReference(), "platform-user:owner-1", "hash", "PENDING", 1, "{}",
            java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));

        assertThatThrownBy(() -> source.deliver(reservation, email))
            .isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("NOTICE_DELIVERY_OUTCOME_UNKNOWN");
        verify(sender, times(0)).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("SELECT state FROM deployment_admission_notice_deliveries WHERE delivery_key=?",
            String.class, email.deliveryKey())).isEqualTo("OUTCOME_UNKNOWN");
    }

    @Test
    void expiredConsoleClaimIsSafelyRepublishedButEmailRequiresOperatorResolution() {
        Reservation reservation = one();
        NoticeWork console = work(reservation, NoticeChannel.CONSOLE);
        jdbc.update("""
            INSERT INTO deployment_admission_notice_deliveries
              (delivery_key,reservation_id,notice_cycle,channel,transport_reference,recipient_reference,
               recipient_hash,state,attempt_count,safe_payload_json,claim_token,claim_expires_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, console.deliveryKey(), reservation.id(), console.noticeCycle(), console.channel().name(),
            console.transportReference(), "platform-user:owner-1", "hash", "PENDING", 1,
            "{\"deploymentId\":\"deployment1\"}", "expired-token", java.sql.Timestamp.from(NOW.minusSeconds(1)),
            java.sql.Timestamp.from(NOW.minusSeconds(10)), java.sql.Timestamp.from(NOW.minusSeconds(10)));

        source.deliver(reservation, console);

        assertThat(source.read(console).deliveryKey()).isEqualTo(console.deliveryKey());
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM deployment_admission_notice_deliveries WHERE delivery_key=?",
            Integer.class, console.deliveryKey())).isEqualTo(2);
    }

    @Test
    void operatorResolutionIsDurableIdempotentAndCannotBeChangedOnReplay() {
        Reservation reservation = one();
        NoticeWork email = work(reservation, NoticeChannel.OWNER_EMAIL);
        jdbc.update("""
            INSERT INTO deployment_admission_notice_deliveries
              (delivery_key,reservation_id,notice_cycle,channel,transport_reference,recipient_reference,
               recipient_hash,state,attempt_count,safe_payload_json,created_at,updated_at,failure_code)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, email.deliveryKey(), reservation.id(), email.noticeCycle(), email.channel().name(),
            email.transportReference(), "platform-user:owner-1", "hash", "OUTCOME_UNKNOWN", 1, "{}",
            java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW), "NOTICE_EMAIL_OUTCOME_UNKNOWN");

        var resolved = source.resolveUnknown(email.deliveryKey(), true, "smtp-provider-receipt-1");
        var replay = source.resolveUnknown(email.deliveryKey(), true, "smtp-provider-receipt-1");

        assertThat(resolved.state()).isEqualTo("SENT");
        assertThat(replay).isEqualTo(resolved);
        assertThat(source.read(email).deliveryKey()).isEqualTo(email.deliveryKey());
        assertThatThrownBy(() -> source.resolveUnknown(email.deliveryKey(), false, "different-evidence"))
            .hasMessageContaining("NOTICE_RESOLUTION_NOT_ALLOWED");
    }

    private NoticeWork work(Reservation reservation, NoticeChannel channel) {
        return new NoticeWork(reservation.fence(), reservation.noticeCycle(), channel,
            reservation.id() + ":" + reservation.noticeCycle() + ":" + channel.name(),
            reservation.expiresAt(), "approved-mail");
    }
}
