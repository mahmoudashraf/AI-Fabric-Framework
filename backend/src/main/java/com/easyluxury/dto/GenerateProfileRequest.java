package com.easyluxury.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateProfileRequest {
    
    @NotBlank(message = "CV content is required")
    @Size(min = 100, max = 50000, message = "CV content must be between 100 and 50000 characters")
    private String cvContent;
}
