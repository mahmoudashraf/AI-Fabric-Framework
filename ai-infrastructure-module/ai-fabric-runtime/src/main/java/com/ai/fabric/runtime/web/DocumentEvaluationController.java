package com.ai.fabric.runtime.web;
import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.documents.DocumentEvaluationService;
import com.ai.fabric.runtime.documents.EvaluationRetrievalVariant;
import com.ai.fabric.runtime.admin.RuntimeAdminMutationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/sources/{sourceId}/evaluations")
@ConditionalOnProperty(prefix="loomai.documents",name="enabled",havingValue="true")
public class DocumentEvaluationController {
    private final DocumentEvaluationService service;
    private final RuntimeRequestAuthResolver auth;
    private final RuntimeAdminMutationService mutations;
    private final ObjectMapper mapper;
    public DocumentEvaluationController(DocumentEvaluationService service,RuntimeRequestAuthResolver auth,RuntimeAdminMutationService mutations,ObjectMapper mapper) { this.service=service; this.auth=auth; this.mutations=mutations; this.mapper=mapper; }
    @PostMapping public Map<String,Object> start(@PathVariable String sourceId,@RequestBody Start body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        var identity=identity(request,RuntimeScopeCatalog.DOCUMENTS_EVALUATE);
        service.requireSourceAccess(identity,sourceId);
        var variant = EvaluationRetrievalVariant.effective(body.retrievalVariant());
        var operationTarget = sourceId+":"+body.expectedRevision()
            + (variant == EvaluationRetrievalVariant.CONFIGURED ? "" : ":"+variant);
        return response(mutations.execute(identity,"DOCUMENT_EVALUATION",operationTarget,key,com.fasterxml.jackson.databind.node.ObjectNode.class,
            () -> variant == EvaluationRetrievalVariant.CONFIGURED ? service.start(identity,sourceId,body.expectedRevision())
                : service.start(identity,sourceId,body.expectedRevision(),variant)));
    }
    @GetMapping public List<Map<String,Object>> list(@PathVariable String sourceId,HttpServletRequest request) { return service.list(identity(request,RuntimeScopeCatalog.DOCUMENTS_READ),sourceId).stream().map(this::response).toList(); }
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable String sourceId,@PathVariable String id,HttpServletRequest request) { return response(service.get(identity(request,RuntimeScopeCatalog.DOCUMENTS_READ),sourceId,id)); }
    @PostMapping("/{id}/cancel") public Map<String,Object> cancel(@PathVariable String sourceId,@PathVariable String id,HttpServletRequest request) { return response(service.cancel(identity(request,RuntimeScopeCatalog.DOCUMENTS_EVALUATE),sourceId,id)); }
    private Map<String,Object> response(JsonNode result) {
        // Boot 4's Jackson 3 HTTP converter must receive values, not Jackson 2 tree internals.
        return mapper.convertValue(result,new TypeReference<Map<String,Object>>() { });
    }
    private RuntimeResolvedIdentity identity(HttpServletRequest request,String scope) {
        var identity=auth.resolveVerifiedPrivateContext(request,"document evaluation"); auth.requireScope(identity,scope,"document evaluation"); return identity;
    }
    public record Start(long expectedRevision, EvaluationRetrievalVariant retrievalVariant) {
        public Start(long expectedRevision) { this(expectedRevision, null); }
    }
}
