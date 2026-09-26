package com.ai.fabric.runtime.documents;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "loomai.documents")
public class DocumentKnowledgeProperties {

    private boolean enabled;
    private String entityType = "document";
    private List<String> allowedDatasetIds = new ArrayList<>(List.of("document-knowledge"));
    private String datasetHandleRefsJson = "{}";
    private String tempRoot = "./data/document-temp";
    private Duration reconcileInterval = Duration.ofSeconds(15);
    private Duration tempRetention = Duration.ofHours(1);
    private Duration evidenceRetention = Duration.ofDays(30);
    private Duration commandRetention = Duration.ofDays(30);
    private int retentionBatchSize = 100;
    private int maxAutomaticDeleteAttempts = 3;
    private Connector connector = new Connector();
    private Policy policy = new Policy();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public List<String> getAllowedDatasetIds() {
        return allowedDatasetIds;
    }

    public void setAllowedDatasetIds(List<String> allowedDatasetIds) {
        this.allowedDatasetIds = allowedDatasetIds;
    }

    public String getDatasetHandleRefsJson() {
        return datasetHandleRefsJson;
    }

    public void setDatasetHandleRefsJson(String datasetHandleRefsJson) {
        this.datasetHandleRefsJson = datasetHandleRefsJson;
    }

    public String getTempRoot() {
        return tempRoot;
    }

    public void setTempRoot(String tempRoot) {
        this.tempRoot = tempRoot;
    }

    public Duration getReconcileInterval() {
        return reconcileInterval;
    }

    public void setReconcileInterval(Duration reconcileInterval) {
        this.reconcileInterval = reconcileInterval;
    }

    public Duration getTempRetention() {
        return tempRetention;
    }

    public void setTempRetention(Duration tempRetention) {
        this.tempRetention = tempRetention;
    }

    public Duration getEvidenceRetention() {
        return evidenceRetention;
    }

    public void setEvidenceRetention(Duration evidenceRetention) {
        this.evidenceRetention = evidenceRetention;
    }

    public Duration getCommandRetention() {
        return commandRetention;
    }

    public void setCommandRetention(Duration commandRetention) {
        this.commandRetention = commandRetention;
    }

    public int getRetentionBatchSize() {
        return retentionBatchSize;
    }

    public void setRetentionBatchSize(int retentionBatchSize) {
        this.retentionBatchSize = retentionBatchSize;
    }

    public int getMaxAutomaticDeleteAttempts() {
        return maxAutomaticDeleteAttempts;
    }

    public void setMaxAutomaticDeleteAttempts(int maxAutomaticDeleteAttempts) {
        this.maxAutomaticDeleteAttempts = maxAutomaticDeleteAttempts;
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public enum ConnectorType {
        S3_COMPATIBLE_OBJECT_STORAGE,
        MOUNTED_FOLDER
    }

    public static class Connector {
        private ConnectorType type;
        private String bindingRef;
        private S3 s3 = new S3();
        private MountedFolder mountedFolder = new MountedFolder();

        public ConnectorType getType() {
            return type;
        }

        public void setType(ConnectorType type) {
            this.type = type;
        }

        public String getBindingRef() {
            return bindingRef;
        }

        public void setBindingRef(String bindingRef) {
            this.bindingRef = bindingRef;
        }

        public S3 getS3() {
            return s3;
        }

        public void setS3(S3 s3) {
            this.s3 = s3;
        }

        public MountedFolder getMountedFolder() {
            return mountedFolder;
        }

        public void setMountedFolder(MountedFolder mountedFolder) {
            this.mountedFolder = mountedFolder;
        }
    }

    public static class S3 {
        private String endpoint;
        private String region = "us-east-1";
        private String bucket;
        private String prefix = "";
        private String accessKey;
        private String secretKey;
        private String sessionToken;
        private boolean pathStyleAccess = true;
        private boolean allowInsecureEndpoint;
        private String allowedEndpointHost;
        private boolean allowPrivateEndpoint;
        private boolean objectVersioningAvailable;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
        public boolean isPathStyleAccess() { return pathStyleAccess; }
        public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }
        public boolean isAllowInsecureEndpoint() { return allowInsecureEndpoint; }
        public void setAllowInsecureEndpoint(boolean allowInsecureEndpoint) { this.allowInsecureEndpoint = allowInsecureEndpoint; }
        public String getAllowedEndpointHost() { return allowedEndpointHost; }
        public void setAllowedEndpointHost(String allowedEndpointHost) { this.allowedEndpointHost = allowedEndpointHost; }
        public boolean isAllowPrivateEndpoint() { return allowPrivateEndpoint; }
        public void setAllowPrivateEndpoint(boolean allowPrivateEndpoint) { this.allowPrivateEndpoint = allowPrivateEndpoint; }
        public boolean isObjectVersioningAvailable() { return objectVersioningAvailable; }
        public void setObjectVersioningAvailable(boolean objectVersioningAvailable) { this.objectVersioningAvailable = objectVersioningAvailable; }
    }

