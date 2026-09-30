package com.pretriage.backend.services.voz;

import com.pretriage.backend.controllers.dtos.ResumenEntrevistaVoz;
import com.pretriage.backend.controllers.dtos.TurnoVoz;
import com.pretriage.backend.model.chat.AutorMensaje;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorCierreEntrevistaVozTest {
    @Test
    void rechazaResumenNuloOVacioAunqueYaExistaUnaRespuesta() {
        var turnos = List.of(paciente("Me duele la cabeza"));
        assertTrue(ValidadorCierreEntrevistaVoz.validar(turnos, null).isPresent());
        var vacio = new ObjectMapper().readValue("{}", ResumenEntrevistaVoz.class);
        assertTrue(ValidadorCierreEntrevistaVoz.validar(turnos, vacio).isPresent());
    }

    @Test
    void rechazaListasNulasOConItemsInvalidosYTextoEnBlanco() {
        var mapper = new ObjectMapper();
        for (String campo : List.of("sintomas", "signosAlarma", "antecedentesRelevantes", "medicamentos", "alergias")) {
            var json = mapper.valueToTree(resumen());
            ((tools.jackson.databind.node.ObjectNode) json).putNull(campo);
            assertTrue(ValidadorCierreEntrevistaVoz.validar(completa(), mapper.treeToValue(json, ResumenEntrevistaVoz.class)).isPresent(), campo);
            ((tools.jackson.databind.node.ObjectNode) json).putArray(campo).add(" ");
            assertTrue(ValidadorCierreEntrevistaVoz.validar(completa(), mapper.treeToValue(json, ResumenEntrevistaVoz.class)).isPresent(), campo);
        }
        for (String campo : List.of("motivoConsulta", "inicio", "evolucion", "posibilidadEmbarazo", "observaciones")) {
            var json = (tools.jackson.databind.node.ObjectNode) mapper.valueToTree(resumen());
            json.put(campo, " ");
            assertTrue(ValidadorCierreEntrevistaVoz.validar(completa(), mapper.treeToValue(json, ResumenEntrevistaVoz.class)).isPresent(), campo);
        }
    }

    @Test
    void validaRangoDeDolorSinConfundirAusenteConCero() {
        var mapper = new ObjectMapper();
        for (int intensidad : List.of(-1, 11)) {
            var json = (tools.jackson.databind.node.ObjectNode) mapper.valueToTree(resumen());
            json.put("intensidadDolor", intensidad);
            assertTrue(ValidadorCierreEntrevistaVoz.validar(completa(), mapper.treeToValue(json, ResumenEntrevistaVoz.class)).isPresent());
        }
        for (Integer intensidad : Arrays.asList(null, 0, 10)) {
            var json = (tools.jackson.databind.node.ObjectNode) mapper.valueToTree(resumen());
            json.put("intensidadDolor", intensidad);
            assertTrue(ValidadorCierreEntrevistaVoz.validar(completa(), mapper.treeToValue(json, ResumenEntrevistaVoz.class)).isEmpty());
        }
    }

    @Test
    void exigeAlMenosUnaRespuestaRealInclusoSiElResumenTieneAlarma() {
        assertTrue(ValidadorCierreEntrevistaVoz.validar(List.of(bot("Tiene dolor de pecho?"), paciente(" ")), conAlarmas("dolor de pecho intenso")).isPresent());
        assertTrue(ValidadorCierreEntrevistaVoz.validar(null, resumen()).isPresent());
    }

    @Test
    void resumenCompletoNoPuedeInventarSuficienciaDeUnaRespuesta() {
        assertTrue(ValidadorCierreEntrevistaVoz.validar(List.of(paciente("Me duele la cabeza")), resumen()).isPresent());
    }

    @Test
    void aceptaTresRespuestasConInicioGravedadAlarmasYContextoIncluyendoNegaciones() {
        assertTrue(ValidadorCierreEntrevistaVoz.validar(completa(), resumen()).isEmpty());
    }

    @Test
    void aceptaRespuestasBrevesContextualizadasPorPreguntasSinContarPreguntasComoRespuestas() {
        var turnos = List.of(paciente("Tengo dolor de cabeza"), bot("Desde cuando?"), paciente("Desde ayer"),
                bot("Del 0 al 10, intensidad del dolor?"), paciente("5"),
                bot("Tenes dificultad para respirar o dolor de pecho?"), paciente("No"),
                bot("Antecedentes, medicamentos o alergias?"), paciente("Ninguno"));
        assertTrue(ValidadorCierreEntrevistaVoz.validar(turnos, resumen()).isEmpty());
        assertTrue(ValidadorCierreEntrevistaVoz.validar(List.of(paciente("Tengo dolor de cabeza"),
                bot("Desde ayer? Dolor fuerte? Tenes dificultad para respirar? Antecedentes?"), paciente("No se"), paciente("No se")), resumen()).isPresent());
    }

    @Test
    void aprovechaHistorialPrevioPorTextoSinExigirTresTurnosNuevosDeVoz() {
        var historial = new ArrayList<>(completa().subList(0, 2));
        historial.add(bot("Tomas medicacion o tenes antecedentes?"));
        historial.add(paciente("No tengo antecedentes ni alergias y no tomo medicamentos"));
        assertTrue(ValidadorCierreEntrevistaVoz.validar(historial, resumen()).isEmpty());
    }

    @Test
    void rechazaCierreNoUrgenteSinInicioNiContextoAunqueElResumenLosInvente() {
        var turnos = List.of(paciente("Dolor de cabeza 5/10"), paciente("No se cuando empezo"),
                paciente("No tengo dificultad para respirar ni dolor de pecho"));
        String instruccion = ValidadorCierreEntrevistaVoz.validar(turnos, resumen()).orElseThrow();
        assertTrue(instruccion.contains("inicio"));
        assertTrue(instruccion.contains("antecedentes"));
        assertFalse(instruccion.contains("Dolor de cabeza"));
    }

    @Test
    void permiteAlarmaAfirmadaDelPacienteSinEsperarMinimoDeTurnos() {
        assertTrue(ValidadorCierreEntrevistaVoz.validar(List.of(paciente("Tengo dolor de pecho y dificultad para respirar desde hace diez minutos")), resumen()).isEmpty());
        assertTrue(ValidadorCierreEntrevistaVoz.validar(List.of(paciente("No tengo alergias, pero tengo dificultad para respirar")), resumen()).isEmpty());
    }

    @Test
    void permiteAlarmaEspecificaReconocidaPorGeminiFueraDelLexicoLocal() {
        assertTrue(ValidadorCierreEntrevistaVoz.validar(List.of(paciente("No puedo mover el brazo y la boca esta torcida")), conAlarmas("debilidad unilateral de inicio subito")).isEmpty());
        assertTrue(ValidadorCierreEntrevistaVoz.validar(List.of(paciente("Estoy desorientado")), conAlarmas("confusion")).isEmpty());
    }

    @Test
    void intensidadLiteralNoConfundeNumerosDeOtrasPreguntasConUnaCorreccionDelDolor() {
        var turnos = List.of(paciente("Dolor 2 sobre 10"), bot("Hace cuantas horas empezo?"), paciente("8"));
        assertEquals(2, ValidadorCierreEntrevistaVoz.ultimaIntensidadLiteral(turnos));
        var corregidos = new ArrayList<>(turnos);
        corregidos.add(bot("Del 0 al 10, cuanto te duele ahora?"));
        corregidos.add(paciente("8"));
        assertEquals(8, ValidadorCierreEntrevistaVoz.ultimaIntensidadLiteral(corregidos));
    }

    @Test
    void negacionesYMarcadoresNoCreanUnaAlarma() {
        for (String signo : List.of("no informado", "sin signos de alarma", "signos de alarma", "ninguno", "signos negados", "pendiente de evaluar")) {
            assertTrue(ValidadorCierreEntrevistaVoz.validar(List.of(paciente("No tengo dolor de pecho ni dificultad para respirar")), conAlarmas(signo)).isPresent(), signo);
        }
        assertEquals(List.of("Confusion"), ValidadorCierreEntrevistaVoz.signosAlarmaSignificativos(
                conAlarmas("ninguno", "no informado", "Confusion")));
    }

    @Test
    void doceRespuestasPermitenResumenEscasoPeroNuncaMalformado() {
        var turnos = IntStream.range(0, 12).mapToObj(i -> paciente("No se")).toList();
        var escaso = new ResumenEntrevistaVoz("no informado", List.of(), "no informado", "no informado", null,
                List.of(), List.of(), List.of(), List.of(), "no informado", "no informado");
        assertTrue(ValidadorCierreEntrevistaVoz.validar(turnos, escaso).isEmpty());
        assertTrue(ValidadorCierreEntrevistaVoz.validar(turnos, null).isPresent());
        assertTrue(ValidadorCierreEntrevistaVoz.validar(turnos.subList(0, 11), escaso).isPresent());
    }

    private static List<TurnoVoz> completa() {
        return List.of(paciente("Dolor de cabeza desde ayer, intensidad 5/10"),
                paciente("No tengo dificultad para respirar ni dolor de pecho"),
                paciente("No tengo antecedentes ni alergias y no tomo medicamentos"));
    }
    private static TurnoVoz paciente(String contenido) { return new TurnoVoz(AutorMensaje.PACIENTE, contenido); }
    private static TurnoVoz bot(String contenido) { return new TurnoVoz(AutorMensaje.BOT, contenido); }
    private static ResumenEntrevistaVoz resumen() { return conAlarmas(); }
    private static ResumenEntrevistaVoz conAlarmas(String... signos) {
        return new ResumenEntrevistaVoz("Dolor de cabeza", List.of("dolor de cabeza"), "desde ayer", "sin cambios", 5,
                List.of(signos), List.of(), List.of(), List.of(), "no informado", "Niega antecedentes, medicamentos y alergias");
    }
}
