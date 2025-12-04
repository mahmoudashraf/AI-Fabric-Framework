package com.ai.behavior.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BehaviorSignalAttributeDefinition {

    private String name;
    private AttributeType type = AttributeType.STRING;
    private boolean required;
    private Integer maxLength;
    private Double minimum;
    private Double maximum;
    private List<String> enumValues = Collections.emptyList();
}
