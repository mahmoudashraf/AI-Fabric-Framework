package com.ai.fabric.runtime.documents;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public class S3DocumentSourceConnector implements DocumentSourceConnector {

    private final S3Client client;
    private final String bucket;
    private final String prefix;
    private final String bindingRef;
    private final boolean versioningAvailable;
    private final DocumentKnowledgeProperties.Policy policy;
    private final DocumentTemporaryFileManager temporaryFiles;

    public S3DocumentSourceConnector(
        S3Client client,
        String bucket,
        String prefix,
        String bindingRef,
        boolean versioningAvailable,
        DocumentKnowledgeProperties.Policy policy,
        DocumentTemporaryFileManager temporaryFiles
    ) {
        this.client = client;
        this.bucket = required(bucket, "S3 bucket");
        this.prefix = normalizePrefix(prefix);
        this.bindingRef = bindingRef;
        this.versioningAvailable = versioningAvailable;
        this.policy = policy;
        this.temporaryFiles = temporaryFiles;
    }

    @Override
    public DocumentKnowledgeProperties.ConnectorType type() {
        return DocumentKnowledgeProperties.ConnectorType.S3_COMPATIBLE_OBJECT_STORAGE;
    }

    @Override
    public ConnectorStatus status() {
        try {
            client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(1)
                .build());
            return new ConnectorStatus(
                true,
                type().name(),
                bindingRef,
                scopeDigest(),
                versioningAvailable,
                null,
                Instant.now().toString()
            );
        } catch (RuntimeException exception) {
            return new ConnectorStatus(
                false,
                type().name(),
                bindingRef,
                scopeDigest(),
                versioningAvailable,
                "DOCUMENT_CONNECTOR_UNAVAILABLE",
                Instant.now().toString()
            );
        }
    }

    @Override
    public DiscoveryPage discover(String cursor, int requestedLimit) {
        int limit = boundedLimit(requestedLimit);
        try {
            ListObjectsV2Response response = client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(StringUtils.hasText(cursor) ? cursor.trim() : null)
                    .maxKeys(limit)
                    .build()
            );
            List<SourceObject> objects = response.contents().stream()
                .filter(item -> item.size() != null
                    && item.size() > 0
                    && item.size() <= policy.getMaxSourceBytes())
                .map(item -> relativeKey(item.key()))
                .filter(StringUtils::hasText)
                .filter(this::isEligible)
                .map(this::stat)
                .toList();
            return new DiscoveryPage(objects, response.isTruncated() ? response.nextContinuationToken() : null);
        } catch (RuntimeException exception) {
            throw translate("Unable to discover S3-compatible document sources.", exception);
        }
    }

    @Override
    public SourceObject stat(String objectReference) {
        String relative = DocumentConnectorSupport.normalizeReference(objectReference);
        try {
            HeadObjectResponse response = client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(fullKey(relative)).build()
            );
            long length = response.contentLength() == null ? 0 : response.contentLength();
            ensureWithinSize(length);
            String revision = revisionFingerprint(
                relative,
                response.versionId(),
                response.eTag(),
                length,
                response.lastModified()
            );
            return new SourceObject(
                relative,
                DocumentConnectorSupport.displayName(relative),
                response.contentType(),
                length,
                response.versionId(),
                response.eTag(),
                response.lastModified(),
                revision
            );
        } catch (RuntimeException exception) {
            throw translate("Unable to inspect the S3-compatible document source.", exception);
        }
    }

    @Override
    public MaterializedDocument materialize(SourceObject object) {
        SourceObject current = stat(object.objectReference());
        requireExpectedRevision(object, current);
        String extension = DocumentConnectorSupport.extension(current.objectReference());
        Path destination = temporaryFiles.create(extension);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fullKey(current.objectReference()))
                .versionId(StringUtils.hasText(current.providerVersionId()) ? current.providerVersionId() : null)
                .ifMatch(!StringUtils.hasText(current.providerVersionId()) && StringUtils.hasText(current.etag())
                    ? current.etag()
                    : null)
                .build();
        try (ResponseInputStream<GetObjectResponse> input = client.getObject(request);
             OutputStream output = Files.newOutputStream(destination)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream bounded = new DigestInputStream(input, digest)) {
                long copied = copyBounded(bounded, output, policy.getMaxSourceBytes());
                requireCompleteRead(current.contentLength(), copied);
            }
            return new MaterializedDocument(
                destination,
                temporaryFiles.root(),
                current,
                HexFormat.of().formatHex(digest.digest()),
                () -> temporaryFiles.delete(destination)
            );
        } catch (NoSuchAlgorithmException exception) {
            temporaryFiles.delete(destination);
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (IOException | RuntimeException exception) {
            temporaryFiles.delete(destination);
            throw translate("Unable to read the S3-compatible document source.", exception);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private long copyBounded(java.io.InputStream input, OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[16_384];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) {
                throw new DocumentKnowledgeException(
                    "DOCUMENT_LIMIT_EXCEEDED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Document source exceeded the configured size boundary while reading."
                );
            }
            output.write(buffer, 0, read);
        }
        return total;
    }

    private void requireCompleteRead(long expectedBytes, long copiedBytes) {
        if (copiedBytes != expectedBytes) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_SOURCE_READ_INCOMPLETE",
                HttpStatus.SERVICE_UNAVAILABLE,
                "Document source content did not match the inspected size. Refresh and retry."
            );
        }
    }

    private String fullKey(String relative) {
        return prefix + DocumentConnectorSupport.normalizeReference(relative);
    }

    private String relativeKey(String key) {
        if (!StringUtils.hasText(key) || !key.startsWith(prefix) || key.equals(prefix)) {
            return null;
        }
        return key.substring(prefix.length());
    }

    private String normalizePrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("../") || normalized.equals("..") || normalized.contains("://")) {
            throw DocumentConnectorSupport.invalid("Configured S3 prefix is invalid.");
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String scopeDigest() {
        return DocumentConnectorSupport.sha256(bucket + "|" + prefix);
    }

    private String revisionFingerprint(String reference, String versionId, String etag, long size, Instant modified) {
        return DocumentConnectorSupport.sha256(
            reference + "|" + safe(versionId) + "|" + safe(etag) + "|" + size + "|" + safe(modified)
        );
    }

    private void requireExpectedRevision(SourceObject expected, SourceObject current) {
        if (expected == null
            || !StringUtils.hasText(expected.providerRevisionFingerprint())
            || !expected.providerRevisionFingerprint().equals(current.providerRevisionFingerprint())) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_SOURCE_CHANGED",
                HttpStatus.CONFLICT,
                "Document source revision changed before it could be read. Refresh and retry."
            );
        }
    }

    private boolean isEligible(String reference) {
        String extension = DocumentConnectorSupport.extension(reference).toLowerCase(Locale.ROOT);
        return policy.getAllowedExtensions().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(extension::equals);
    }

    private int boundedLimit(int requested) {
        int configured = Math.max(1, Math.min(policy.getDiscoveryPageSize(), 200));
        return requested > 0 ? Math.min(requested, configured) : configured;
    }

    private void ensureWithinSize(long size) {
        if (size <= 0 || size > policy.getMaxSourceBytes()) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_LIMIT_EXCEEDED",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Document source exceeds the configured size boundary."
            );
        }
    }

    private DocumentKnowledgeException translate(String message, Throwable exception) {
        if (exception instanceof DocumentKnowledgeException documentException) {
            return documentException;
        }
        if (exception instanceof NoSuchKeyException
            || exception instanceof S3Exception s3 && s3.statusCode() == 404) {
            return new DocumentKnowledgeException("DOCUMENT_SOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message, exception);
        }
        if (exception instanceof S3Exception s3 && s3.statusCode() == 412) {
            return new DocumentKnowledgeException(
                "DOCUMENT_SOURCE_CHANGED",
                HttpStatus.CONFLICT,
                "Document source revision changed while it was being read.",
                exception
            );
        }
        return new DocumentKnowledgeException(
            "DOCUMENT_CONNECTOR_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE,
            message,
            exception
        );
    }

    private String required(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required");
        }
        return value.trim();
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}
