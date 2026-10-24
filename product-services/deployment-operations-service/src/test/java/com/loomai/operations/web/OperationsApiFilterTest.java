package com.loomai.operations.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.operations.OperationsTestConfiguration;
import com.loomai.operations.config.OperationsProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OperationsApiFilterTest {

    @Test
    void limitsStreamedBodyEvenWhenContentLengthIsUnknown() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/internal/v1/deployment-operations/assess");
        raw.addHeader("Authorization", "Bearer " + OperationsTestConfiguration.API_KEY);
        raw.setContentType("application/json");
        raw.setContent(" ".repeat(16385).getBytes(StandardCharsets.UTF_8));
        HttpServletRequest streamed = new HttpServletRequestWrapper(raw) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        new OperationsApiFilter(new OperationsProperties(OperationsTestConfiguration.API_KEY,
            "{}", Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofSeconds(30), 16384), new ObjectMapper())
            .doFilter(streamed, response, (request, result) -> invoked.set(true));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(invoked).isFalse();
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
    }
}
