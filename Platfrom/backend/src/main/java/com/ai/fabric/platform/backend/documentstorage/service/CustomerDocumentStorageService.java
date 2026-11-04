package com.ai.fabric.platform.backend.documentstorage.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.UpsertDocumentStorageBindingRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentOperationIdempotencyKey;
import com.ai.fabric.platform.backend.documentstorage.entity.*;
import com.ai.fabric.platform.backend.documentstorage.model.DocumentStorageContracts.*;
import com.ai.fabric.platform.backend.documentstorage.provider.CustomerDocumentStorageProvider;
import com.ai.fabric.platform.backend.documentstorage.provider.DocumentStorageProviderException;
import com.ai.fabric.platform.backend.documentstorage.repository.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Owns source-file storage, not document parsing, vectors or runtime manifests. */
@Service
public class CustomerDocumentStorageService {
    public static final int MAX_UPLOAD_BYTES = 1024 * 1024;
    private final DeploymentRepository deployments;
    private final DeploymentAccessService access;
    private final DocumentStorageConnectionRepository connections;
    private final DocumentStorageUploadRepository uploads;
    private final CustomerDocumentStorageProvider provider;
    private final DeploymentDocumentStorageBindingService bindings;
    private final DeploymentTargetProfileRepository targets;
    private final CustomerSelfServiceOperationBoundary boundary;
    private final PlatformSecretService secrets;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;
    // Bound paid source-storage side effects, independently of AI semantic decisions.
    @Value("${platform.document-storage.max-connections-per-customer:3}")
    private int maxConnections = 3;
    @Value("${platform.document-storage.max-uploads-per-connection:100}")
    private int maxUploads = 100;

    public CustomerDocumentStorageService(DeploymentRepository deployments, DeploymentAccessService access,
        DocumentStorageConnectionRepository connections, DocumentStorageUploadRepository uploads,
        CustomerDocumentStorageProvider provider, DeploymentDocumentStorageBindingService bindings,
        DeploymentTargetProfileRepository targets, CustomerSelfServiceOperationBoundary boundary,
        PlatformSecretService secrets, PlatformAuditService audit, ObjectMapper mapper) {
        this.deployments=deployments; this.access=access; this.connections=connections; this.uploads=uploads;
        this.provider=provider; this.bindings=bindings; this.targets=targets; this.boundary=boundary;
        this.secrets=secrets; this.audit=audit; this.mapper=mapper;
    }

    public Catalogue list(String deploymentId) {
        var deployment = requireDeployment(deploymentId);
        return new Catalogue(provider.enabled(), MAX_UPLOAD_BYTES, List.of("txt", "json"),
            connections.findByCustomerIdAndTenantIdOrderByCreatedAtDesc(deployment.getCustomerId(), deployment.getTenantId())
                .stream().map(this::summary).toList());
    }

    public Connection create(String deploymentId, Create request) {
        var deployment=requireDeployment(deploymentId);
        if(request==null)throw invalid("Storage creation request is required.");
        requireProvider();
        String operation=DeploymentOperationIdempotencyKey.require(request.operationKey());
        String name=text(request.name(),160,"Storage name");
        var existing=connections.findByCustomerIdAndTenantIdAndOperationKey(deployment.getCustomerId(), deployment.getTenantId(), operation);
        if(existing.isPresent()) {
            if(!name.equals(existing.get().getName())) throw conflict("This operation key belongs to a different storage request.");
            return summary(existing.get());
        }
        if(connections.findByCustomerIdAndTenantIdOrderByCreatedAtDesc(deployment.getCustomerId(),deployment.getTenantId()).size()>=maxConnections)
            throw conflict("The hosted storage connection limit is reached. Reuse an existing connection or contact the integration team.");
        var entity=new DocumentStorageConnectionEntity();
        entity.setId("dsc-"+UUID.randomUUID());
        entity.setCustomerId(deployment.getCustomerId()); entity.setTenantId(deployment.getTenantId());
        entity.setName(name); entity.setMode("LOOMAI_HOSTED_S3");
        entity.setBucket(provider.bucketPrefix()+"-"+entity.getId().substring(4).replace("-","").substring(0,24));
        entity.setRegion(provider.region()); entity.setRootPrefix("knowledge/"); entity.setStatus("NOT_PREPARED");
        entity.setOperationKey(operation); entity.setCreatedAt(Instant.now()); entity.setUpdatedAt(entity.getCreatedAt());
        entity=connections.saveAndFlush(entity);
        audit.record("DOCUMENT_STORAGE_CONNECTION_CREATED","DEPLOYMENT",deploymentId,Map.of("connectionId",entity.getId(),"cloudProvisioned",false));
        return summary(entity);
    }

