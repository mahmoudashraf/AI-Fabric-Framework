package com.loomai.desk.ticket;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class TicketNotificationEmail {
  private final JavaMailSender sender;
  private final String from;
  private final boolean enabled;

  public TicketNotificationEmail(
      ObjectProvider<JavaMailSender> sender,
      @Value("${loomai.desk.email.enabled:false}") boolean enabled,
      @Value("${loomai.desk.email.from:}") String from) {
    this.sender = sender.getIfAvailable();
    this.from = from;
    this.enabled = enabled;
    if (enabled) {
      try {
        if (this.sender == null
            || from == null
            || from.isBlank()
            || from.contains("\r")
            || from.contains("\n")) throw new IllegalArgumentException();
        InternetAddress address = new InternetAddress(from, true);
        address.validate();
      } catch (Exception invalid) {
        throw new IllegalStateException(
            "Desk email requires a configured SMTP sender and fixed From address.");
      }
    }
  }

  public boolean available() {
    return enabled && sender != null;
  }

  public void send(String id, String recipient, String reference, String returnUrl)
      throws Exception {
    MimeMessage draft = sender.createMimeMessage();
    var helper = new MimeMessageHelper(draft, StandardCharsets.UTF_8.name());
    helper.setFrom(from);
    helper.setTo(recipient);
    helper.setSubject("Support request update: " + reference);
    helper.setText(
        "There is an update to support request "
            + reference
            + ".\n\nSign in to view it:\n"
            + returnUrl
            + "\n\n"
            + "This email contains no conversation content. Manage notifications in My Requests.",
        false);
    // JavaMailSender preserves an explicitly supplied ID across its saveChanges call.
    draft.setHeader("Message-ID", "<desk-ticket-" + id + "@loomai.pro>");
    sender.send(draft);
  }
}
