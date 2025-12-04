package com.ai.behavior.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingPolicy {

    private boolean enabled;
    private String textField;
    private int minTextLength = 8;

    public static EmbeddingPolicy disabled() {
        return new EmbeddingPolicy(false, null, 8);
    }
}