    public static class MountedFolder {
        private String root;

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static class Policy {
        private long maxSourceBytes = 1_048_576;
        private int maxSources = 100;
        private long maxTotalIndexedBytes = 104_857_600;
        private int maxChunksPerSource = 100;
        private int maxChunkCharacters = 8_000;
        private int maxTotalCharacters = 250_000;
        private int previewMaxChunks = 10;
        private int previewMaxCharactersPerChunk = 500;
        private int discoveryPageSize = 50;
        private boolean trustedAutoIndexingAllowed;
        private List<String> allowedExtensions = new ArrayList<>(List.of(".txt", ".json"));
        private List<String> allowedMediaTypes = new ArrayList<>(List.of("text/plain", "application/json"));
        private List<String> jsonContentKeys = new ArrayList<>(List.of("content", "text", "body"));
        private List<String> allowedMetadataKeys = new ArrayList<>(List.of(
            "originalFilename", "locale", "sourceCategory"
        ));

        public long getMaxSourceBytes() { return maxSourceBytes; }
        public void setMaxSourceBytes(long maxSourceBytes) { this.maxSourceBytes = maxSourceBytes; }
        public int getMaxSources() { return maxSources; }
        public void setMaxSources(int maxSources) { this.maxSources = maxSources; }
        public long getMaxTotalIndexedBytes() { return maxTotalIndexedBytes; }
        public void setMaxTotalIndexedBytes(long maxTotalIndexedBytes) { this.maxTotalIndexedBytes = maxTotalIndexedBytes; }
        public int getMaxChunksPerSource() { return maxChunksPerSource; }
        public void setMaxChunksPerSource(int maxChunksPerSource) { this.maxChunksPerSource = maxChunksPerSource; }
        public int getMaxChunkCharacters() { return maxChunkCharacters; }
        public void setMaxChunkCharacters(int maxChunkCharacters) { this.maxChunkCharacters = maxChunkCharacters; }
        public int getMaxTotalCharacters() { return maxTotalCharacters; }
        public void setMaxTotalCharacters(int maxTotalCharacters) { this.maxTotalCharacters = maxTotalCharacters; }
        public int getPreviewMaxChunks() { return previewMaxChunks; }
        public void setPreviewMaxChunks(int previewMaxChunks) { this.previewMaxChunks = previewMaxChunks; }
        public int getPreviewMaxCharactersPerChunk() { return previewMaxCharactersPerChunk; }
        public void setPreviewMaxCharactersPerChunk(int previewMaxCharactersPerChunk) { this.previewMaxCharactersPerChunk = previewMaxCharactersPerChunk; }
        public int getDiscoveryPageSize() { return discoveryPageSize; }
        public void setDiscoveryPageSize(int discoveryPageSize) { this.discoveryPageSize = discoveryPageSize; }
        public boolean isTrustedAutoIndexingAllowed() { return trustedAutoIndexingAllowed; }
        public void setTrustedAutoIndexingAllowed(boolean trustedAutoIndexingAllowed) { this.trustedAutoIndexingAllowed = trustedAutoIndexingAllowed; }
        public List<String> getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(List<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
        public List<String> getAllowedMediaTypes() { return allowedMediaTypes; }
        public void setAllowedMediaTypes(List<String> allowedMediaTypes) { this.allowedMediaTypes = allowedMediaTypes; }
        public List<String> getJsonContentKeys() { return jsonContentKeys; }
        public void setJsonContentKeys(List<String> jsonContentKeys) { this.jsonContentKeys = jsonContentKeys; }
        public List<String> getAllowedMetadataKeys() { return allowedMetadataKeys; }
        public void setAllowedMetadataKeys(List<String> allowedMetadataKeys) { this.allowedMetadataKeys = allowedMetadataKeys; }
    }
}
