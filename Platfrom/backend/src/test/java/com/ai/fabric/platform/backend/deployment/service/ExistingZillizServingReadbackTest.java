package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ExistingZillizServingReadbackTest {
    @SuppressWarnings("unchecked")
    @Test void usesOnlyServingCredentialAndDocumentedReadOnlyCollectionList() throws Exception {
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn("{\"code\":0,\"data\":[\"scope_vehicle\",\"scope_document\"]}");
        when(http.<String>send(argThat(request -> request.method().equals("POST")
            && request.uri().toString().equals("https://cluster.zilliz.com/v2/vectordb/collections/list")
            && request.headers().firstValue("Authorization").orElseThrow().equals("Bearer serving-user:serving-password")),
            any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client = new ZillizCloudControlPlaneClient(new ObjectMapper(), http);
        assertThat(client.listServingCollections("https://cluster.zilliz.com/", "serving-user", "serving-password"))
            .containsExactly("scope_vehicle", "scope_document");
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"data\":[]}", "{\"code\":0}", "{\"code\":4294967296,\"data\":[]}",
        "{\"code\":-4294967296,\"data\":[]}", "{\"code\":0.0,\"data\":[]}", "{\"code\":100,\"message\":\"serving-password\",\"data\":[]}",
        "{\"code\":0,\"data\":[1]}", "{\"code\":0,\"data\":[\"\"]}"})
    void rejectsDeniedOrIncompleteMetadataWithoutEchoingUpstreamSecrets(String body) throws Exception {
        var http = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(body);
        when(http.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client = new ZillizCloudControlPlaneClient(new ObjectMapper(), http);
        assertThatThrownBy(() -> client.listServingCollections("https://cluster.zilliz.com", "user", "serving-password"))
            .isInstanceOf(RailwayProvisioningException.class).hasMessageNotContaining("serving-password");
    }

    @SuppressWarnings("unchecked")
    @Test void rejectsHttpDenialWithoutLeakingResponseBody() throws Exception {
        var http = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401); when(response.body()).thenReturn("serving-password");
        when(http.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client = new ZillizCloudControlPlaneClient(new ObjectMapper(), http);
        assertThatThrownBy(() -> client.listServingCollections("https://cluster.zilliz.com", "user", "serving-password"))
            .hasMessageContaining("HTTP 401").hasMessageNotContaining("serving-password");
    }
}
