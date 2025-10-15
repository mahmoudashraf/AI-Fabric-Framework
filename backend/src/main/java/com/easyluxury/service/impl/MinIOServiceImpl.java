package com.easyluxury.service.impl;

import com.easyluxury.service.MinIOService;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinIOServiceImpl implements MinIOService {
    
    private final MinioClient minioClient;
    
    @Value("${minio.bucket.name:ai-profiles}")
    private String bucketName;
    
    @Value("${minio.bucket.presigned-expiry:7}")
    private int presignedExpiryDays;
    
    @Override
    public String uploadPhoto(UUID userId, MultipartFile photo, String photoType) {
        try {
            String objectKey = String.format("users/%s/photos/%s-%s.%s", 
                userId, 
                photoType, 
                UUID.randomUUID(),
                getFileExtension(photo.getOriginalFilename())
            );
            
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(photo.getInputStream(), photo.getSize(), -1)
                    .contentType(photo.getContentType())
                    .build()
            );
            
            return generatePresignedUrl(objectKey);
        } catch (Exception e) {
            log.error("Failed to upload photo for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to upload photo", e);
        }
    }
    
    @Override
    public String uploadCV(UUID userId, MultipartFile cv) {
        try {
            String objectKey = String.format("users/%s/cv/%s", 
                userId, 
                cv.getOriginalFilename()
            );
            
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(cv.getInputStream(), cv.getSize(), -1)
                    .contentType(cv.getContentType())
                    .build()
            );
            
            return generatePresignedUrl(objectKey);
        } catch (Exception e) {
            log.error("Failed to upload CV for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to upload CV", e);
        }
    }
    
    @Override
    public void deletePhoto(String photoUrl) {
        try {
            String objectKey = extractObjectKeyFromUrl(photoUrl);
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to delete photo {}: {}", photoUrl, e.getMessage());
            throw new RuntimeException("Failed to delete photo", e);
        }
    }
    
    @Override
    public void deleteCV(String cvUrl) {
        try {
            String objectKey = extractObjectKeyFromUrl(cvUrl);
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to delete CV {}: {}", cvUrl, e.getMessage());
            throw new RuntimeException("Failed to delete CV", e);
        }
    }
    
    @Override
    public String generatePresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectKey)
                    .expiry(presignedExpiryDays, TimeUnit.DAYS)
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}: {}", objectKey, e.getMessage());
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
    
    @Override
    public boolean photoExists(String photoUrl) {
        try {
            String objectKey = extractObjectKeyFromUrl(photoUrl);
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    private String extractObjectKeyFromUrl(String url) {
        // Extract object key from presigned URL
        // This is a simplified implementation - you might need to adjust based on your URL format
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }
}
