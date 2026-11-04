package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.loomai.desk.channel.service.InboxSecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
public class RequesterSecurityConfiguration {
  @Bean
  @Order(1)
  SecurityFilterChain requesterSecurity(
      HttpSecurity http, SupportSpaces spaces, RequesterIdentityService identities)
      throws Exception {
    http.securityMatcher("/api/desk/requester/v1/**", "/api/desk/integrations/v1/**")
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(c -> c.disable())
        .requestCache(c -> c.disable())
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .exceptionHandling(
            e -> e.authenticationEntryPoint((request, response, error) -> response.sendError(401)));
    http.addFilterBefore(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Vary", "Origin");
            try {
              String path = request.getServletPath();
              String[] pieces = path.split("/");
              if (pieces.length < 7 || !"spaces".equals(pieces[5])) throw TicketException.missing();
              Space space = spaces.available(pieces[6]);
              String origin = request.getHeader("Origin");
              if (origin != null) {
                if (!space.view().allowedOrigins().contains(origin))
                  throw new TicketException(
                      403, "REQUESTER_ORIGIN_DENIED", "This origin is not permitted.");
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
                response.setHeader(
                    "Access-Control-Allow-Headers",
                    "Authorization, Content-Type, Idempotency-Key, X-LoomAI-Runtime-Authorization");
              }
              if ("OPTIONS".equals(request.getMethod())) {
                response.setStatus(204);
                return;
              }
              boolean integration = path.startsWith("/api/desk/integrations/");
              if (integration
                  && !InboxSecurityService.constantTime(
                      space.connectorKey(), request.getHeader("X-LoomAI-Ticket-Connector-Key")))
                throw new TicketException(
                    401,
                    "TICKET_CONNECTOR_AUTH_INVALID",
                    "Ticket connector authentication is required.");
              // These are explicit server-to-server routes, never a cookie or role fallback.
              if ((integration || space.identity().mode() == IdentityMode.TRUSTED_PRIVATE_HOST)
                  && origin != null)
                throw new TicketException(
                    403,
                    "PRIVATE_REQUESTER_BROWSER_DENIED",
                    "Private requester credentials cannot be used from a browser.");
              String operation = integration ? "TICKET_COMMAND" : operation(request);
              Requester requester =
                  path.equals(
                          "/api/desk/integrations/v1/spaces/"
                              + space.view().spaceId()
                              + "/server-ticket-commands")
                      ? identities.authenticateServerConnector(space, request)
                      : identities.authenticate(
                          space,
                          request.getHeader("Authorization"),
                          request.getHeader("X-LoomAI-Requester-Server-Key"),
                          operation);
              if (!space.view().enabled()
                  && !java.util.Set.of("SESSION", "TEAM").contains(operation))
                throw new TicketException(
                    409, "SUPPORT_SPACE_DISABLED", "Ticket operations are disabled.");
              request.setAttribute("ticket.space", space);
              request.setAttribute("ticket.requester", requester);
              if (path.endsWith("/server-ticket-commands"))
                request.setAttribute(
                    "ticket.runtimeTrace",
                    new VerifiedRuntimeTrace(
                        space.view().spaceId(),
                        requester.id(),
                        requester.subject(),
                        requester.runtimeConversationId()));
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      new UsernamePasswordAuthenticationToken(requester, null, List.of()));
              chain.doFilter(request, response);
            } catch (TicketException error) {
              if (!response.isCommitted()) {
                response.setStatus(error.status());
                response.setContentType("application/json");
                response
                    .getWriter()
                    .write(
                        spaces.write(
                            Map.of(
                                "success",
                                false,
                                "code",
                                error.code(),
                                "message",
                                error.getMessage())));
              }
            } finally {
              SecurityContextHolder.clearContext();
            }
          }
        },
        UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private String operation(HttpServletRequest r) {
    String path = r.getServletPath();
    String last = path.substring(path.lastIndexOf('/') + 1);
    return switch (last) {
      case "session" -> "SESSION";
      case "team", "photo" -> "TEAM";
      case "start" -> "START";
      case "resolve" -> "RESOLVE";
      case "cancel" -> "CANCEL";
      case "reopen" -> "REOPEN";
      case "seen" -> "SEEN";
      case "activity" -> "ACTIVITY";
      case "notifications" -> "NOTIFICATIONS";
      case "link", "episodes" -> "LINK_CONVERSATION";
      case "requests" -> "GET".equals(r.getMethod()) ? "LIST" : "CREATE";
      default -> "GET";
    };
  }
}
