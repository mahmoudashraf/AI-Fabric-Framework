package com.ai.fabric.vectorization.model;

public record TargetConnectionDescriptor(
    String targetType,
    String baseUrl,
    String batchPath,
    String vectorSpacesPath,
    String authHeader,
    String apiKey,
    String workStatusPath,
    String privateAuthorizationHeader,
    String privateAuthorization
) {

    public TargetConnectionDescriptor(
        String targetType,
        String baseUrl,
        String batchPath,
        String vectorSpacesPath,
        String authHeader,
        String apiKey
    ) {
        this(
            targetType,
            baseUrl,
            batchPath,
            vectorSpacesPath,
            authHeader,
            apiKey,
            null,
            null,
            null
        );
    }

    @Override
    public String toString() {
        return "TargetConnectionDescriptor[targetType=" + targetType
            + ", baseUrl=" + baseUrl
            + ", batchPath=" + batchPath
            + ", vectorSpacesPath=" + vectorSpacesPath
            + ", authHeader=" + authHeader
            + ", apiKey=<masked>"
            + ", workStatusPath=" + workStatusPath
            + ", privateAuthorizationHeader=" + privateAuthorizationHeader
            + ", privateAuthorization=<masked>]";
    }
}
