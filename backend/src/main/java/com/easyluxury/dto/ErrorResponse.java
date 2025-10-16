package com.easyluxury.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private String code;
    
    private String message;
    
    private List<String> details;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
