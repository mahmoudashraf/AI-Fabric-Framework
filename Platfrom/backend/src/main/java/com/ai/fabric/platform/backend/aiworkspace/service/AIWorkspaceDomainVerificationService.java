package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Completes explicit Verify intent; never starts ownership checks for new PENDING domains. */
@Service
public class AIWorkspaceDomainVerificationService {
    private static final String ROUTE_PENDING = "The shared frontend route is not applied yet.";
    private final AIWorkspaceDomainRepository domains;
    private final EntityManager entities;
    private final AIWorkspaceDomainVerifier verifier;
    private final AIWorkspaceDomainRouteService routes;
    private final PlatformAuditService audit;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate transaction;
    private final ExecutorService probes;
    private final Duration probeTimeout;

    @Autowired
    public AIWorkspaceDomainVerificationService(AIWorkspaceDomainRepository domains, EntityManager entities,
                                               AIWorkspaceDomainVerifier verifier, AIWorkspaceDomainRouteService routes,
                                               PlatformAuditService audit, PlatformTransactionManager transactions) {
        this(domains, entities, verifier, routes, audit, transactions,
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), task -> {
                var thread = new Thread(task, "ai-workspace-domain-probe");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy()), Duration.ofSeconds(30));
    }

    AIWorkspaceDomainVerificationService(AIWorkspaceDomainRepository domains, EntityManager entities,
                                         AIWorkspaceDomainVerifier verifier, AIWorkspaceDomainRouteService routes,
                                         PlatformAuditService audit, PlatformTransactionManager transactions,
                                         ExecutorService probes, Duration probeTimeout) {
        this.domains = domains;
        this.entities = entities;
        this.verifier = verifier;
        this.routes = routes;
        this.audit = audit;
        this.probes = probes;
        this.probeTimeout = probeTimeout;
        readTransaction = new TransactionTemplate(transactions);
        readTransaction.setReadOnly(true);
        transaction = new TransactionTemplate(transactions);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runNext() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Domain probes cannot run in a database transaction");
        }
        Candidate candidate = readTransaction.execute(ignored -> {
            var pending = domains.findVerificationCandidates(AIWorkspaceDomainStatus.ROUTING_PENDING,
                AIWorkspaceDeliveryMode.STANDALONE, AIWorkspaceInstallationStatus.DISABLED, PageRequest.of(0, 1));
            if (pending.isEmpty()) return null;
            var domain = pending.get(0);
            var installation = entities.find(AIWorkspaceInstallationEntity.class, domain.getInstallationEntityId());
            if (!eligible(installation) || domain.getStatus() != AIWorkspaceDomainStatus.ROUTING_PENDING) return null;
            return new Candidate(domain.getId(), domain.getInstallationEntityId(), domain.getHostname(),
                domain.getVerificationToken(), domain.getDomainType(), domain.getRowVersion(), installation.getRowVersion());
        });
        if (candidate == null) return;

        // Route application is proven by the existing durable worker, not by DNS or an HTTP response alone.
        Outcome outcome = routes.isApplied(candidate.hostname()) ? probe(candidate)
            : new Outcome(AIWorkspaceDomainStatus.ROUTING_PENDING, ROUTE_PENDING);
        if (outcome == null) return;
        transaction.executeWithoutResult(ignored -> complete(candidate, outcome));
    }

    private Outcome probe(Candidate candidate) {
        Future<Outcome> attempt;
        try {
            attempt = probes.submit(() -> {
                if (candidate.type() == AIWorkspaceDomainType.CUSTOMER_CUSTOM) {
                    var ownership = verifier.verifyOwnership(candidate.hostname(), candidate.token());
                    if (!ownership.verified()) return new Outcome(AIWorkspaceDomainStatus.PENDING, ownership.message());
                }
                var route = verifier.verifyRoute(candidate.hostname());
                if (!route.verified()) return new Outcome(AIWorkspaceDomainStatus.ROUTING_PENDING, route.message());
                if (candidate.type() == AIWorkspaceDomainType.CUSTOMER_CUSTOM) {
                    var ownership = verifier.verifyOwnership(candidate.hostname(), candidate.token());
                    if (!ownership.verified()) return new Outcome(AIWorkspaceDomainStatus.PENDING, ownership.message());
                }
                return new Outcome(AIWorkspaceDomainStatus.VERIFIED, null);
            });
        } catch (RejectedExecutionException ex) {
            // A timed-out DNS call may ignore interruption. Never queue more probes behind it.
            return null;
        }
        try {
            return attempt.get(probeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            attempt.cancel(true);
            Thread.currentThread().interrupt();
            return null;
        } catch (TimeoutException ex) {
            attempt.cancel(true);
            return new Outcome(AIWorkspaceDomainStatus.ROUTING_PENDING, "Domain verification timed out; it will be retried.");
        } catch (ExecutionException ex) {
            return new Outcome(AIWorkspaceDomainStatus.ROUTING_PENDING, "Domain verification is not ready; it will be retried.");
        }
    }

    private void complete(Candidate candidate, Outcome outcome) {
        // Serialize with app disable/delete, then reject any domain/app edit made while network IO was in flight.
        var installation = entities.find(AIWorkspaceInstallationEntity.class, candidate.installationId(), LockModeType.PESSIMISTIC_WRITE);
        if (!eligible(installation) || installation.getRowVersion() != candidate.installationVersion()) return;
        var domain = entities.find(AIWorkspaceDomainEntity.class, candidate.id(), LockModeType.PESSIMISTIC_WRITE);
        if (domain == null || domain.getStatus() != AIWorkspaceDomainStatus.ROUTING_PENDING
            || domain.getRowVersion() != candidate.domainVersion()
            || !candidate.installationId().equals(domain.getInstallationEntityId())
            || !candidate.hostname().equals(domain.getHostname()) || !candidate.token().equals(domain.getVerificationToken())
            || candidate.type() != domain.getDomainType()) return;
        boolean verified = outcome.status() == AIWorkspaceDomainStatus.VERIFIED && routes.isApplied(candidate.hostname());
        domain.setStatus(outcome.status() == AIWorkspaceDomainStatus.VERIFIED && !verified
            ? AIWorkspaceDomainStatus.ROUTING_PENDING : outcome.status());
        domain.setLastVerificationError(outcome.status() == AIWorkspaceDomainStatus.VERIFIED && !verified
            ? ROUTE_PENDING : outcome.message());
        domain.setUpdatedAt(Instant.now());
        domain.setVerifiedAt(verified ? domain.getUpdatedAt() : null);
        domains.saveAndFlush(domain);
        if (verified) audit.record("AI_WORKSPACE_DOMAIN_VERIFICATION_COMPLETED", "AI_WORKSPACE_INSTALLATION",
            installation.getId(), Map.of("hostname", domain.getHostname(), "status", domain.getStatus().name()));
    }

    private static boolean eligible(AIWorkspaceInstallationEntity installation) {
        return installation != null && installation.getStatus() != AIWorkspaceInstallationStatus.DISABLED
            && installation.getDeliveryMode() == AIWorkspaceDeliveryMode.STANDALONE;
    }

    @PreDestroy
    public void close() {
        probes.shutdownNow();
        try { probes.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }

    private record Candidate(String id, String installationId, String hostname, String token, AIWorkspaceDomainType type,
                             long domainVersion, long installationVersion) { }
    private record Outcome(AIWorkspaceDomainStatus status, String message) { }
}
