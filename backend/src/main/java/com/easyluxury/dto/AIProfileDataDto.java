package com.easyluxury.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIProfileDataDto {
    
    private String name;
    
    private String jobTitle;
    
    private List<CompanyDto> companies;
    
    private String profileSummary;
    
    private List<String> skills;
    
    private Integer experience;
    
    private Map<String, Object> photos;
    
    private Map<String, PhotoSuggestionDto> photoSuggestions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyDto {
        private String name;
        private String icon;
        private String position;
        private String duration;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoSuggestionDto {
        private Boolean required;
        private Integer count;
        private List<String> suggestions;
        private String description;
    }
}
