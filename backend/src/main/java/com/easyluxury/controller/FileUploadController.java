package com.easyluxury.controller;

import com.easyluxury.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "File upload and management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class FileUploadController {

    private final S3Service s3Service;

    @PostMapping("/upload-url")
    @Operation(summary = "Generate presigned upload URL", description = "Generate a presigned URL for uploading files to S3")
    @ApiResponse(responseCode = "200", description = "Presigned URL generated successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> generateUploadUrl(
            @RequestParam String fileName,
            @RequestParam String contentType,
            @RequestParam(defaultValue = "uploads") String prefix
    ) {
        log.info("Generating presigned upload URL for file: {} with content type: {}", fileName, contentType);
        
        try {
            String key = s3Service.generateFileKey(prefix, fileName);
            String presignedUrl = s3Service.generatePresignedUploadUrl(key, contentType);
            String fileUrl = s3Service.getFileUrl(key);
            
            Map<String, Object> response = Map.of(
                "presignedUrl", presignedUrl,
                "key", key,
                "fileUrl", fileUrl,
                "expiresInMinutes", 15
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate upload URL: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Delete file", description = "Delete a file from S3 storage")
    @ApiResponse(responseCode = "200", description = "File deleted successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "File not found")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String key) {
        log.info("Deleting file with key: {}", key);
        
        try {
            if (!s3Service.fileExists(key)) {
                return ResponseEntity.notFound().build();
            }
            
            s3Service.deleteFile(key);
            return ResponseEntity.ok(Map.of("message", "File deleted successfully", "key", key));
        } catch (Exception e) {
            log.error("Failed to delete file with key: {}", key, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to delete file: " + e.getMessage()));
        }
    }

    @GetMapping("/{key}/exists")
    @Operation(summary = "Check file existence", description = "Check if a file exists in S3 storage")
    @ApiResponse(responseCode = "200", description = "File existence checked")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> checkFileExists(@PathVariable String key) {
        try {
            boolean exists = s3Service.fileExists(key);
            return ResponseEntity.ok(Map.of("exists", exists, "key", key));
        } catch (Exception e) {
            log.error("Failed to check file existence for key: {}", key, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to check file existence: " + e.getMessage()));
        }
    }

    @GetMapping("/{key}/metadata")
    @Operation(summary = "Get file metadata", description = "Get metadata for a file in S3 storage")
    @ApiResponse(responseCode = "200", description = "File metadata retrieved")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "File not found")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getFileMetadata(@PathVariable String key) {
        try {
            if (!s3Service.fileExists(key)) {
                return ResponseEntity.notFound().build();
            }
            
            var metadata = s3Service.getFileMetadata(key);
            Map<String, Object> response = Map.of(
                "key", key,
                "contentType", metadata.contentType() != null ? metadata.contentType() : "unknown",
                "contentLength", metadata.contentLength(),
                "lastModified", metadata.lastModified() != null ? metadata.lastModified().toString() : null,
                "etag", metadata.eTag() != null ? metadata.eTag() : null
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get file metadata for key: {}", key, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get file metadata: " + e.getMessage()));
        }
    }
}