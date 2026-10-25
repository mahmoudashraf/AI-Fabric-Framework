package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifecycleCommandExecutorTest {
    @Test void unresolvedNativeWorkLogsLocationWithoutExceptionMessagesOrCredentials() {
        var store = mock(LifecycleCommandStore.class);
        var effects = mock(LifecycleEffects.class);
        var command = new Command("1", "command", "operation", "grant", "a".repeat(64), Phase.DELETE);
        var scope = mock(LifecycleCommandStore.Scope.class);
        var work = new LifecycleCommandStore.Work(command,
            new LifecycleCommandStore.Grant("grant", scope, "OPEN", true), "worker");
        when(store.claim("command")).thenReturn(work);
        when(effects.execute(work)).thenThrow(new IllegalStateException("password=private-credential"));
        var logger = (Logger) LoggerFactory.getLogger(LifecycleCommandExecutor.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            new LifecycleCommandExecutor(store, effects, Runnable::run).dispatch("command");
            verify(store).finish(work, Status.UNKNOWN, "NATIVE_OUTCOME_UNKNOWN", List.of("NATIVE_OUTCOME_UNKNOWN"));
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("operationId=operation", "phase=DELETE", "exceptionType=java.lang.IllegalStateException",
                    "source=com.ai.fabric.platform.backend.deployment.operations.lifecycle.")
                .doesNotContain("password", "private-credential");
            assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender); appender.stop();
        }
    }
}
