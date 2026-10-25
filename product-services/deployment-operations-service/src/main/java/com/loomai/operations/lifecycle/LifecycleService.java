package com.loomai.operations.lifecycle;

import static com.loomai.operations.lifecycle.LifecycleContracts.*;

import com.loomai.operations.config.OperationsProperties;
import com.loomai.operations.lifecycle.LifecycleStore.JobView;
import com.loomai.operations.service.OperationsException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LifecycleService {
    private static final Logger log = LoggerFactory.getLogger(LifecycleService.class);
    private final LifecycleStore store;
    private final LifecycleGateway gateway;
    private final HttpLifecycleGateway webhook;
    private final LifecycleProperties properties;
    private final OperationsProperties bounds;
    private final Validator validator;
    private final Clock clock;

    public LifecycleService(LifecycleStore store, LifecycleGateway gateway, HttpLifecycleGateway webhook,
                            LifecycleProperties properties, OperationsProperties bounds, Validator validator, Clock clock) {
        this.store = store; this.gateway = gateway; this.webhook = webhook;
        this.properties = properties; this.bounds = bounds; this.validator = validator; this.clock = clock;
    }

    public JobView register(JobRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) throw invalid();
        return store.register(request, () -> {
            var now = clock.instant();
            try {
                if (request.requestedAt().isBefore(now.minus(bounds.maxRequestAge()))
                    || request.requestedAt().isAfter(now.plus(bounds.maxClockSkew()))
                    || request.dueAt().isBefore(request.requestedAt().minus(bounds.maxRequestAge()))
                    || request.dueAt().isAfter(request.requestedAt().plus(Duration.ofDays(366)))
                    || !request.deadlineAt().isAfter(request.dueAt())
                    || request.deadlineAt().isAfter(request.dueAt().plus(Duration.ofDays(7)))) throw invalid();
            } catch (java.time.DateTimeException | ArithmeticException exception) { throw invalid(); }
        });
    }

    public JobView advance(String operationId) { return advance(operationId, false); }
    public JobView retry(String operationId) { return advance(operationId, true); }
    public JobView cancel(String operationId) {
        store.requestCancel(operationId);
        return advance(operationId, false);
    }

    private JobView advance(String operationId, boolean retry) {
        if (!gateway.configured()) throw new OperationsException(503, "LIFECYCLE_GATEWAY_NOT_CONFIGURED", "Core lifecycle gateway is unavailable.");
        var acquired = store.acquire(operationId, retry);
        if (acquired.isEmpty()) return store.status(operationId);
        var lease = acquired.get();
        if (lease.submit() && !store.markSent(lease)) return store.status(operationId);
        try {
            Receipt receipt = lease.submit() ? gateway.submit(lease.command()) : gateway.observe(lease.command());
            if (!valid(lease, receipt)) throw new IllegalStateException("Invalid core receipt.");
            store.received(lease, receipt);
        } catch (RuntimeException exception) {
            // Unknown dispatches, including an uncertain explicit BLOCKED replay, resume by GET only.
            store.unknown(lease);
        }
        return store.status(operationId);
    }

    private boolean valid(LifecycleStore.Lease lease, Receipt receipt) {
        if (receipt == null || !validator.validate(receipt).isEmpty()) return false;
        var command = lease.command();
        return command.schemaVersion().equals(receipt.schemaVersion()) && command.commandId().equals(receipt.commandId())
            && command.operationId().equals(receipt.operationId()) && command.grantId().equals(receipt.grantId())
            && command.phase() == receipt.phase()
            && !receipt.observedAt().isAfter(clock.instant().plus(bounds.maxClockSkew()))
            && !receipt.observedAt().isBefore(lease.request().requestedAt().minus(bounds.maxClockSkew()));
    }

    @Scheduled(fixedDelayString = "${loomai.deployment-operations.lifecycle.scheduler-interval:PT5S}")
    public void scheduledTick() {
        if (!properties.schedulerEnabled()) return;
        try {
            store.notices();
            for (String id : store.eligible()) {
                try { advance(id); }
                catch (RuntimeException exception) { log.warn("Lifecycle job continuation unavailable for {}", id); }
            }
            dispatchNotices();
        } catch (RuntimeException exception) { log.warn("Lifecycle scheduler storage or gateway is unavailable"); }
    }

    public int dispatchNotices() {
        if (properties.webhookUrl().isBlank()) return 0;
        int delivered = 0;
        for (int index = 0; index < 20; index++) {
            var claimed = store.claimDelivery();
            if (claimed.isEmpty()) break;
            var delivery = claimed.get(); boolean success = false;
            try { webhook.deliver(delivery.event(), delivery.event().eventId()); success = true; delivered++; }
            catch (RuntimeException exception) { /* Durable outbox retries the same event identity, not the job. */ }
            store.delivered(delivery, success);
        }
        return delivered;
    }

    private OperationsException invalid() {
        return new OperationsException(400, "LIFECYCLE_JOB_INVALID", "Lifecycle scope or timestamp bounds are invalid.");
    }
}
