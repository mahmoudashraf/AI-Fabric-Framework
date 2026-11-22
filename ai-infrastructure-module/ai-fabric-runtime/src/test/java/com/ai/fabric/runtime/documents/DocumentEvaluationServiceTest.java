package com.ai.fabric.runtime.documents;

import ai.fabric.rag.evaluation.*;
import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.ai.fabric.runtime.documents.entity.DocumentEvaluationEntity;
import com.ai.fabric.runtime.documents.repository.DocumentEvaluationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentEvaluationServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final DocumentKnowledgeService documents=mock(DocumentKnowledgeService.class);
    private final DocumentEvaluationModels models=mock(DocumentEvaluationModels.class);
    private final DocumentEvaluationRepository repository=mock(DocumentEvaluationRepository.class);
    private final RuntimeBudgetTurnScopes turns=mock(RuntimeBudgetTurnScopes.class);
    private final RuntimeBudgetTurnScopes.Turn turn=mock(RuntimeBudgetTurnScopes.Turn.class);
    private final Map<String,DocumentEvaluationEntity> rows=new ConcurrentHashMap<>();
    private final RuntimeResolvedIdentity identity=RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
        .tenantId("tenant").customerId("customer").deploymentId("brain").subjectId("owner").build()).build();
    private final DocumentRuntimeIdentity boundaries=new DocumentRuntimeIdentity("tenant","customer","brain","stage","release");
    private DocumentEvaluationService service;
    private void setup() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> { var row=call.<DocumentEvaluationEntity>getArgument(0);rows.put(row.getId(),row);return row; });
        when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(turns.openDocumentEvaluation(identity)).thenReturn(turn);when(turn.chatReceipt()).thenReturn(Optional.empty());when(turn.interruption()).thenReturn(Optional.empty());
        var profile=new DocumentPreparation(null,null,List.of(new DocumentPreparation.Question("q1","What is the policy?","Thirty days",false)));
        when(documents.preparation(identity,"source")).thenReturn(new DocumentKnowledgeService.PreparationSnapshot("source",3,2,1L,profile,List.of("eng"),true,true));
        when(documents.activePreparation(identity,"source")).thenReturn(DocumentPreparation.defaults());
        when(documents.retrieveSource(eq(identity),eq("source"),anyString(),any())).thenReturn(new DocumentKnowledgeService.RetrievalProof("What is the policy?",1,List.of(
            new DocumentKnowledgeService.RetrievalEvidence("entity","source",1L,"Policy","chunk",0,"The policy allows thirty days.",.8,Map.of())),1));
        when(models.answer(eq(identity),anyString(),anyList(),anyInt(),any())).thenReturn("The policy allows thirty days [chunk].");
        RagEvaluationProvider judge=new RagEvaluationProvider(){public String id(){return "fixture";}public RagEvaluationResult evaluate(RagEvaluationSample sample){
            assertThat(sample.evidence()).singleElement().satisfies(e -> assertThat(e.content()).contains("thirty days"));
            return new RagEvaluationResult(id(),"fixture-v1",Map.of("faithfulness",new RagEvaluationResult.Metric(RagEvaluationResult.Metric.Status.SCORED,.9,"Supported by the excerpt.")));}};
        when(models.judge(eq(identity),any())).thenReturn(judge);
        service=new DocumentEvaluationService(documents,boundaries,repository,models,mapper,List.of(),"structured-model-judge",java.time.Duration.ofDays(30));
        service.setBudgetTurns(turns);
    }
    @AfterEach void close(){if(service!=null)service.close();}
    @Test void evaluatesRealRetrievedEvidenceAndRecordsTheExactActiveAndCandidateVersions() throws Exception {
        setup();var started=service.start(identity,"source",3);String id=started.path("id").asText();
        var result=await(id,"COMPLETED");
        assertThat(result.path("sourceVersion").asLong()).isEqualTo(1);assertThat(result.path("candidateSourceVersion").asLong()).isEqualTo(2);
        assertThat(result.path("indexedProcessing").path("chunkTokens").asInt()).isEqualTo(800);
        assertThat(result.path("cases").get(0).path("answer").asText()).contains("thirty days");
        verify(turns).openDocumentEvaluation(identity);verify(turn).close();
    }
    @Test void startsWithoutTheOptionalLedgerAndUsesDeploymentProviderAccounting() throws Exception {
        setup();service.close();
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory().setConversionService(org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
            .withPropertyValues("loomai.documents.enabled=true")
            .withBean(DocumentKnowledgeService.class, () -> documents)
            .withBean(DocumentRuntimeIdentity.class, () -> boundaries)
            .withBean(DocumentEvaluationRepository.class, () -> repository)
            .withBean(DocumentEvaluationModels.class, () -> models)
            .withBean(ObjectMapper.class, () -> mapper)
            .withBean(DocumentEvaluationService.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                service=context.getBean(DocumentEvaluationService.class);
                var result=await(service.start(identity,"source",3).path("id").asText(),"COMPLETED");
                assertThat(result.path("accountingMode").asText()).isEqualTo("DEPLOYMENT_PROVIDER");
                assertThat(result.path("accountingReceipt").isNull()).isTrue();
                verifyNoInteractions(turns);
            });
    }
    @Test void fundedAdmissionFailureNeverFallsBackToDeploymentCredentials() throws Exception {
        setup();when(turns.openDocumentEvaluation(identity)).thenThrow(new IllegalStateException("Budget denied"));
        var result=await(service.start(identity,"source",3).path("id").asText(),"FAILED");
        assertThat(result.path("accountingMode").asText()).isEqualTo("PROVIDER_BUDGET");
        verifyNoInteractions(models);
    }
    @Test void caseFailureRetainsTheAnswerAndNeverInventsAScore() throws Exception {
        setup();when(models.judge(eq(identity),any())).thenReturn(new RagEvaluationProvider(){public String id(){return "failing";}public RagEvaluationResult evaluate(RagEvaluationSample sample){throw new IllegalStateException("provider failed");}});
        var result=await(service.start(identity,"source",3).path("id").asText(),"PARTIAL");
        var item=result.path("cases").get(0);assertThat(item.path("answer").asText()).contains("thirty days");
        assertThat(item.has("evaluation")).isFalse();assertThat(item.path("phase").asText()).isEqualTo("EVALUATION");
    }
    @Test void staleSettingsRejectBeforeBudgetOrProviderCalls() {
        setup();assertThatThrownBy(() -> service.start(identity,"source",2)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(turns,models);
    }
    @Test void stoppingAnOrphanUpdatesBothTheReceiptAndQueryableStatus() {
        setup();
        var row=new DocumentEvaluationEntity();row.setId("orphan");row.setSourceId("source");
        row.setTenantId("tenant");row.setCustomerId("customer");row.setDeploymentId("brain");
        row.setTargetProfileId("stage");row.setReleaseId("release");row.setStatus("RUNNING");
        row.setResultJson("{\"status\":\"RUNNING\"}");rows.put(row.getId(),row);
        var result=service.cancel(identity,"source",row.getId());
        assertThat(result.path("status").asText()).isEqualTo("INTERRUPTED");
        assertThat(result.path("finishedAt").asText()).isNotBlank();
        assertThat(row.getStatus()).isEqualTo("INTERRUPTED");
        verifyNoInteractions(turns,models);
    }
    @Test void cancellationStopsBeforeAnotherModelStageAndCrossOwnerReadsAreDenied() throws Exception {
        setup();var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);
        when(models.answer(eq(identity),anyString(),anyList(),anyInt(),any())).thenAnswer(call -> {entered.countDown();finish.await();return "Answer";});
        String id=service.start(identity,"source",3).path("id").asText();assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        service.cancel(identity,"source",id);finish.countDown();await(id,"CANCELLED");
        var other=RuntimeResolvedIdentity.builder().authContext(identity.getAuthContext().toBuilder().customerId("other").build()).build();
        assertThatThrownBy(() -> service.get(other,"source",id)).isInstanceOf(RuntimeException.class);
    }
    private com.fasterxml.jackson.databind.JsonNode await(String id,String status) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<deadline){var result=service.get(identity,"source",id);if(status.equals(result.path("status").asText()))return result;Thread.sleep(20);}
        throw new AssertionError("Expected "+status+" but saw "+service.get(identity,"source",id));
    }
}
