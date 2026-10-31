package com.loomai.desk.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loomai.desk")
public class DeskProperties {
  private String serviceKey;
  private String dataEncryptionSecret;
  private String supabaseUrl;
  private String supabasePublishableKey;
  private String identityPortalUrl = "";
  private String issuer;
  private String jwksUri;
  private List<String> audiences = List.of("authenticated");
  private String clientId;
  private String clientSecret;
  private String oidcSigningAlgorithm = "ES256";
  private Duration requestTimeout = Duration.ofSeconds(10);
  private int maxCallbackAttempts = 8;
  private Duration callbackRetryDelay = Duration.ofSeconds(5);
  private Duration retention = Duration.ofDays(30);
  private List<String> conversationContextAllowedOrigins = List.of();
  private boolean conversationContextAllowInsecureLoopback;

  public List<String> getConversationContextAllowedOrigins() {
    return conversationContextAllowedOrigins;
  }

  public void setConversationContextAllowedOrigins(List<String> v) {
    conversationContextAllowedOrigins = v == null ? List.of() : List.copyOf(v);
  }

  public boolean isConversationContextAllowInsecureLoopback() {
    return conversationContextAllowInsecureLoopback;
  }

  public void setConversationContextAllowInsecureLoopback(boolean v) {
    conversationContextAllowInsecureLoopback = v;
  }

  public String getServiceKey() {
    return serviceKey;
  }

  public void setServiceKey(String v) {
    serviceKey = v;
  }

  public String getDataEncryptionSecret() {
    return dataEncryptionSecret;
  }

  public void setDataEncryptionSecret(String v) {
    dataEncryptionSecret = v;
  }

  public String getSupabaseUrl() {
    return supabaseUrl;
  }

  public void setSupabaseUrl(String v) {
    supabaseUrl = v;
  }

  public String getSupabasePublishableKey() {
    return supabasePublishableKey;
  }

  public void setSupabasePublishableKey(String v) {
    supabasePublishableKey = v;
  }

  public String getIdentityPortalUrl() {
    return identityPortalUrl;
  }

  public void setIdentityPortalUrl(String v) {
    identityPortalUrl = v == null ? "" : v.trim();
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String v) {
    issuer = v;
  }

  public String getJwksUri() {
    return jwksUri;
  }

  public void setJwksUri(String v) {
    jwksUri = v;
  }

  public List<String> getAudiences() {
    return audiences;
  }

  public void setAudiences(List<String> v) {
    audiences = v;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String v) {
    clientId = v;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String v) {
    clientSecret = v;
  }

  public String getOidcSigningAlgorithm() {
    return oidcSigningAlgorithm;
  }

  public void setOidcSigningAlgorithm(String v) {
    oidcSigningAlgorithm = v;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Duration v) {
    requestTimeout = v;
  }

  public int getMaxCallbackAttempts() {
    return maxCallbackAttempts;
  }

  public void setMaxCallbackAttempts(int v) {
    maxCallbackAttempts = v;
  }

  public Duration getCallbackRetryDelay() {
    return callbackRetryDelay;
  }

  public void setCallbackRetryDelay(Duration v) {
    callbackRetryDelay = v;
  }

  public Duration getRetention() {
    return retention;
  }

  public void setRetention(Duration v) {
    retention = v;
  }
}
