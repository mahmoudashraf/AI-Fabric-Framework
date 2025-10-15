package com.easyluxury.dto;

import com.easyluxury.entity.AIProfile;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIProfileDto {
    
    private UUID id;
    private UUID userId;
    private String aiAttributes;
    private String cvFileUrl;
    private AIProfile.AIProfileStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
