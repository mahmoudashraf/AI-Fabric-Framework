package com.ai.fabric.runtime.documents;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

public class DocumentTemporaryFileManager {

    private static final String FILE_PREFIX = "loomai-document-";

    private final Path root;
    private final Duration retention;
    private final Clock clock;

    public DocumentTemporaryFileManager(Path root, Duration retention, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.retention = retention == null ? Duration.ofHours(1) : retention;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(root);
            cleanupExpired();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize document temporary storage", exception);
        }
    }

    public Path root() {
        return root;
    }

    public Path create(String extension) {
        try {
            Files.createDirectories(root);
            return Files.createTempFile(root, FILE_PREFIX, extension == null ? ".tmp" : extension);
        } catch (IOException exception) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_TEMP_STORAGE_FAILED",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Document temporary storage is unavailable.",
                exception
            );
        }
    }

    public void delete(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || !normalized.getFileName().toString().startsWith(FILE_PREFIX)) {
            return;
        }
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException ignored) {
            // A scheduled sweep retries bounded temporary-file cleanup.
        }
    }

    @Scheduled(fixedDelayString = "${loomai.documents.temp-cleanup-interval:PT15M}")
    public void cleanupExpired() {
        if (!Files.isDirectory(root)) {
            return;
        }
        Instant cutoff = clock.instant().minus(retention);
        try (var files = Files.walk(root, 1)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(FILE_PREFIX))
                .filter(path -> lastModified(path).toInstant().isBefore(cutoff))
                .sorted(Comparator.reverseOrder())
                .forEach(this::delete);
        } catch (IOException ignored) {
            // Readiness/status surfaces report connector failures; cleanup remains retryable.
        }
    }

    private FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException ignored) {
            return FileTime.from(Instant.EPOCH);
        }
    }
}
