package com.ai.fabric.platform.backend.documentstorage.provider;

public interface CustomerDocumentStorageProvider {
    boolean enabled();

    String region();

    String endpoint();

    String accountId();

    /** Server-owned namespace and account prefix, without a trailing separator. */
    String bucketPrefix();

    Bucket prepareBucket(String connectionId, String customerId, String bucketName);

    Reader createReader(String readerName, String bucketName, String prefix);

    StoredObject putObject(String bucketName, String key, byte[] bytes, String contentType, String sha256Hex);

    record Bucket(String name, boolean versioningEnabled) { }

    record Reader(String principalArn, String accessKey, String secretKey) {
        @Override
        public String toString() {
            return "Reader[principalArn=" + principalArn + ", credentials=REDACTED]";
        }
    }

    record StoredObject(String etag, String versionId, long size) { }
}
