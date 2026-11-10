package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthoringDocumentBodyLimitAdviceTest {
    private static final int LIMIT = AuthoringDocumentBodyLimitAdvice.MAX_BODY_BYTES;
    private final AuthoringDocumentBodyLimitAdvice advice = new AuthoringDocumentBodyLimitAdvice();
    private final ObjectMapper mapper = new ObjectMapper();
    private ConfigurationAssistantService service;
    private MockMvc mvc;
    private MethodParameter parameter;

    @BeforeEach
    void setup() throws Exception {
        service = mock(ConfigurationAssistantService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ConfigurationAssistantController(service), new OtherController())
                .setControllerAdvice(advice).build();
        parameter = new MethodParameter(ConfigurationAssistantController.class.getMethod("source", String.class,
                ConfigurationAssistantService.SourceRequest.class), 1);
    }

    @Test
    void supportsEveryControllerRequestBodyDto() {
        int bodies = 0;
        for (var method : ConfigurationAssistantController.class.getDeclaredMethods()) {
            for (int i = 0; i < method.getParameterCount(); i++) {
                if (method.getParameters()[i].isAnnotationPresent(RequestBody.class)) {
                    var body = new MethodParameter(method, i);
                    assertThat(advice.supports(body, body.getGenericParameterType(), MappingJackson2HttpMessageConverter.class)).isTrue();
                    bodies++;
                }
            }
        }
        assertThat(bodies).isGreaterThanOrEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/sessions", "/sessions/session/sources", "/sessions/session/sources/reuse", "/sessions/session/turns"})
    void rejectsOversizedMalformedBodiesBeforeJacksonOrService(String path) throws Exception {
        byte[] malformed = new byte[LIMIT + 1];
        Arrays.fill(malformed, (byte) '!');
        mvc.perform(post("/api/configuration-assistant" + path).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "body-limit-test").content(malformed))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOversizedChunkedMvcBodyWithoutContentLength() throws Exception {
        mvc.perform(servletContext -> {
            MockHttpServletRequest request = new MockHttpServletRequest(servletContext) {
                @Override public int getContentLength() { return -1; }
                @Override public long getContentLengthLong() { return -1; }
            };
            request.setMethod("POST");
            request.setRequestURI("/api/configuration-assistant/sessions/session/sources");
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.addHeader(HttpHeaders.TRANSFER_ENCODING, "chunked");
            request.setContent(new byte[LIMIT + 1]);
            return request;
        }).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }

    @Test
    void stopsReadingAtLimitPlusOneEvenForAnUnendingChunkedStream() {
        AtomicInteger read = new AtomicInteger();
        InputStream stream = new InputStream() {
            @Override public int read() { read.incrementAndGet(); return '!'; }
            @Override public int read(byte[] bytes, int offset, int length) {
                Arrays.fill(bytes, offset, offset + length, (byte) '!');
                read.addAndGet(length);
                return length;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
        assertThatThrownBy(() -> bounded(input(headers, stream)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(413));
        assertThat(read.get()).isEqualTo(LIMIT + 1);
    }

    @Test
    void declaredOversizeIsRejectedWithoutReadingTheBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(LIMIT + 1L);
        assertThatThrownBy(() -> bounded(input(headers, new InputStream() {
            @Override public int read() { throw new AssertionError("Oversized Content-Length must fail before reading"); }
        }))).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(413));
    }

    @Test
    void underreportedLengthDoesNotBypassTheStreamLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(1);
        assertThatThrownBy(() -> bounded(input(headers, new ByteArrayInputStream(new byte[LIMIT + 1]))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(413));
    }

    @Test
    void exactByteLimitIsPreservedForJackson() throws Exception {
        byte[] bytes = new byte[LIMIT];
        Arrays.fill(bytes, (byte) 'x');
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var result = bounded(input(headers, new ByteArrayInputStream(bytes)));
        assertThat(result.getBody().readAllBytes()).isEqualTo(bytes);
        assertThat(result.getHeaders()).isSameAs(headers);
    }

    @Test
    void acceptsARealTwoMiBDocumentBase64Envelope() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(new byte[AuthoringDocumentImporter.MAX_FILE_BYTES]);
        var body = mapper.createObjectNode().put("title", "At file limit").put("mediaType", "text/plain").put("base64", base64);
        byte[] json = mapper.writeValueAsBytes(body);
        assertThat(json.length).isLessThan(LIMIT);
        when(service.addSource(eq("session"), any())).thenReturn(mapper.createObjectNode().put("id", "session"));
        mvc.perform(post("/api/configuration-assistant/sessions/session/sources")
                        .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
        verify(service).addSource("session", new ConfigurationAssistantService.SourceRequest("At file limit", "text/plain", null, base64, null));
    }

    @Test
    void normalDtoStillReachesTheExistingController() throws Exception {
        when(service.create(any())).thenReturn(mapper.createObjectNode().put("id", "session"));
        mvc.perform(post("/api/configuration-assistant/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceKind\":\"DEPLOYMENT_DRAFT\",\"resourceId\":\"draft\",\"task\":\"ACTIONS\"}"))
                .andExpect(status().isOk());
        verify(service).create(new ConfigurationAssistantService.CreateSession("DEPLOYMENT_DRAFT", "draft", "ACTIONS"));
    }

    @Test
    void adviceDoesNotImposeAnAssistantLimitOnUnrelatedControllers() throws Exception {
        byte[] json = ("{\"value\":\"" + "x".repeat(LIMIT) + "\"}").getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/other-body-limit-test").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
        verifyNoInteractions(service);
    }

    private HttpInputMessage bounded(HttpInputMessage message) throws Exception {
        return advice.beforeBodyRead(message, parameter, parameter.getGenericParameterType(), MappingJackson2HttpMessageConverter.class);
    }

    private static HttpInputMessage input(HttpHeaders headers, InputStream stream) {
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return stream; }
            @Override public HttpHeaders getHeaders() { return headers; }
        };
    }

    @RestController
    static class OtherController {
        @PostMapping("/other-body-limit-test")
        public int size(@RequestBody JsonNode body) { return body.path("value").asText().length(); }
    }
}
