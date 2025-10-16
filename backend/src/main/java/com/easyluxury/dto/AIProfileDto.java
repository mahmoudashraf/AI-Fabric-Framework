package com.easyluxury.dto;

import com.easyluxury.entity.AIProfileStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIProfileDto {
    
    private UUID id;
    
    private UUID userId;
    
    private String aiAttributes;
    
    private String cvFileUrl;
    
    private AIProfileStatus status;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