    // Provider operations deliberately run outside a control-plane transaction.
    public Connection prepare(String deploymentId,String connectionId,Command request) {
        requireDeployment(deploymentId);
        if(request==null)throw invalid("Storage preparation request is required.");
        DeploymentOperationIdempotencyKey.require(request.operationKey());
        var connection=requireConnection(deploymentId,connectionId);
        requireProvider();
        if("READY".equals(connection.getStatus())) return summary(connection);
        if("PREPARING".equals(connection.getStatus())) throw conflict("Storage preparation is already in progress. Refresh its status.");
        connection.setStatus("PREPARING"); connection.setLastError(null); connection.setUpdatedAt(Instant.now());
        connection=connections.saveAndFlush(connection);
        try {
            var bucket=provider.prepareBucket(connection.getId(),connection.getCustomerId(),connection.getBucket());
            if(!connection.getBucket().equals(bucket.name())||!bucket.versioningEnabled())
                throw conflict("Storage provider did not confirm the expected private versioned bucket.");
            connection.setStatus("READY"); connection.setUpdatedAt(Instant.now());
            connections.saveAndFlush(connection);
            audit.record("DOCUMENT_STORAGE_PREPARED","DEPLOYMENT",deploymentId,Map.of("connectionId",connectionId));
            return summary(connection);
        } catch(RuntimeException error) {
            connection.setStatus("FAILED"); connection.setLastError(providerFailure("Storage preparation failed. Check provider permissions and retry.",error));
            connection.setUpdatedAt(Instant.now()); connections.saveAndFlush(connection);
            throw unavailable(connection.getLastError());
        }
    }

    public Binding bind(String deploymentId,String connectionId,Bind request) {
        var deployment=requireDeployment(deploymentId);
        if(request==null)throw invalid("Target binding request is required.");
        var connection=ready(deploymentId,connectionId);
        requireProvider();
        String operation=DeploymentOperationIdempotencyKey.require(request.operationKey());
        var target=targets.findById(text(request.targetProfileId(),64,"Target"))
            .filter(value->value.isActive()).orElseThrow(()->notFound("Active target was not found."));
        boundary.require(deployment,target.getId(),null,Operation.BINDING_REPLACE,operation,Map.of(),null,null,null);
        String prefix=connection.getRootPrefix()+relativePrefix(request.prefix());
        String readerId="loomai-doc-reader-"+sha256(connectionId+"|"+deploymentId+"|"+target.getId()+"|"+prefix).substring(0,40);
        String secretRef="MANAGED_DOCUMENT_READER_"+sha256(readerId).substring(0,40).toUpperCase(Locale.ROOT);
        String accessKey=secrets.resolveSecretForDeployment(secretRef+"_ACCESS_KEY",deploymentId,target.getId());
        String secretKey=secrets.resolveSecretForDeployment(secretRef+"_SECRET_KEY",deploymentId,target.getId());
        if(accessKey==null || secretKey==null) {
            if(accessKey!=null || secretKey!=null) throw conflict("Reader credential storage is incomplete; operator reconciliation is required.");
            CustomerDocumentStorageProvider.Reader reader;
            try { reader=provider.createReader(readerId,connection.getBucket(),prefix); }
            catch(DocumentStorageProviderException error) { throw unavailable(providerFailure("The target reader could not be prepared.",error)); }
            // Store the returned secret before any subsequent provider or binding operation.
            secrets.upsertTargetManagedSecret(secretRef+"_ACCESS_KEY",reader.accessKey(),deploymentId,target.getId(),Map.of("purpose","DOCUMENT_STORAGE_READER"));
            secrets.upsertTargetManagedSecret(secretRef+"_SECRET_KEY",reader.secretKey(),deploymentId,target.getId(),Map.of("purpose","DOCUMENT_STORAGE_READER"));
            accessKey=reader.accessKey(); secretKey=reader.secretKey();
        }
        var binding=bindings.upsertHostedConnection(deploymentId,new UpsertDocumentStorageBindingRequest(
            target.getId(),DeploymentDocumentStorageBindingService.S3_CONNECTOR,provider.endpoint(),connection.getRegion(),
            connection.getBucket(),prefix,accessKey,secretKey,null,false,true,false,null),connectionId,operation);
        return new Binding(summary(connection),binding);
    }

