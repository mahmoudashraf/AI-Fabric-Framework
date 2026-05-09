package com.ai.fabric.platform.backend.partner.gateway;

import com.ai.fabric.platform.backend.partner.config.PartnerMerchantEmailNotificationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class SmtpPartnerNotificationGateway implements PartnerNotificationGateway {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final PartnerMerchantEmailNotificationProperties properties;

    public SmtpPartnerNotificationGateway(ObjectProvider<JavaMailSender> mailSender,
                                          PartnerMerchantEmailNotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public PartnerNotificationDeliverySummary sendMerchantAccessInvite(MerchantAccessInviteMessage message) {
        String subject = subject("Approve Loom Companion partner access");
        String body = """
            A Loom Companion implementation partner requested scoped setup access for %s.

            Review and approve or deny the request here:
            %s

            This link expires at %s. If you did not expect this request, ignore this email or deny the request from Shopify Admin.
            """.formatted(
            nullToFallback(message.shopDomain(), "your Shopify store"),
            message.approvalUrl(),
            formatInstant(message.expiresAt())
        );
        return send(message.recipientEmail(), subject, body);
    }

    @Override
    public PartnerNotificationDeliverySummary notifyPartnerAccessDecision(PartnerAccessDecisionNotification message) {
        String subject = subject("Merchant partner access " + nullToFallback(message.status(), "updated"));
        String body = """
            Merchant access for %s is now %s.

            %s
            """.formatted(
            nullToFallback(message.shopDomain(), "the Shopify store"),
            nullToFallback(message.status(), "updated"),
            nullToFallback(message.message(), "Open the Partner Portal for the latest workflow state.")
        );
        return send(message.recipientEmail(), subject, body);
    }

    private PartnerNotificationDeliverySummary send(String recipientEmail, String subject, String body) {
        String recipient = requireText(recipientEmail, "recipientEmail");
        Instant now = Instant.now();
        if (!properties.enabled() || properties.dryRun()) {
            return new PartnerNotificationDeliverySummary(
                "RECORDED",
                properties.enabled() ? "SMTP_DRY_RUN" : "EMAIL_DISABLED",
                recipient,
                properties.enabled()
                    ? "Email dry-run is enabled; notification was recorded but not sent."
                    : "Email delivery is disabled; notification was recorded but not sent.",
                now
            );
        }
        String from = requireText(properties.from(), "platform.notifications.merchant-email.from");
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Email delivery is enabled but no mail sender is configured.");
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(recipient);
            mail.setFrom(from);
            if (StringUtils.hasText(properties.replyTo())) {
                mail.setReplyTo(properties.replyTo().trim());
            }
            mail.setSubject(subject);
            mail.setText(body);
            sender.send(mail);
            return new PartnerNotificationDeliverySummary("SENT", "SMTP", recipient, "Email sent.", now);
        } catch (MailException exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Email delivery failed.");
        }
    }

    private String subject(String suffix) {
        String prefix = StringUtils.hasText(properties.subjectPrefix())
            ? properties.subjectPrefix().trim()
            : "Loom Companion";
        return prefix + " - " + suffix;
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "the configured expiry time";
        }
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC));
    }

    private String nullToFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, field + " is required for email delivery.");
        }
        return value.trim();
    }
}
