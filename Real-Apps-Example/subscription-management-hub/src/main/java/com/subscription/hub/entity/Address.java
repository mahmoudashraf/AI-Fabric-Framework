package com.subscription.hub.entity;

import com.ai.infrastructure.annotation.AIContext;
import com.ai.infrastructure.annotation.AISearchable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @AISearchable(weight = 1.0)
    @Column(nullable = false)
    private String streetAddress;
    
    @Column(nullable = false)
    private String city;
    
    @Column(nullable = false)
    private String state;
    
    @Column(nullable = false)
    private String postalCode;
    
    @Column(nullable = false)
    private String country;
    
    @AIContext(contextKey = "addressType")
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AddressType type;  // BILLING, SHIPPING
    
    @AIContext(contextKey = "isValidated")
    @Builder.Default
    private Boolean isValidated = false;
    
    @AIContext(contextKey = "validationScore", dataType = "decimal")
    private Double validationScore;  // 0.0-1.0 confidence in address validity
    
    // Manual getters/setters for Lombok compatibility
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStreetAddress() { return streetAddress; }
    public void setStreetAddress(String streetAddress) { this.streetAddress = streetAddress; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public AddressType getType() { return type; }
    public void setType(AddressType type) { this.type = type; }
    public Boolean getIsValidated() { return isValidated; }
    public void setIsValidated(Boolean isValidated) { this.isValidated = isValidated; }
    public Double getValidationScore() { return validationScore; }
    public void setValidationScore(Double validationScore) { this.validationScore = validationScore; }
    
    public enum AddressType {
        BILLING, SHIPPING
    }
}
