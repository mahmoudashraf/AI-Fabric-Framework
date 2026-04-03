package com.ai.fabric.runtime;

import com.ai.fabric.runtime.web.admin.MigrationAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationAdminControllerTest {

    @Test
    void clearReturnsServiceUnavailableWhenVectorDatabaseServiceIsMissing() {
        MigrationAdminController controller = new MigrationAdminController(new EmptyObjectProvider<>());
        ReflectionTestUtils.setField(controller, "adminApiKey", "test-admin-key");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        MigrationAdminController.ClearRequest request = new MigrationAdminController.ClearRequest();
        request.setConfirm(true);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-ADMIN-API-KEY", "test-admin-key");

        var response = controller.clear(null, null, request, httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isInstanceOfAny(java.util.Map.class);
        assertThat(((java.util.Map<?, ?>) response.getBody()).get("message"))
            .isEqualTo("No VectorDatabaseService is configured for this runtime.");
    }

    private static final class EmptyObjectProvider<T> implements ObjectProvider<T> {

        @Override
        public T getObject(Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T getIfAvailable() {
            return null;
        }

        @Override
        public T getIfUnique() {
            return null;
        }

        @Override
        public T getObject() {
            throw new UnsupportedOperationException();
        }
    }
}
