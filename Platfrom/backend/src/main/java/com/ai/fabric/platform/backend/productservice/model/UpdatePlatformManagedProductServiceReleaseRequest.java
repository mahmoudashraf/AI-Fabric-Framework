package com.ai.fabric.platform.backend.productservice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePlatformManagedProductServiceReleaseRequest(
    @NotBlank @Size(max = 512)
    @Pattern(regexp = "[A-Za-z0-9._/-]+", message = "must be a registry image repository without a tag")
    String imageRepository,
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{40}", message = "must be an immutable 40-character source commit tag")
    String imageTag,
    @NotBlank
    @Pattern(regexp = "sha256:[a-f0-9]{64}", message = "must be a sha256 image digest")
    String imageDigest,
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{40}", message = "must be a 40-character source commit")
    String sourceCommit
) {
}
