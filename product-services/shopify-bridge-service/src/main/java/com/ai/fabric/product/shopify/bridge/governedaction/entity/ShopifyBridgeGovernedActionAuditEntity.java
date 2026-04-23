package com.ai.fabric.product.shopify.bridge.governedaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopify_bridge_governed_action_audits")
public class ShopifyBridgeGovernedActionAuditEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String shopDomain;

    @Column(nullable = false)
    private String shopperSessionId;

    @Column(nullable = false)
    private String actionType;

    @Column(nullable = false)
    private String actionPackage;

    @Column(nullable = false)
    private String surfaceId;

    @Column(nullable = false)
    private String pageType;

    private String productId;

    private String productHandle;

    private String productTitle;

    private String variantId;

    private Integer requestedQuantity;

    private Integer targetQuantity;

    private Integer resultingQuantity;

    private String cartLineKey;

    @Column(nullable = false)
    private boolean confirmationRequired;

    @Column(nullable = false)
    private boolean confirmationAccepted;

    @Column(nullable = false)
    private String status;

    @Column(length = 500)
    private String message;

    private Instant expiresAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getShopDomain() {
        return shopDomain;
    }

    public void setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
    }

    public String getShopperSessionId() {
        return shopperSessionId;
    }

    public void setShopperSessionId(String shopperSessionId) {
        this.shopperSessionId = shopperSessionId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionPackage() {
        return actionPackage;
    }

    public void setActionPackage(String actionPackage) {
        this.actionPackage = actionPackage;
    }

    public String getSurfaceId() {
        return surfaceId;
    }

    public void setSurfaceId(String surfaceId) {
        this.surfaceId = surfaceId;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductHandle() {
        return productHandle;
    }

    public void setProductHandle(String productHandle) {
        this.productHandle = productHandle;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public String getVariantId() {
        return variantId;
    }

    public void setVariantId(String variantId) {
        this.variantId = variantId;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(Integer requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public Integer getTargetQuantity() {
        return targetQuantity;
    }

    public void setTargetQuantity(Integer targetQuantity) {
        this.targetQuantity = targetQuantity;
    }

    public Integer getResultingQuantity() {
        return resultingQuantity;
    }

    public void setResultingQuantity(Integer resultingQuantity) {
        this.resultingQuantity = resultingQuantity;
    }

    public String getCartLineKey() {
        return cartLineKey;
    }

    public void setCartLineKey(String cartLineKey) {
        this.cartLineKey = cartLineKey;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public void setConfirmationRequired(boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
    }

    public boolean isConfirmationAccepted() {
        return confirmationAccepted;
    }

    public void setConfirmationAccepted(boolean confirmationAccepted) {
        this.confirmationAccepted = confirmationAccepted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
