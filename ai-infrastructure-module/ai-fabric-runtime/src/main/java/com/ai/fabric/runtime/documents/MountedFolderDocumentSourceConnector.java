package com.ai.fabric.runtime.documents;

import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public class MountedFolderDocumentSourceConnector implements DocumentSourceConnector {

    private final Path configuredRoot;
    private final String bindingRef;
    private final DocumentKnowledgeProperties.Policy policy;
    private final DocumentTemporaryFileManager temporaryFiles;

    public MountedFolderDocumentSourceConnector(
        Path configuredRoot,
        String bindingRef,
        DocumentKnowledgeProperties.Policy policy,
        DocumentTemporaryFileManager temporaryFiles
    ) {
        this.configuredRoot = configuredRoot.toAbsolutePath().normalize();
        this.bindingRef = bindingRef;
        this.policy = policy;
        this.temporaryFiles = temporaryFiles;
    }

    @Override
    public DocumentKnowledgeProperties.ConnectorType type() {
        return DocumentKnowledgeProperties.ConnectorType.MOUNTED_FOLDER;
    }

    @Override
    public ConnectorStatus status() {
        try {
            Path root = realRoot();
            boolean ready = Files.isDirectory(root) && Files.isReadable(root);
            return new ConnectorStatus(
                ready,
                type().name(),
                bindingRef,
                DocumentConnectorSupport.sha256(root.toString()),
                false,
                ready ? null : "DOCUMENT_SOURCE_NOT_READABLE",
                Instant.now().toString()
            );
        } catch (RuntimeException exception) {
            return new ConnectorStatus(
                false,
                type().name(),
                bindingRef,
                DocumentConnectorSupport.sha256(configuredRoot.toString()),
                false,
                "DOCUMENT_CONNECTOR_UNAVAILABLE",
                Instant.now().toString()
            );
        }
    }

    @Override
    public DiscoveryPage discover(String cursor, int requestedLimit) {
        Path root = realRoot();
        int offset = parseCursor(cursor);
        int limit = boundedLimit(requestedLimit);
        try (var paths = Files.walk(root)) {
            List<SourceObject> objects = paths
                .filter(path -> !path.equals(root))
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> isEligible(path.getFileName().toString()))
                .filter(this::isWithinSizeBoundary)
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .skip(offset)
                .limit(limit + 1L)
                .map(this::toSourceObject)
                .toList();
            boolean hasMore = objects.size() > limit;
            List<SourceObject> page = hasMore ? objects.subList(0, limit) : objects;
            return new DiscoveryPage(page, hasMore ? String.valueOf(offset + limit) : null);
        } catch (IOException exception) {
            throw unavailable("Unable to discover mounted document sources.", exception);
        }
    }

    @Override
    public SourceObject stat(String objectReference) {
        return toSourceObject(resolve(objectReference));
    }

    @Override
    public MaterializedDocument materialize(SourceObject object) {
        Path path = resolve(object.objectReference());
        SourceObject current = toSourceObject(path);
        requireExpectedRevision(object, current);
        ensureWithinSize(current.contentLength());
        Path destination = temporaryFiles.create(DocumentConnectorSupport.extension(current.objectReference()));
        try (InputStream input = Files.newInputStream(path);
             OutputStream output = Files.newOutputStream(destination)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream bounded = new DigestInputStream(input, digest)) {
                long copied = copyBounded(bounded, output, policy.getMaxSourceBytes());
                requireCompleteRead(current.contentLength(), copied);
            }
            SourceObject afterRead = toSourceObject(path);
            if (!current.providerRevisionFingerprint().equals(afterRead.providerRevisionFingerprint())) {
                temporaryFiles.delete(destination);
                throw new DocumentKnowledgeException(
                    "DOCUMENT_SOURCE_CHANGED",
                    HttpStatus.CONFLICT,
                    "Mounted document changed while it was being read. Refresh and retry."
                );
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
        } catch (IOException exception) {
            temporaryFiles.delete(destination);
            throw unavailable("Unable to read the mounted document source.", exception);
        } catch (RuntimeException exception) {
            temporaryFiles.delete(destination);
            throw exception;
        }
    }

    private long copyBounded(InputStream input, OutputStream output, long maxBytes) throws IOException {
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

    private SourceObject toSourceObject(Path candidate) {
        Path path = trustedRealPath(candidate);
        try {
            long size = Files.size(path);
            ensureWithinSize(size);
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            String contentFingerprint = contentFingerprint(path, size);
            long sizeAfterRead = Files.size(path);
            Instant modifiedAfterRead = Files.getLastModifiedTime(path).toInstant();
            if (size != sizeAfterRead || !modified.equals(modifiedAfterRead)) {
                throw sourceChanged("Mounted document changed while its revision was being inspected.");
            }
            String relative = realRoot().relativize(path).toString().replace('\\', '/');
            String contentType = Files.probeContentType(path);
            String revision = DocumentConnectorSupport.sha256(
                relative + "|" + size + "|" + modified + "|" + contentFingerprint
            );
            return new SourceObject(
                relative,
                DocumentConnectorSupport.displayName(relative),
                contentType,
                size,
                null,
                null,
                modified,
                revision
            );
        } catch (IOException exception) {
            throw unavailable("Unable to inspect the mounted document source.", exception);
        }
    }

    private String contentFingerprint(Path path, long expectedBytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied;
            try (InputStream input = Files.newInputStream(path);
                 DigestInputStream bounded = new DigestInputStream(input, digest)) {
                copied = copyBounded(bounded, OutputStream.nullOutputStream(), policy.getMaxSourceBytes());
            }
            requireCompleteRead(expectedBytes, copied);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireCompleteRead(long expectedBytes, long copiedBytes) {
        if (copiedBytes != expectedBytes) {
            throw sourceChanged("Mounted document content did not match the inspected size. Refresh and retry.");
        }
    }

    private DocumentKnowledgeException sourceChanged(String message) {
        return new DocumentKnowledgeException(
            "DOCUMENT_SOURCE_CHANGED",
            HttpStatus.CONFLICT,
            message
        );
    }

    private Path resolve(String reference) {
        String normalized = DocumentConnectorSupport.normalizeReference(reference);
        return trustedRealPath(realRoot().resolve(normalized).normalize());
    }

    private Path trustedRealPath(Path candidate) {
        try {
            Path root = realRoot();
            Path normalized = candidate.toAbsolutePath().normalize();
            if (!normalized.startsWith(configuredRoot)) {
                throw DocumentConnectorSupport.invalid("Document object reference escapes the configured source scope.");
            }
            rejectSymbolicSegments(normalized);
            Path real = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.startsWith(root)
                || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(real)) {
                throw DocumentConnectorSupport.invalid("Document source is not a readable regular file.");
            }
            return real;
        } catch (IOException exception) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_SOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "Document source was not found in the configured mounted folder.",
                exception
            );
        }
    }

    private void rejectSymbolicSegments(Path candidate) throws IOException {
        Path current = configuredRoot;
        Path relative = configuredRoot.relativize(candidate);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw DocumentConnectorSupport.invalid("Symbolic links are not accepted as document sources.");
            }
        }
    }

    private Path realRoot() {
        try {
            return configuredRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw unavailable("Configured mounted document root is unavailable.", exception);
        }
    }

    private boolean isEligible(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return policy.getAllowedExtensions().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(lower::endsWith);
    }

    private boolean isWithinSizeBoundary(Path path) {
        try {
            long size = Files.size(path);
            return size > 0 && size <= policy.getMaxSourceBytes();
        } catch (IOException ignored) {
            return false;
        }
    }

    private void requireExpectedRevision(SourceObject expected, SourceObject current) {
        if (expected == null
            || expected.providerRevisionFingerprint() == null
            || !expected.providerRevisionFingerprint().equals(current.providerRevisionFingerprint())) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_SOURCE_CHANGED",
                HttpStatus.CONFLICT,
                "Mounted document changed before it could be read. Refresh and retry."
            );
        }
    }

    private int boundedLimit(int requested) {
        int configured = Math.max(1, Math.min(policy.getDiscoveryPageSize(), 200));
        return requested > 0 ? Math.min(requested, configured) : configured;
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(cursor.trim());
            if (value < 0 || value > 1_000_000) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw DocumentConnectorSupport.invalid("Discovery cursor is invalid.");
        }
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

    private DocumentKnowledgeException unavailable(String message, Throwable cause) {
        return new DocumentKnowledgeException(
            "DOCUMENT_CONNECTOR_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE,
            message,
            cause
        );
    }
}
