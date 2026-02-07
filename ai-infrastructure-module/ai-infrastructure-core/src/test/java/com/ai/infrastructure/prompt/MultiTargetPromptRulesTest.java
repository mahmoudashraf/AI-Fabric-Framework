package com.ai.infrastructure.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class MultiTargetPromptRulesTest {

    @Test
    void compoundSystemPrompt_shouldIncludeMultiTargetRules() {
        PromptTemplateStore store = new ClasspathPromptTemplateStore(new DefaultResourceLoader());
        PromptTemplate system = store.load(new PromptTemplateKey(
            "intent-extraction/compound",
            "v1",
            "system"
        ));

        assertThat(system.template()).contains("MULTI-TARGET");
        assertThat(system.template()).contains("one ACTION intent per target");
    }

    @Test
    void multiStepClassify_shouldMentionPinnedTargetsSection() {
        PromptTemplateStore store = new ClasspathPromptTemplateStore(new DefaultResourceLoader());
        PromptTemplate classify = store.load(new PromptTemplateKey(
            "intent-extraction/multi-step",
            "v1",
            "classify"
        ));

        assertThat(classify.template()).contains("PINNED TARGETS");
    }
}