    public List<Upload> uploads(String deploymentId,String connectionId) {
        requireConnection(deploymentId,connectionId);
        return uploads.findByConnectionIdOrderByCreatedAtDesc(connectionId).stream().map(this::summary).toList();
    }

    public Upload upload(String deploymentId,String connectionId,String filename,String purposePrefix,String operationKey,byte[] bytes) {
        var connection=ready(deploymentId,connectionId);
        requireProvider();
        String operation=DeploymentOperationIdempotencyKey.require(operationKey);
        filename=filename(filename);
        String prefix=relativePrefix(purposePrefix);
        String contentType=validateContents(filename,bytes);
        String hash=sha256(bytes);
        var existing=uploads.findByConnectionIdAndOperationKey(connectionId,operation);
        DocumentStorageUploadEntity upload;
        if(existing.isPresent()) {
            upload=existing.get();
            if(!filename.equals(upload.getFilename())||!prefix.equals(upload.getPrefix())||!hash.equals(upload.getSha256()))
                throw conflict("This operation key belongs to a different document upload.");
            if("UPLOADED".equals(upload.getStatus())) return summary(upload);
            if("UPLOADING".equals(upload.getStatus())) throw conflict("Upload is in progress. Refresh its status before retrying.");
        } else {
            if(uploads.findByConnectionIdOrderByCreatedAtDesc(connectionId).size()>=maxUploads)
                throw conflict("The document upload limit is reached. Contact the integration team before adding more source files.");
            upload=new DocumentStorageUploadEntity(); upload.setId("du-"+UUID.randomUUID());
            upload.setConnectionId(connectionId); upload.setFilename(filename); upload.setPrefix(prefix);
            upload.setObjectKey(connection.getRootPrefix()+prefix+upload.getId()+"/"+filename);
            if(upload.getObjectKey().getBytes(StandardCharsets.UTF_8).length>1024) throw invalid("Document object path is too long.");
            upload.setSizeBytes(bytes.length); upload.setContentType(contentType); upload.setSha256(hash);
            upload.setOperationKey(operation); upload.setCreatedAt(Instant.now());
        }
        upload.setStatus("UPLOADING"); upload.setLastError(null); upload.setUpdatedAt(Instant.now());
        upload=uploads.saveAndFlush(upload);
        try {
            var stored=provider.putObject(connection.getBucket(),upload.getObjectKey(),bytes,contentType,hash);
            if(stored.size()!=bytes.length) throw conflict("Uploaded document size could not be verified.");
            upload.setEtag(stored.etag()); upload.setProviderVersionId(stored.versionId());
            upload.setStatus("UPLOADED"); upload.setUpdatedAt(Instant.now()); uploads.saveAndFlush(upload);
            audit.record("DOCUMENT_SOURCE_UPLOADED","DEPLOYMENT",deploymentId,Map.of("connectionId",connectionId,"uploadId",upload.getId(),"bytes",bytes.length,"indexed",false));
            return summary(upload);
        } catch(RuntimeException error) {
            upload.setStatus("FAILED"); upload.setLastError(providerFailure("Upload could not be confirmed. Retry the same operation; it will not create a second document.",error));
            upload.setUpdatedAt(Instant.now()); uploads.saveAndFlush(upload);
            throw unavailable(upload.getLastError());
        }
    }

    public DeploymentEntity requireDeployment(String id) {
        var deployment=deployments.findById(id).orElseThrow(()->notFound("Deployment was not found."));
        if(deployment.getArchivedAt()!=null||deployment.getDeletionRequestedAt()!=null) throw conflict("Deployment is archived or being removed.");
        return access.requireDeploymentAdminAccess(deployment);
    }

    public DocumentStorageConnectionEntity requireConnection(String deploymentId,String connectionId) {
        var deployment=requireDeployment(deploymentId);
        return connections.findById(connectionId)
            .filter(value->Objects.equals(value.getCustomerId(),deployment.getCustomerId())&&Objects.equals(value.getTenantId(),deployment.getTenantId()))
            .orElseThrow(()->notFound("Document storage connection was not found."));
    }

