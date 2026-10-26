package com.loomai.desk.service;

import static com.loomai.desk.model.DeskContracts.*;

import ai.fabric.chat.channel.ConversationChannelDispatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.channel.config.BasicInboxProperties;
import com.loomai.desk.channel.model.InboxContracts.*;
import com.loomai.desk.channel.persistence.BasicInboxRepository;
import com.loomai.desk.channel.service.BasicInboxService;
import com.loomai.desk.channel.service.InboxCallbackWorker;
import com.loomai.desk.channel.service.InboxSecurityService;
import com.loomai.desk.config.DeskProperties;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DeskChannelService {
  private static final Logger LOG = LoggerFactory.getLogger(DeskChannelService.class);
  private final DataSource ds;
  private final ObjectMapper mapper;
  private final DeskProperties properties;
  private final DeskDirectory directory;
  private final HttpClient client;

  public DeskChannelService(
      DataSource ds, ObjectMapper mapper, DeskProperties properties, DeskDirectory directory) {
    this.ds = ds;
    this.mapper = mapper;
    this.properties = properties;
    this.directory = directory;
    client =
        HttpClient.newBuilder()
            .connectTimeout(properties.getRequestTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public DispatchResponse dispatch(String key, String body) throws Exception {
    Binding binding = directory.provider(key);
    ConversationChannelDispatch dispatch =
        mapper.readValue(body, ConversationChannelDispatch.class);
    return engine(binding).service().dispatch(dispatch, body);
  }

  public <T> T support(
      String bindingId,
      Operator operator,
      String handoverId,
      String operation,
      Function<BasicInboxService, T> command) {
    Binding binding = directory.authorize(bindingId, operator, Role.SUPPORT);
    T result = command.apply(engine(binding).service());
    if (operation != null)
      directory.audit(bindingId, binding.customerId(), operator.id(), operation, handoverId);
    return result;
  }

  public OperatorIdentity channelOperator(Operator o, String binding) {
    return new OperatorIdentity(o.id(), o.displayName(), List.of(binding));
  }

  public Engine engine(Binding b) {
    BasicInboxProperties p = new BasicInboxProperties();
    p.setBindingId(b.bindingId());
    p.setTenantId(b.tenantId());
    p.setDeploymentId(b.deploymentId());
    p.setProviderApiKey(b.providerApiKey());
    p.setCallbackSigningSecret(b.callbackSigningSecret());
    p.setConnectorCallbackUrl(b.callbackUrl());
    p.setDataEncryptionSecret(properties.getDataEncryptionSecret());
    p.setRequestTimeout(properties.getRequestTimeout());
    p.setMaxCallbackAttempts(properties.getMaxCallbackAttempts());
    p.setCallbackRetryDelay(properties.getCallbackRetryDelay());
    p.setRetention(properties.getRetention());
    InboxSecurityService security = new InboxSecurityService(p);
    BasicInboxRepository repository = new BasicInboxRepository(ds, mapper, security, b.bindingId());
    return new Engine(
        new BasicInboxService(p, repository, security, mapper),
        repository,
        new InboxCallbackWorker(p, repository, security, mapper, client));
  }

  @Scheduled(fixedDelayString = "${DESK_CALLBACK_POLL_MS:1000}")
  public void callbacks() {
    for (String binding : directory.enabledBindings()) {
      try {
        engine(directory.binding(binding)).worker().deliverNext();
      } catch (Exception e) {
        LOG.warn(
            "Desk callback poll failed for binding {} ({})", binding, e.getClass().getSimpleName());
      }
    }
  }

  @Scheduled(cron = "${DESK_RETENTION_CRON:0 20 3 * * *}")
  public void retention() {
    for (String binding : directory.allBindings())
      engine(directory.binding(binding))
          .repository()
          .purgeBefore(Instant.now().minus(properties.getRetention()));
  }

  public record Engine(
      BasicInboxService service, BasicInboxRepository repository, InboxCallbackWorker worker) {}
}
