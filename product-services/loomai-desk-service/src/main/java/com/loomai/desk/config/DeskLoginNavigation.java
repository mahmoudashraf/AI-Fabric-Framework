package com.loomai.desk.config;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Fixed same-origin navigation only. The binding hint never grants resource access. */
@Component
public class DeskLoginNavigation implements AuthenticationSuccessHandler {
  private static final String BINDING_ATTRIBUTE =
      DeskLoginNavigation.class.getName() + ".bindingId";
  private static final Pattern BINDING_ID = Pattern.compile("[A-Za-z0-9_-]{1,160}");

  public OAuth2AuthorizationRequestResolver preservingBinding(
      OAuth2AuthorizationRequestResolver delegate) {
    return new OAuth2AuthorizationRequestResolver() {
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        remember(request);
        return delegate.resolve(request);
      }

      public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        remember(request);
        return delegate.resolve(request, registrationId);
      }
    };
  }

  private void remember(HttpServletRequest request) {
    String binding = request.getParameter("bindingId");
    if (binding == null) return;
    if (!BINDING_ID.matcher(binding).matches())
      throw new OAuth2AuthenticationException(
          new OAuth2Error("invalid_request"), "Invalid binding navigation hint.");
    request.getSession(true).setAttribute(BINDING_ATTRIBUTE, binding);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      org.springframework.security.core.Authentication authentication)
      throws IOException {
    Object hint = request.getSession().getAttribute(BINDING_ATTRIBUTE);
    request.getSession().removeAttribute(BINDING_ATTRIBUTE);
    String target =
        hint instanceof String binding && BINDING_ID.matcher(binding).matches()
            ? "/?bindingId=" + binding
            : "/";
    response.sendRedirect(target);
  }
}
