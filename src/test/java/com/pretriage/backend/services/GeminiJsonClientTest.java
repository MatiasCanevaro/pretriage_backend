package com.pretriage.backend.services;

import com.pretriage.backend.exceptions.ProveedorIaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiJsonClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpClient http;
    private HttpResponse<String> response;
    private GeminiJsonClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        http = mock(HttpClient.class);
        response = mock(HttpResponse.class);
        client = new GeminiJsonClient(properties("test-key", "gemini-3.5-flash-lite"), mapper, http);
    }

    private GeminiChatProperties properties(String key, String model) {
        return new GeminiChatProperties(key, model, "https://example.invalid/v1beta",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private Map<String, Object> schema() { return Map.of("type", "object"); }

    @Test
    void usaHeaderDeAutenticacionPlazoYUnaSolaPeticion() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                {"thought":true,"text":"internal"},{"text":"{\\\"ok\\\":"},{"text":"true}"}]}}]}
                """);
        when(http.<String>send(any(), any())).thenReturn(response);
        assertEquals("{\"ok\":true}", client.generar("sistema", "prueba", schema()));
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(1)).send(request.capture(), any());
        assertEquals("test-key", request.getValue().headers().firstValue("x-goog-api-key").orElseThrow());
        assertFalse(request.getValue().uri().toString().contains("test-key"));
        assertEquals(Duration.ofSeconds(2), request.getValue().timeout().orElseThrow());
        assertEquals("POST", request.getValue().method());
        var subscriber = HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
        request.getValue().bodyPublisher().orElseThrow().subscribe(
                new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }
                    public void onNext(java.nio.ByteBuffer buffer) { subscriber.onNext(java.util.List.of(buffer)); }
                    public void onError(Throwable error) { subscriber.onError(error); }
                    public void onComplete() { subscriber.onComplete(); }
                });
        var body = mapper.readTree(subscriber.getBody().toCompletableFuture().join());
        var format = body.path("generationConfig").path("responseFormat").path("text");
        assertEquals("APPLICATION_JSON", format.path("mimeType").asText());
        assertEquals(mapper.valueToTree(schema()), format.path("schema"));
        assertEquals("prueba", body.path("contents").get(0).path("parts").get(0).path("text").asText());
    }

    @Test
    void cuotaAgotadaNoReintentaNiExponeElCuerpo() throws Exception {
        when(response.statusCode()).thenReturn(429);
        when(http.<String>send(any(), any())).thenReturn(response);
        var error = assertThrows(ProveedorIaException.class, () -> client.generar("sistema", "prueba", schema()));
        assertTrue(error.getCause().getMessage().contains("429"));
        verify(response, never()).body();
        verify(http, times(1)).send(any(), any());
    }

    @Test
    void rechazaRespuestasTruncadasBloqueadasOVacias() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(http.<String>send(any(), any())).thenReturn(response);
        for (String body : new String[]{"{}", "{\"candidates\":[]}",
                "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}",
                "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}",
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[]}}]}"}) {
            when(response.body()).thenReturn(body);
            assertThrows(ProveedorIaException.class, () -> client.generar("sistema", "prueba", schema()));
        }
    }

    @Test
    void timeoutNoReintenta() throws Exception {
        when(http.<String>send(any(), any())).thenThrow(new HttpTimeoutException("timeout"));
        assertThrows(ProveedorIaException.class, () -> client.generar("sistema", "prueba", schema()));
        verify(http, times(1)).send(any(), any());
    }

    @Test
    void configuracionInvalidaNoEnviaPeticiones() {
        for (GeminiChatProperties properties : new GeminiChatProperties[]{
                properties("", "gemini-3.5-flash-lite"), properties("test-key", "invalid/model")}) {
            var invalid = new GeminiJsonClient(properties, mapper, http);
            assertThrows(ProveedorIaException.class, () -> invalid.generar("sistema", "prueba", schema()));
        }
        verifyNoInteractions(http);
    }

    @Test
    void interrupcionRestauraLaBanderaDelHilo() throws Exception {
        when(http.<String>send(any(), any())).thenThrow(new InterruptedException());
        try {
            assertThrows(ProveedorIaException.class, () -> client.generar("sistema", "prueba", schema()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
