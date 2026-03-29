package com.ai.fabric.platform.backend.secret.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_secrets")
public class PlatformSecretEntity {

    @Id
    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "secret_value", nullable = false, columnDefinition = "CLOB")
    private String secretValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSecretValue() {
        return secretValue;
    }

    public void setSecretValue(String secretValue) {
        this.secretValue = secretValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
