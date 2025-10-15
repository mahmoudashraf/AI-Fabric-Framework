package com.easyluxury.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name:easyluxury}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiration:15}")
    private int presignedUrlExpirationMinutes;

    /**
     * Generate a presigned URL for uploading a file
     */
    public String generatePresignedUploadUrl(String key, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(presignedUrlExpirationMinutes))
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String url = presignedRequest.url().toString();
            
            log.debug("Generated presigned upload URL for key: {} with expiration: {} minutes", key, presignedUrlExpirationMinutes);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL for key: {}", key, e);
            throw new RuntimeException("Failed to generate presigned upload URL", e);
        }
    }

    /**
     * Generate a unique key for file upload
     */
    public String generateFileKey(String prefix, String originalFileName) {
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return prefix + "/" + UUID.randomUUID() + extension;
    }

    /**
     * Upload a file directly to S3
     */
    public String uploadFile(String key, byte[] fileContent, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileContent));
            
            String fileUrl = getFileUrl(key);
            log.debug("Uploaded file to S3 with key: {}", key);
            return fileUrl;
        } catch (Exception e) {
            log.error("Failed to upload file with key: {}", key, e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * Delete a file from S3
     */
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.debug("Deleted file from S3 with key: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete file with key: {}", key, e);
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    /**
     * Delete multiple files from S3
     */
    public void deleteFiles(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        try {
            List<ObjectIdentifier> objectsToDelete = keys.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

            Delete delete = Delete.builder()
                    .objects(objectsToDelete)
                    .build();

            DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(delete)
                    .build();

            s3Client.deleteObjects(deleteObjectsRequest);
            log.debug("Deleted {} files from S3", keys.size());
        } catch (Exception e) {
            log.error("Failed to delete files: {}", keys, e);
            throw new RuntimeException("Failed to delete files from S3", e);
        }
    }

    /**
     * Get the public URL of a file
     */
    public String getFileUrl(String key) {
        // For MinIO/S3, construct the public URL
        // Note: This assumes the bucket is publicly readable
        // Use the endpoint from configuration since serviceClientConfiguration is not available
        return String.format("http://localhost:9000/%s/%s", bucketName, key);
    }

    /**
     * Check if a file exists in S3
     */
    public boolean fileExists(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Failed to check if file exists with key: {}", key, e);
            throw new RuntimeException("Failed to check file existence", e);
        }
    }

    /**
     * Get file metadata
     */
    public HeadObjectResponse getFileMetadata(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.headObject(headObjectRequest);
        } catch (Exception e) {
            log.error("Failed to get file metadata for key: {}", key, e);
            throw new RuntimeException("Failed to get file metadata", e);
        }
    }

    /**
     * Create bucket if it doesn't exist
     */
    public void createBucketIfNotExists() {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            s3Client.headBucket(headBucketRequest);
            log.debug("Bucket {} already exists", bucketName);
        } catch (NoSuchBucketException e) {
            try {
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                
                s3Client.createBucket(createBucketRequest);
                log.info("Created bucket: {}", bucketName);
            } catch (Exception createException) {
                log.error("Failed to create bucket: {}", bucketName, createException);
                throw new RuntimeException("Failed to create S3 bucket", createException);
            }
        } catch (Exception e) {
            log.error("Failed to check bucket existence: {}", bucketName, e);
            throw new RuntimeException("Failed to check S3 bucket", e);
        }
    }
}