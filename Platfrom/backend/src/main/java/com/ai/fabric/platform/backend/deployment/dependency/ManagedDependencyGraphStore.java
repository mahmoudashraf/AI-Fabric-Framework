package com.ai.fabric.platform.backend.deployment.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Function;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.fail;

@Service
public class ManagedDependencyGraphStore {
    private final ManagedDependencyGraphRepository repository;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    public ManagedDependencyGraphStore(ManagedDependencyGraphRepository repository, ObjectMapper mapper,
                                       PlatformTransactionManager manager) {
        this.repository = repository;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
    }
    public <T> T transaction(Function<ManagedDependencyGraphRepository, T> action) {
        return transaction.execute(status -> action.apply(repository));
    }
    public ManagedDependencyGraphEntity read(String id) {
        return repository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Dependency graph not found."));
    }
    public void mutate(String id, String workerToken, BiConsumer<ManagedDependencyGraphEntity, ManagedDependencyState> action) {
        transaction.executeWithoutResult(status -> {
            var graph = repository.lockById(id).orElseThrow();
            if (workerToken != null && !workerToken.equals(graph.getWorkerToken())) fail("DEPENDENCY_WORKER_FENCED");
            String original = json(graph);
            var state = state(graph);
            action.accept(graph, state);
            graph.setStateJson(json(state));
            if (original.equals(json(graph))) return;
            graph.setRevision(graph.getRevision() + 1);
            graph.setUpdatedAt(Instant.now());
            repository.saveAndFlush(graph);
        });
    }
    public ManagedDependencyState state(ManagedDependencyGraphEntity graph) { return parse(graph.getStateJson(), ManagedDependencyState.class); }
    public ManagedDependencySpec spec(ManagedDependencyGraphEntity graph) { return parse(graph.getSpecJson(), ManagedDependencySpec.class); }
    public <T> T parse(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (Exception error) { throw new IllegalStateException("Persisted dependency contract is invalid.", error); }
    }
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("Dependency contract cannot be persisted.", error); }
    }
}
