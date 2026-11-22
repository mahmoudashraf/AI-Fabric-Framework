package com.ai.fabric.runtime.web;
import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.documents.DocumentEvaluationService;
import com.ai.fabric.runtime.admin.RuntimeAdminMutationService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/sources/{sourceId}/evaluations")
@ConditionalOnProperty(prefix="loomai.documents",name="enabled",havingValue="true")
public class DocumentEvaluationController {
    private final DocumentEvaluationService service;
    private final RuntimeRequestAuthResolver auth;
    private final RuntimeAdminMutationService mutations;
    public DocumentEvaluationController(DocumentEvaluationService service,RuntimeRequestAuthResolver auth,RuntimeAdminMutationService mutations) { this.service=service; this.auth=auth; this.mutations=mutations; }
    @PostMapping public JsonNode start(@PathVariable String sourceId,@RequestBody Start body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        var identity=identity(request,RuntimeScopeCatalog.DOCUMENTS_EVALUATE);
        service.requireSourceAccess(identity,sourceId);
        return mutations.execute(identity,"DOCUMENT_EVALUATION",sourceId+":"+body.expectedRevision(),key,com.fasterxml.jackson.databind.node.ObjectNode.class,
            () -> service.start(identity,sourceId,body.expectedRevision()));
    }
    @GetMapping public List<JsonNode> list(@PathVariable String sourceId,HttpServletRequest request) { return service.list(identity(request,RuntimeScopeCatalog.DOCUMENTS_READ),sourceId); }
    @GetMapping("/{id}") public JsonNode get(@PathVariable String sourceId,@PathVariable String id,HttpServletRequest request) { return service.get(identity(request,RuntimeScopeCatalog.DOCUMENTS_READ),sourceId,id); }
    @PostMapping("/{id}/cancel") public JsonNode cancel(@PathVariable String sourceId,@PathVariable String id,HttpServletRequest request) { return service.cancel(identity(request,RuntimeScopeCatalog.DOCUMENTS_EVALUATE),sourceId,id); }
    private RuntimeResolvedIdentity identity(HttpServletRequest request,String scope) {
        var identity=auth.resolveVerifiedPrivateContext(request,"document evaluation"); auth.requireScope(identity,scope,"document evaluation"); return identity;
    }
    public record Start(long expectedRevision) { }
}
