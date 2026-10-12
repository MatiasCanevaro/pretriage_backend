package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TriageAiResponse;
import com.pretriage.backend.exceptions.ProveedorIaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private GeminiJsonClient geminiClient;
    private TriageIaClient client;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiJsonClient.class);
        client = new TriageIaClient(geminiClient, new ObjectMapper());
    }

    @Test
    void aceptaResultadoIntermedioConCamposNumericosNulos() {
        when(geminiClient.generar(anyString(), anyString(), any()))
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
        clearInvocations(geminiClient);

        TriageAiResponse response = client.consultar("sistema", "conversacion");

        assertFalse(response.finalizado());
        assertEquals(null, response.resultado().nivelPrioridad());
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void rechazaCierreSinResultadoYAcotaLosIntentos() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn("{\"finalizado\":true,\"mensaje\":\"Listo\",\"resultado\":null}");
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));

        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void rechazaJsonMalformadoSinReintentosIlimitados() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn("{esto no es json");
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));

        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void aceptaCierreValidoConPrioridad() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn(response(true, "5", "3", "[\"dolor de garganta\"]"));
        clearInvocations(geminiClient);

        TriageAiResponse result = client.consultar("sistema", "conversacion");

        assertTrue(result.finalizado());
        assertEquals(3, result.resultado().nivelPrioridad());
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void rechazaCierreConPrioridadNula() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn(response(true, "5", "null", "[\"dolor\"]"));
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void rechazaNumerosFueraDeRango() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn(response(false, "11", "null", "[\"dolor\"]"));
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void rechazaPrioridadFueraDeRango() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn(response(true, "5", "6", "[\"dolor\"]"));
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void rechazaSintomasNoInformadosEnCierre() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn(response(true, "2", "2", "[\"no informado\"]"));
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void rechazaRespuestaRealDeAlarmaSinMotivoUtil() {
        when(geminiClient.generar(anyString(), anyString(), any()))
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
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion", true));
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    void fallaConUnaSolaLlamadaSiElProveedorNoResponde() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("provider unavailable"));
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class, () -> client.consultar("sistema", "conversacion"));
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void enviaEsquemaNativoDeGeminiConRangosNumericos() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn(response(false, "null", "null", "[\"dolor\"]"));
        client.consultar("sistema", "conversacion");
        ArgumentCaptor<Map<String, Object>> schema = ArgumentCaptor.forClass(Map.class);
        verify(geminiClient).generar(anyString(), anyString(), schema.capture());
        String value = schema.getValue().toString();
        assertTrue(value.contains("minimum=0"));
        assertTrue(value.contains("maximum=10"));
        assertTrue(value.contains("minimum=1"));
        assertTrue(value.contains("maximum=5"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modoFinalExigeFinalizadoTrueYPrioridadNoNulaEnElEsquema() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn(response(true, "5", "3", "[\"dolor\"]"));
        client.consultar("sistema", "conversacion", true);
        ArgumentCaptor<Map<String, Object>> schema = ArgumentCaptor.forClass(Map.class);
        verify(geminiClient).generar(anyString(), anyString(), schema.capture());
        Map<?, ?> properties = (Map<?, ?>) schema.getValue().get("properties");
        Map<?, ?> finalizado = (Map<?, ?>) properties.get("finalizado");
        Map<?, ?> resultSchema = (Map<?, ?>) properties.get("resultado");
        Map<?, ?> resultProperties = (Map<?, ?>) resultSchema.get("properties");
        Map<?, ?> priority = (Map<?, ?>) resultProperties.get("nivelPrioridad");
        assertEquals(java.util.List.of(true), finalizado.get("enum"));
        assertEquals("integer", priority.get("type"));
        assertFalse(priority.containsKey("anyOf"));
    }

    @Test
    void modoFinalRechazaRespuestaQueAunDiceNoFinalizado() {
        when(geminiClient.generar(anyString(), anyString(), any()))
                .thenReturn(response(false, "null", "null", "[\"dolor\"]"));
        clearInvocations(geminiClient);

        assertThrows(ProveedorIaException.class,
                () -> client.consultar("sistema", "conversacion", true));
        verify(geminiClient, times(1)).generar(anyString(), anyString(), any());
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
