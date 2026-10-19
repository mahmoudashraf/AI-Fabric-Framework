package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.ControlPlane;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;

import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Deployment configuration is the trust root. Requests cannot select this server's CP identity. */
@Component
public class UnusedVectorTransferIdentity {
    private final String id, origin, peers;
    private final UnusedVectorTransferJson json;

    public UnusedVectorTransferIdentity(@Value("${platform.external-vector-transfer.control-plane-id:}") String id,
            @Value("${platform.external-vector-transfer.control-plane-origin:}") String origin,
            @Value("${platform.external-vector-transfer.peer-origins:{}}") String peers, UnusedVectorTransferJson json) {
        this.id = id; this.origin = origin; this.peers = peers; this.json = json;
    }
    public ControlPlane local() {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw invalid();
        return new ControlPlane(id, strictOrigin(origin));
    }
    public void requireLocal(ControlPlane value) { if (!local().equals(value)) throw invalid(); }
    public void requirePeer(ControlPlane value) {
        if (value == null || value.id() == null || !value.id().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw invalid();
        var own = local();
        JsonNode configured = json.read(peers, JsonNode.class);
        if (!configured.isObject() || own.id().equals(value.id()) || own.origin().equals(value.origin())
            || !configured.path(value.id()).isTextual()
            || !strictOrigin(value.origin()).equals(strictOrigin(configured.path(value.id()).textValue()))) throw invalid();
    }
    private String strictOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().isEmpty() || !uri.getHost().equals(uri.getHost().toLowerCase(Locale.ROOT))
                || !value.equals("https://" + uri.getHost())) throw invalid();
            return value;
        } catch (Exception ex) { throw invalid(); }
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(PRECONDITION_FAILED, "UNUSED_VECTOR_TRANSFER_CP_IDENTITY_REQUIRED"); }
}
