package com.ai.fabric.observability;

import com.ai.fabric.observability.logging.LoomaiMdcKeys;
import com.ai.fabric.observability.logging.LoomaiMdcTaskDecorator;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class LoomaiMdcTaskDecoratorTest {

    @Test
    void carriesMdcAcrossDecoratedExecution() {
        LoomaiMdcTaskDecorator decorator = new LoomaiMdcTaskDecorator();
        MDC.put(LoomaiMdcKeys.REQUEST_ID, "req-123");
        try {
            String[] observed = new String[1];
            decorator.decorate(() -> observed[0] = MDC.get(LoomaiMdcKeys.REQUEST_ID)).run();
            assertThat(observed[0]).isEqualTo("req-123");
        } finally {
            MDC.clear();
        }
    }
}
