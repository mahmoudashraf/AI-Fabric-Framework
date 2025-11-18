package com.ai.behavior.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BehaviorSignalDefinition {

    private String id;
    private String domain;
    private String summary;
    private String version = "1.0";
    private List<String> tags = Collections.emptyList();
    private List<BehaviorSignalAttributeDefinition> attributes = Collections.emptyList();
    private EmbeddingPolicy embedding = EmbeddingPolicy.disabled();
    private Map<String, Object> metricHints = Collections.emptyMap();

    public boolean hasTag(String tag) {
        if (tags == null || tag == null) {
            return false;
        }
        return tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag));
    }
}
