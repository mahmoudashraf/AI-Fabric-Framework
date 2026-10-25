package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;

/** Resumes only accepted native commands. It does not select or schedule operational policy. */
@Service
public class LifecycleCommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(LifecycleCommandExecutor.class);
    private final LifecycleCommandStore store;
    private final LifecycleEffects effects;
    private final Executor executor;
    public LifecycleCommandExecutor(LifecycleCommandStore store, LifecycleEffects effects,
        @Qualifier("lifecycleNativeExecutor") Executor executor) {
        this.store = store; this.effects = effects; this.executor = executor;
    }
    public Receipt submit(Command command) {
        Receipt receipt;
        try { receipt = store.accept(command); }
        catch (ResponseStatusException rejected) {
            if (rejected.getReason() == null) throw rejected;
            return store.rejection(command, rejected.getReason());
        }
        dispatch(command.commandId());
        return receipt;
    }
    public Receipt poll(String commandId) {
        Receipt receipt = store.receipt(commandId);
        if (receipt.status() == Status.PENDING || receipt.status() == Status.UNKNOWN) dispatch(commandId);
        return receipt;
    }
    public void dispatch(String commandId) {
        // Claim before queueing so repeated polling cannot fill the shared executor queue.
        var work = store.claim(commandId);
        if (work == null) return;
        try { executor.execute(() -> execute(work)); }
        catch (RuntimeException unavailable) { store.finish(work, Status.PENDING, "NATIVE_EXECUTOR_UNAVAILABLE", List.of()); }
    }
    @Scheduled(fixedDelayString = "${platform.deployment-lifecycle.command-poll-ms:10000}")
    public void resumeAcceptedCommands() {
        // Disabling the optional machine gateway cannot erase an accepted native command or its readback obligation.
        store.pending().forEach(this::dispatch);
    }
    private void execute(LifecycleCommandStore.Work work) {
        try {
            if (work.command().phase() != Phase.CANCEL) effects.requireUnchanged(work.grant().scope());
            var result = effects.execute(work);
            store.finish(work, result.status(), result.code(), result.blockers());
        } catch (ResponseStatusException failure) {
            String code = failure.getReason();
            if (code == null || !code.matches("[A-Z0-9_]{1,128}")) code = "NATIVE_EXECUTION_BLOCKED";
            store.finish(work, Status.BLOCKED, code, List.of(code));
        } catch (RuntimeException unknown) {
            String location = java.util.Arrays.stream(unknown.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("com.ai.fabric."))
                .findFirst().map(StackTraceElement::toString).orElse("unavailable");
            log.warn("Native lifecycle command unresolved operationId={} phase={} exceptionType={} source={}",
                work.command().operationId(), work.command().phase(), unknown.getClass().getName(), location);
            store.finish(work, Status.UNKNOWN, "NATIVE_OUTCOME_UNKNOWN", List.of("NATIVE_OUTCOME_UNKNOWN"));
        }
    }
}
