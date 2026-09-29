package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TriageAiResponse;
import com.pretriage.backend.exceptions.ProveedorIaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;

import java.util.Map;

class TriageIaClientTest {
    private ChatClient chatClient;
    private TriageIaClient client;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        client = new TriageIaClient(builder, new ObjectMapper());
    }

    @Test
    void aceptaResultadoIntermedioConCamposNumericosNulos() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn("""
                        {
                          "finalizado": false,
                          "mensaje": "¿Desde cuando comenzaron los sintomas?",
                          "resultado": {
                            "motivoConsulta": "dolor de garganta",
                            "sintomas": ["dolor de garganta"],
                            "inicio": "ayer",
                            "evolucion": "sin cambios",
                            "intensidadDolor": null,
                            "signosAlarma": [],
                            "antecedentesRelevantes": [],
                            "medicamentos": [],
                            "alergias": [],
                            "posibilidadEmbarazo": "no informado",
                            "observaciones": "no informado",
                            "nivelPrioridad": null,
                            "requiereAtencionInmediata": false,
                            "recomendacionSeguridad": "Si empeora, consulte."
                          }
                        }
                        """);
        clearInvocations(chatClient);

        TriageAiResponse response = client.consultar("sistema", "conversacion");

        assertFalse(response.finalizado());
        assertEquals(null, response.resultado().nivelPrioridad());
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void rechazaCierreSinResultadoYAcotaLosIntentos() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"finalizado\":true,\"mensaje\":\"Listo\",\"resultado\":null}");
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));

        verify(chatClient, times(1)).prompt();
    }

    @Test
    void rechazaJsonMalformadoSinReintentosIlimitados() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn("{esto no es json");
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));

        verify(chatClient, times(1)).prompt();
    }

    @Test
    void aceptaCierreValidoConPrioridad() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn(response(true, "5", "3", "[\"dolor de garganta\"]"));
        clearInvocations(chatClient);

        TriageAiResponse result = client.consultar("sistema", "conversacion");

        assertTrue(result.finalizado());
        assertEquals(3, result.resultado().nivelPrioridad());
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void rechazaCierreConPrioridadNula() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn(response(true, "5", "null", "[\"dolor\"]"));
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void rechazaNumerosFueraDeRango() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn(response(false, "11", "null", "[\"dolor\"]"));
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void rechazaPrioridadFueraDeRango() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn(response(true, "5", "6", "[\"dolor\"]"));
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void rechazaSintomasNoInformadosEnCierre() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn(response(true, "2", "2", "[\"no informado\"]"));
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void rechazaRespuestaRealDeAlarmaSinMotivoUtil() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn("""
                        {
                          "finalizado": true,
                          "resultado": {
                            "motivoConsulta": "no informado",
                            "sintomas": ["dolor de pecho muy fuerte", "dificultad para respirar"],
                            "inicio": "desde ayer",
                            "evolucion": "no informado",
                            "intensidadDolor": null,
                            "signosAlarma": ["dificultad para respirar"],
                            "antecedentesRelevantes": [],
                            "medicamentos": [],
                            "alergias": [],
                            "posibilidadEmbarazo": "no informado",
                            "observaciones": "Dolor de pecho muy fuerte y dificultad para respirar desde hace diez minutos.",
                            "nivelPrioridad": 5,
                            "requiereAtencionInmediata": true,
                            "recomendacionSeguridad": "Busque atencion medica urgente."
                          },
                          "mensaje": "Gracias. Registre tus sintomas y la preclasificacion."
                        }
                        """);
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion", true));
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void fallaConUnaSolaLlamadaSiElProveedorNoResponde() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenThrow(new IllegalStateException("provider unavailable"));
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void enviaEsquemaNativoDeOllamaConRangosNumericos() {
        ChatClient.ChatClientRequestSpec request = chatClient.prompt();
        when(request.options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn(response(false, "null", "null", "[\"dolor\"]"));
        clearInvocations(chatClient, request);

        client.consultar("sistema", "conversacion");

        ArgumentCaptor<ChatOptions.Builder> options = ArgumentCaptor.forClass(ChatOptions.Builder.class);
        verify(request).options(options.capture());
        OllamaChatOptions built = assertInstanceOf(OllamaChatOptions.Builder.class, options.getValue()).build();
        String schema = built.getFormat().toString();
        assertTrue(schema.contains("minimum=0"));
        assertTrue(schema.contains("maximum=10"));
        assertTrue(schema.contains("minimum=1"));
        assertTrue(schema.contains("maximum=5"));
    }

    @Test
    void modoFinalExigeFinalizadoTrueYPrioridadNoNulaEnElEsquema() {
        ChatClient.ChatClientRequestSpec request = chatClient.prompt();
        when(request.options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn(response(true, "5", "3", "[\"dolor\"]"));
        clearInvocations(chatClient, request);

        client.consultar("sistema", "conversacion", true);

        ArgumentCaptor<ChatOptions.Builder> options = ArgumentCaptor.forClass(ChatOptions.Builder.class);
        verify(request).options(options.capture());
        OllamaChatOptions built = assertInstanceOf(OllamaChatOptions.Builder.class, options.getValue()).build();
        Map<?, ?> root = (Map<?, ?>) built.getFormat();
        Map<?, ?> properties = (Map<?, ?>) root.get("properties");
        Map<?, ?> finalizado = (Map<?, ?>) properties.get("finalizado");
        Map<?, ?> resultado = (Map<?, ?>) properties.get("resultado");
        Map<?, ?> resultSchema = resultado;
        Map<?, ?> resultProperties = (Map<?, ?>) resultSchema.get("properties");
        Map<?, ?> priority = (Map<?, ?>) resultProperties.get("nivelPrioridad");
        assertEquals(java.util.List.of(true), finalizado.get("enum"));
        assertEquals("integer", priority.get("type"));
        assertFalse(priority.containsKey("anyOf"));
    }

    @Test
    void modoFinalRechazaRespuestaQueAunDiceNoFinalizado() {
        when(chatClient.prompt().options(any()).system(anyString()).user(anyString()).call().content())
                .thenReturn(response(false, "null", "null", "[\"dolor\"]"));
        clearInvocations(chatClient);

        assertThrows(ProveedorIaException.class,
                () -> client.consultar("sistema", "conversacion", true));
        verify(chatClient, times(1)).prompt();
    }

    private String response(boolean finalizado, String intensidad, String prioridad, String sintomas) {
        return """
                {
                  "finalizado": %s,
                  "mensaje": "Respuesta",
                  "resultado": {
                    "motivoConsulta": "dolor de garganta",
                    "sintomas": %s,
                    "inicio": "ayer",
                    "evolucion": "sin cambios",
                    "intensidadDolor": %s,
                    "signosAlarma": [],
                    "antecedentesRelevantes": [],
                    "medicamentos": [],
                    "alergias": [],
                    "posibilidadEmbarazo": "no informado",
                    "observaciones": "no informado",
                    "nivelPrioridad": %s,
                    "requiereAtencionInmediata": false,
                    "recomendacionSeguridad": "Consulte si empeora."
                  }
                }
                """.formatted(finalizado, sintomas, intensidad, prioridad);
    }
}
