package com.loomai.desk;

import static com.loomai.desk.ticket.TicketContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.loomai.desk.ticket.TicketNotificationEmail;
import com.loomai.desk.ticket.TicketNotifications;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SupportTicketNotificationSmtpTest {
  @Test
  void realSmtpRetriesWithStableMessageIdAndOnlySafeReferenceAndProtectedLink() throws Exception {
    try (var smtp = new Capture();
        var f = new SupportTicketNotificationsTest.Fixture("h2")) {
      smtp.rejectFirst = true;
      JavaMailSenderImpl sender = new JavaMailSenderImpl();
      sender.setHost("127.0.0.1");
      sender.setPort(smtp.port());
      sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "3000");
      sender.getJavaMailProperties().put("mail.smtp.timeout", "3000");
      sender.getJavaMailProperties().put("mail.smtp.writetimeout", "3000");
      var beans = new StaticListableBeanFactory();
      beans.addBean("smtp", sender);
      var mail =
          new TicketNotificationEmail(
              beans.getBeanProvider(JavaMailSender.class), true, "support@loomai.pro");
      var notifications =
          new TicketNotifications(
              f.f.spaces, f.identities, mail, new DataSourceTransactionManager(f.f.db.dataSource));
      notifications.put(
          f.f.space, f.f.user, true, "https://page.example/support", "verified@example.com");
      var ticket = f.linked("smtp");
      f.f.tickets.change(
          f.f.space, f.f.user, ticket.id(), "RESOLVE", new VersionInput("0"), "smtp-resolve");
      notifications.collect();
      assertThat(notifications.deliverNext()).isTrue();
      assertThat(notifications.view(f.f.space, f.f.user).deliveryStatus()).isEqualTo("RETRY");
      f.f.jdbc.update(
          "UPDATE desk_support_notification_outbox SET next_attempt_at=?",
          Timestamp.from(Instant.now().minusSeconds(1)));
      assertThat(notifications.deliverNext()).isTrue();
      assertThat(notifications.view(f.f.space, f.f.user).deliveryStatus()).isEqualTo("SENT");
      assertThat(notifications.deliverNext()).isFalse();
      assertThat(smtp.messages).hasSize(2);
      assertThat(smtp.recipients)
          .allSatisfy(value -> assertThat(value).isEqualTo("RCPT TO:<verified@example.com>"));
      String messageId = null;
      for (String raw : smtp.messages) {
        var message =
            new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)));
        assertThat(message.getFrom()[0].toString()).isEqualTo("support@loomai.pro");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("verified@example.com");
        assertThat(message.getSubject()).isEqualTo("Support request update: " + ticket.reference());
        assertThat(message.getContent().toString())
            .contains(ticket.reference(), "https://page.example/support")
            .doesNotContain(
                "Secret subject",
                "Never email intake",
                "auth tokens",
                "Bearer",
                "access_token",
                "conversationId");
        if (messageId == null) messageId = message.getMessageID();
        assertThat(message.getMessageID())
            .isEqualTo(messageId)
            .matches("<desk-ticket-[a-f0-9]{64}@loomai.pro>");
      }
    }
  }

  static final class Capture implements AutoCloseable {
    final ServerSocket socket = new ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress());
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final List<String> messages = new CopyOnWriteArrayList<>();
    final List<String> recipients = new CopyOnWriteArrayList<>();
    volatile boolean rejectFirst;

    Capture() throws IOException {
      worker.submit(
          () -> {
            while (!socket.isClosed()) {
              try (var peer = socket.accept()) {
                peer.setSoTimeout(5000);
                var input =
                    new BufferedReader(
                        new InputStreamReader(peer.getInputStream(), StandardCharsets.ISO_8859_1));
                var output =
                    new PrintWriter(
                        new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.ISO_8859_1),
                        true);
                output.print("220 local-capture ESMTP\r\n");
                output.flush();
                String line;
                while ((line = input.readLine()) != null) {
                  if (line.startsWith("DATA")) {
                    output.print("354 End with dot\r\n");
                    output.flush();
                    var body = new StringBuilder();
                    while ((line = input.readLine()) != null && !line.equals("."))
                      body.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                    messages.add(body.toString());
                    output.print(
                        rejectFirst && messages.size() == 1
                            ? "451 retry later\r\n"
                            : "250 accepted\r\n");
                  } else if (line.startsWith("QUIT")) {
                    output.print("221 bye\r\n");
                    output.flush();
                    break;
                  } else {
                    if (line.startsWith("RCPT TO:")) recipients.add(line);
                    output.print("250 OK\r\n");
                  }
                  output.flush();
                }
              } catch (IOException ignored) {
                if (!socket.isClosed()) throw new UncheckedIOException(ignored);
              }
            }
          });
    }

    int port() {
      return socket.getLocalPort();
    }

    public void close() throws Exception {
      socket.close();
      worker.shutdownNow();
      worker.awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
