package com.ai.fabric.runtime.documents;

import ai.fabric.dto.AIGenerationResponse;
import ai.fabric.rag.evaluation.*;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.ai.fabric.runtime.documents.entity.DocumentEvaluationEntity;
import com.ai.fabric.runtime.documents.repository.DocumentEvaluationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix="loomai.documents", name="enabled", havingValue="true")
public class DocumentEvaluationService {
    private final DocumentKnowledgeService documents;
    private final DocumentRuntimeIdentity identities;
    private final DocumentEvaluationRepository repository;
    private final DocumentEvaluationModels models;
    private RuntimeBudgetTurnScopes turns;
    private final ObjectMapper mapper;
    private final List<RagEvaluationProvider> alternatives;
    private final String providerId;
    private final java.time.Duration retention;
    private final ExecutorService workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
        Thread.ofPlatform().name("document-evaluation-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("document-evaluation-deadline").factory());
    private final ConcurrentMap<String, Running> active = new ConcurrentHashMap<>();

    public DocumentEvaluationService(DocumentKnowledgeService documents, DocumentRuntimeIdentity identities, DocumentEvaluationRepository repository,
        DocumentEvaluationModels models, ObjectMapper mapper, List<RagEvaluationProvider> alternatives,
        @Value("${loomai.documents.evaluation-provider:structured-model-judge}") String providerId,
        @Value("${loomai.documents.evidence-retention:P30D}") java.time.Duration retention) {
        this.documents=documents; this.identities=identities; this.repository=repository; this.models=models; this.mapper=mapper;
        this.alternatives=List.copyOf(alternatives); this.providerId=providerId;
        if(retention==null || retention.compareTo(java.time.Duration.ofDays(1))<0 || retention.compareTo(java.time.Duration.ofDays(3650))>0)
            throw new IllegalArgumentException("Document evaluation retention must be between 1 and 3650 days.");
        this.retention=retention;
    }
    @Autowired(required = false)
    public void setBudgetTurns(RuntimeBudgetTurnScopes turns) { this.turns = turns; }
    public synchronized ObjectNode start(RuntimeResolvedIdentity identity, String sourceId, long expectedRevision) {
        var snapshot = documents.preparation(identity, sourceId);
        if (snapshot.revision() != expectedRevision) throw error(HttpStatus.CONFLICT, "Document settings changed. Reload before evaluating.");
        if (snapshot.activeSourceVersion() == null) throw error(HttpStatus.CONFLICT, "Index this document before evaluating it.");
        if (snapshot.configuration().questions().isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Add test questions or prepare them with AI first.");
        if (active.values().stream().anyMatch(run -> sourceId.equals(run.entity.getSourceId()) && !run.terminal))
            throw error(HttpStatus.CONFLICT, "An evaluation for this document is already running.");
        if (active.size() >= 4) throw error(HttpStatus.TOO_MANY_REQUESTS, "Evaluation capacity is busy.");
        var boundary = identities.requireBoundary(identity);
        var entity = new DocumentEvaluationEntity(); entity.setId("eval-"+UUID.randomUUID()); entity.setSourceId(sourceId);
        entity.setTenantId(boundary.tenantId()); entity.setCustomerId(boundary.customerId()); entity.setDeploymentId(boundary.deploymentId());
        entity.setTargetProfileId(boundary.targetProfileId()); entity.setReleaseId(boundary.releaseId()); entity.setCreatedAt(Instant.now());
        var result = mapper.createObjectNode().put("id", entity.getId()).put("sourceId", sourceId).put("status", "QUEUED")
            .put("createdAt", entity.getCreatedAt().toString()).put("releaseId", boundary.releaseId()).put("sourceVersion", snapshot.activeSourceVersion())
            .put("candidateSourceVersion", snapshot.sourceVersion()).put("reindexRequired", snapshot.reindexRequired()).put("providerId", providerId)
            .put("rubricVersion", "document-rag-v1").put("modelJudged", true).put("deadlineSeconds", 120)
            .put("accountingMode", turns == null ? "DEPLOYMENT_PROVIDER" : "PROVIDER_BUDGET")
            .put("maxModelCalls", Math.min(snapshot.configuration().questions().size(), snapshot.configuration().evaluationOptions().maxQuestions()) * 2)
            .put("modelCalls", 0).put("syntheticReferences", snapshot.configuration().questions().stream().anyMatch(q -> !q.reviewed())) ;
        result.set("configuration", mapper.valueToTree(snapshot.configuration())); result.putArray("cases"); result.putArray("usage");
        result.set("indexedProcessing",mapper.valueToTree(documents.activePreparation(identity,sourceId).processing()));
        var run = new Running(entity, result); persist(run); active.put(entity.getId(), run);
        try {
            run.future = workers.submit(() -> execute(run, identity, snapshot));
            synchronized(run) { if(!run.terminal) run.deadline = timer.schedule(() -> stop(run, "TIMED_OUT"), 120, TimeUnit.SECONDS); }
        } catch (RejectedExecutionException busy) { stop(run,"FAILED"); throw error(HttpStatus.TOO_MANY_REQUESTS,"Evaluation capacity is busy."); }
        synchronized(run) { return result.deepCopy(); }
    }
    public List<JsonNode> list(RuntimeResolvedIdentity identity, String sourceId) {
        documents.preparation(identity, sourceId);
        return repository.findTop20BySourceIdOrderByCreatedAtDesc(sourceId).stream().filter(row -> owned(identity,row)).map(this::read).toList();
    }
    public void requireSourceAccess(RuntimeResolvedIdentity identity, String sourceId) { documents.preparation(identity,sourceId); }
    public JsonNode get(RuntimeResolvedIdentity identity, String sourceId, String id) { return read(require(identity,sourceId,id)); }
    public JsonNode cancel(RuntimeResolvedIdentity identity, String sourceId, String id) {
        var entity = require(identity,sourceId,id); var run = active.get(id);
        if (run != null) stop(run,"CANCELLED");
        else {
            ObjectNode result = (ObjectNode)read(entity);
            if (Set.of("QUEUED","RUNNING").contains(result.path("status").asText())) {
                result.put("status","INTERRUPTED").put("finishedAt",Instant.now().toString());
                entity.setStatus("INTERRUPTED"); entity.setResultJson(result.toString()); repository.saveAndFlush(entity);
            }
        }
        return get(identity,sourceId,id);
    }
    private void execute(Running run, RuntimeResolvedIdentity identity, DocumentKnowledgeService.PreparationSnapshot snapshot) {
        try (var turn = turns == null ? null : turns.openDocumentEvaluation(identity)) {
            if (turn != null) turn.verifyBeforeOrchestration();
            synchronized(run) { if(run.terminal) return; run.result.put("status","RUNNING"); run.result.set("accountingReceipt",mapper.valueToTree(turn == null ? null : turn.chatReceipt().orElse(null))); persist(run); }
            var options = snapshot.configuration().evaluationOptions();
            RagEvaluationProvider provider = "structured-model-judge".equals(providerId) ? models.judge(identity,response -> usage(run,response))
                : alternatives.stream().filter(p -> providerId.equals(p.id())).findFirst().orElseThrow(() -> new IllegalStateException("Configured evaluation provider is unavailable."));
            for (var question : snapshot.configuration().questions().stream().limit(options.maxQuestions()).toList()) {
                check(run);
                var current = documents.preparation(identity,run.entity.getSourceId());
                if (!Objects.equals(current.activeSourceVersion(), snapshot.activeSourceVersion())) throw new StaleEvaluation();
                var result = mapper.createObjectNode().put("id",question.id()).put("question",question.question())
                    .put("referenceAnswer",question.referenceAnswer()).put("referenceReviewed",question.reviewed());
                try {
                    result.put("phase","RETRIEVAL");
                    var proof = documents.retrieveSource(identity,run.entity.getSourceId(),question.question(),options);
                    if(proof.evidence().stream().anyMatch(item -> !Objects.equals(item.sourceVersion(),snapshot.activeSourceVersion()))
                        || !Objects.equals(documents.preparation(identity,run.entity.getSourceId()).activeSourceVersion(),snapshot.activeSourceVersion())) throw new StaleEvaluation();
                    var evidence = new ArrayList<RagEvaluationSample.Evidence>(); int remaining=options.maxContextCharacters();
                    for(var item:proof.evidence()) {
                        if(remaining==0) break; String content=item.content().substring(0,Math.min(remaining,item.content().length())); remaining-=content.length();
                        evidence.add(new RagEvaluationSample.Evidence(item.chunkId(),item.sourceId(),content));
                    }
                    result.set("retrieval",mapper.valueToTree(proof)); result.set("evaluatedExcerpts",mapper.valueToTree(evidence));
                    check(run);
                    result.put("phase","ANSWER");
                    String answer=models.answer(identity,question.question(),evidence,options.answerMaxTokens(),response -> usage(run,response));
                    result.put("answer",answer); check(run);
                    result.put("phase","EVALUATION");
                    result.set("evaluation",mapper.valueToTree(provider.evaluate(new RagEvaluationSample(question.question(),answer,evidence,question.referenceAnswer()))));
                    result.put("status","COMPLETED");
                } catch (CancellationException cancelled) { throw cancelled; }
                catch (StaleEvaluation stale) { throw stale; }
                catch (RuntimeException failure) {
                    if(turn != null && turn.interruption().isPresent()) throw failure;
                    result.put("status","FAILED").put("error","This case failed in "+result.path("phase").asText().toLowerCase()+". No score was fabricated.");
                }
                synchronized(run) { if(run.terminal) return; run.result.withArray("cases").add(result); persist(run); }
            }
            synchronized(run) {
                if(run.terminal) return;
                boolean failed = java.util.stream.StreamSupport.stream(run.result.path("cases").spliterator(),false).anyMatch(c -> !"COMPLETED".equals(c.path("status").asText()));
                finish(run,failed ? "PARTIAL" : "COMPLETED");
            }
        } catch(StaleEvaluation stale) { stop(run,"STALE_INDEX"); }
        catch(CancellationException cancelled) { stop(run,"CANCELLED"); }
        catch(RuntimeException failure) { stop(run,"FAILED"); }
        finally { if(run.deadline!=null) run.deadline.cancel(false); active.remove(run.entity.getId(),run); }
    }
    private void usage(Running run,AIGenerationResponse response) {
        synchronized(run) {
            run.result.put("modelCalls",run.result.path("modelCalls").asInt()+1);
            var item=run.result.withArray("usage").addObject();
            if(response!=null) { item.put("model",response.getModel()).put("requestId",response.getRequestId()); item.set("tokens",mapper.valueToTree(response.getTokensUsed())); item.set("usage",mapper.valueToTree(response.getUsage())); }
            persist(run);
        }
    }
    private void check(Running run) { if(run.terminal || Thread.currentThread().isInterrupted()) throw new CancellationException(); }
    private void stop(Running run,String status) {
        synchronized(run) { if(!run.terminal) finish(run,status); if(run.future!=null) run.future.cancel(true); }
        active.remove(run.entity.getId(),run);
    }
    private void finish(Running run,String status) { run.terminal=true; run.result.put("status",status).put("finishedAt",Instant.now().toString()); persist(run); }
    private void persist(Running run) { run.entity.setResultJson(run.result.toString()); run.entity.setStatus(run.result.path("status").asText()); repository.saveAndFlush(run.entity); }
    private DocumentEvaluationEntity require(RuntimeResolvedIdentity identity,String sourceId,String id) {
        documents.preparation(identity,sourceId);
        return repository.findById(id).filter(row -> sourceId.equals(row.getSourceId()) && owned(identity,row)).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Evaluation was not found."));
    }
    private boolean owned(RuntimeResolvedIdentity identity,DocumentEvaluationEntity row) {
        var boundary=identities.requireBoundary(identity);
        return boundary.tenantId().equals(row.getTenantId()) && boundary.customerId().equals(row.getCustomerId())
            && boundary.deploymentId().equals(row.getDeploymentId()) && boundary.targetProfileId().equals(row.getTargetProfileId()) && boundary.releaseId().equals(row.getReleaseId());
    }
    private JsonNode read(DocumentEvaluationEntity row) { try { return mapper.readTree(row.getResultJson()); } catch(Exception failure) { throw new IllegalStateException("Stored evaluation is invalid.",failure); } }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${loomai.documents.evaluation-reconcile-ms:60000}")
    public void reconcileAbandoned() {
        for(var row:repository.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(List.of("RUNNING","QUEUED"),Instant.now().minusSeconds(180))) {
            if(active.containsKey(row.getId())) continue;
            var node=(ObjectNode)read(row); node.put("status","INTERRUPTED").put("finishedAt",Instant.now().toString());
            row.setResultJson(node.toString()); row.setStatus("INTERRUPTED"); repository.saveAndFlush(row);
        }
        repository.deleteByCreatedAtBefore(Instant.now().minus(retention));
    }
    @PreDestroy public void close() { active.values().forEach(run -> stop(run,"INTERRUPTED")); workers.shutdownNow(); timer.shutdownNow(); }
    private static final class StaleEvaluation extends RuntimeException { }
    private static final class Running {
        final DocumentEvaluationEntity entity; final ObjectNode result; volatile boolean terminal; volatile Future<?> future; volatile ScheduledFuture<?> deadline;
        Running(DocumentEvaluationEntity entity,ObjectNode result) { this.entity=entity; this.result=result; }
    }
}
