package com.ai.fabric.platform.backend.documentstorage.provider;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "platform.document-storage.hosted")
public class HostedDocumentStorageProperties {
    private boolean enabled;
    private String region = "eu-north-1";
    private String accountId = "316658062476";
    private String bucketNamespace = "loomai-customer-docs";
    private String controlPlaneId = "platform";
    private String accessKey;
    private String secretKey;
    private String sessionToken;
    private String readerPermissionsBoundaryArn;
    private int maxObjectBytes = 1_048_576;
}