    public DocumentStorageConnectionEntity ready(String deploymentId,String connectionId) {
        var value=requireConnection(deploymentId,connectionId);
        if(!"READY".equals(value.getStatus())) throw conflict("Prepare storage before uploading or binding documents.");
        return value;
    }

    public DocumentStorageUploadEntity requireUpload(String deploymentId,String connectionId,String uploadId) {
        requireConnection(deploymentId,connectionId);
        return uploads.findById(uploadId).filter(value->connectionId.equals(value.getConnectionId())&&"UPLOADED".equals(value.getStatus()))
            .orElseThrow(()->notFound("Confirmed document upload was not found."));
    }

    private void requireProvider() {
        if(!provider.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"LoomAI document storage is not configured. Customer-provided storage remains available.");
    }

    private String validateContents(String filename,byte[] bytes) {
        if(bytes==null||bytes.length==0||bytes.length>MAX_UPLOAD_BYTES) throw invalid("Upload a non-empty TXT or JSON document of at most 1 MiB.");
        try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)); }
        catch(Exception error) { throw invalid("Documents must contain UTF-8 text."); }
        if(filename.toLowerCase(Locale.ROOT).endsWith(".json")) {
            try { if(mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes)==null) throw new IllegalArgumentException(); }
            catch(Exception error) { throw invalid("The JSON document is not valid JSON."); }
            return "application/json";
        }
        return "text/plain; charset=utf-8";
    }

    static String filename(String value) {
        String name=text(value,180,"Filename");
        String lower=name.toLowerCase(Locale.ROOT);
        if(name.contains("/")||name.contains("\\")||name.chars().anyMatch(Character::isISOControl)
            ||!(lower.endsWith(".txt")||lower.endsWith(".json"))||name.equals(".txt")||name.equals(".json"))
            throw invalid("Choose a TXT or JSON filename without folders or control characters.");
        return name;
    }

    public static String relativePrefix(String value) {
        String prefix=value==null||value.isBlank()?"public/":value.trim();
        if(prefix.length()>480||prefix.startsWith("/")||prefix.contains("\\")) throw invalid("Use a relative purpose folder.");
        String[] segments=prefix.split("/",-1);
        for(int i=0;i<segments.length;i++) {
            if(i==segments.length-1&&segments[i].isEmpty()) continue;
            if(!segments[i].matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")||segments[i].equals(".")||segments[i].equals(".."))
                throw invalid("Use folder names containing letters, numbers, dots, hyphens or underscores.");
        }
        return prefix.endsWith("/")?prefix:prefix+"/";
    }

    private Connection summary(DocumentStorageConnectionEntity c) { return new Connection(c.getId(),c.getName(),c.getMode(),c.getBucket(),c.getRegion(),c.getRootPrefix(),c.getStatus(),c.getLastError(),c.getCreatedAt(),c.getUpdatedAt()); }
    private Upload summary(DocumentStorageUploadEntity u) { return new Upload(u.getId(),u.getConnectionId(),u.getFilename(),u.getObjectKey(),u.getPrefix(),u.getSizeBytes(),u.getContentType(),u.getSha256(),u.getStatus(),u.getLastError(),u.getCreatedAt()); }
    static String text(String value,int max,String field) { if(value==null||value.isBlank()||value.trim().length()>max) throw invalid(field+" is required and must be bounded.");return value.trim(); }
    public static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] value) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception error){throw new IllegalStateException(error);} }
    static ResponseStatusException conflict(String message) {return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    static ResponseStatusException invalid(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    static ResponseStatusException notFound(String message) {return new ResponseStatusException(HttpStatus.NOT_FOUND,message);}
    static ResponseStatusException unavailable(String message) {return new ResponseStatusException(HttpStatus.BAD_GATEWAY,message);}
    private static String providerFailure(String message,RuntimeException error) {
        if(error instanceof DocumentStorageProviderException failure) {
            // These are provider-owned fixed codes/enums, never an SDK message or cause chain.
            return message+" ["+failure.getCode()+(failure.getAwsErrorCode()==null?"":"; "+failure.getAwsErrorCode())+"]";
        }
        return message;
    }
}
