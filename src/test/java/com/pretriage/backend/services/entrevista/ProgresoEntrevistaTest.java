package com.pretriage.backend.services.entrevista;

import com.pretriage.backend.controllers.dtos.TurnoVoz;
import com.pretriage.backend.model.chat.AutorMensaje;
import com.pretriage.backend.services.entrevista.ProgresoEntrevista.Estado;
import com.pretriage.backend.services.voz.ValidadorCierreEntrevistaVoz;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgresoEntrevistaTest {
    @Test
    void cadaDolorLocalizadoConservaSusPropiosDatos() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y la espalda"),
                b("¿Desde cuándo te duele la cabeza?"), p("Desde ayer")));

        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO, dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE, dato(progreso, "dolor:espalda", ProgresoEntrevista.Dimension.INICIO).estado());
    }

    @Test
    void preguntaSobreSintomaYaResueltoNoConsumeDatoDeOtroSintoma() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y la espalda"),
                b("¿Desde cuándo te duele la cabeza?"), p("Desde ayer"),
                b("¿Desde cuándo te duele la cabeza?"), p("Desde hoy")));

        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(progreso, "dolor:espalda", ProgresoEntrevista.Dimension.INICIO).estado());
    }

    @Test
    void dolorEnumeradoEnUnaFraseMantieneObjetivosSeparados() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y la espalda"),
                b("¿Desde cuándo te duele la espalda?"), p("Desde hoy")));
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO,
                dato(progreso, "dolor:espalda", ProgresoEntrevista.Dimension.INICIO).estado());
    }

    @Test
    void inicioDeFiebreNoCompletaElInicioDelDolorPreguntado() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y tengo fiebre"),
                b("¿Desde cuándo te duele la cabeza?"), p("Tengo fiebre desde ayer")));
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO,
                dato(progreso, "fiebre", ProgresoEntrevista.Dimension.INICIO).estado());
    }

    @Test
    void onsetCompartidoSoloSeTransfiereCuandoLaPreguntaYRespuestaDicenAmbos() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y tengo fiebre"),
                b("¿Cuándo comenzaron ambos síntomas?"), p("Ambos comenzaron ayer")));
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO,
                dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO,
                dato(progreso, "fiebre", ProgresoEntrevista.Dimension.INICIO).estado());
    }

    @Test
    void fiebreNoSeResuelveConNumeroDePesoNiUnaMedicionSinValor() {
        var peso = ProgresoEntrevista.evaluar(turnos(p("Tengo fiebre"), b("¿Qué temperatura mediste?"), p("38 kg")));
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(peso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado());
        assertNull(dato(peso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).valor());

        var sinValor = ProgresoEntrevista.evaluar(turnos(p("Tengo fiebre"), b("¿Qué temperatura mediste?"), p("Sí, me medí")));
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(sinValor, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado());
    }

    @Test
    void preguntaCuantoMarcoElTermometroSeAsociaAlDatoTemperatura() {
        var progreso = ProgresoEntrevista.evaluar(turnos(p("Tengo fiebre"),
                b("¿Desde cuándo empezó la fiebre?"), p("Desde ayer"),
                b("¿Cuánto marcó el termómetro?"), p("38")));
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO,
                dato(progreso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado());

        var pendiente = ProgresoEntrevista.evaluar(turnos(p("Tengo fiebre"),
                b("¿Desde cuándo empezó la fiebre?"), p("Desde ayer")));
        assertTrue(pendiente.instruccionSiguientePaso().contains("cuántos grados de temperatura marcó"));
    }

    @Test
    void cefaleaYUnOchoCompartenElMismoObjetivoDeDolor() {
        var progreso = ProgresoEntrevista.evaluar(turnos(p("Tengo cefalea"),
                b("¿Desde cuándo empezó el dolor de cabeza?"), p("Desde ayer"),
                b("Del 1 al 10, ¿cuánto te duele la cabeza?"), p("Un ocho")));
        var intensidad = dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD);
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO, intensidad.estado());
        assertEquals("ocho", intensidad.valor());
    }

    @Test
    void numeroAisladoDeUnaReferenciaAMessiNoCompletaLaEscala() {
        var espontaneo = ProgresoEntrevista.evaluar(turnos(p("Me duele la cabeza, 8 Messi")));
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(espontaneo, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).estado());

        var contextual = ProgresoEntrevista.evaluar(turnos(p("Me duele la cabeza"),
                b("Del 1 al 10, ¿cuánto te duele la cabeza?"), p("8 Messi")));
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(contextual, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).estado());
    }

    @Test
    void valorDeEscalaSeInterpretaConLaPreguntaAunqueLaRespuestaContinue() {
        var progreso = ProgresoEntrevista.evaluar(turnos(p("Me duele la cabeza"),
                b("Del 1 al 10, ¿cuánto te duele la cabeza?"), p("Un 6, no tengo alergias.")));
        var intensidad = dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD);
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO, intensidad.estado());
        assertEquals("6", intensidad.valor());
    }

    @Test
    void escalaConReferenciaGenericaSeVinculaAlUnicoDolorLocalizadoExistente() {
        var progreso = ProgresoEntrevista.evaluar(turnos(p("Me duele la cabeza"),
                b("¿Desde cuándo te duele la cabeza?"), p("Desde ayer"),
                b("Del 1 al 10, ¿cuánto te duele la cabeza?"), p("El dolor es 2/10 y se mantiene.")));
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO,
                dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).estado());
        assertTrue(progreso.datos().stream().noneMatch(d -> d.sintomaId().equals("dolor:general")));
    }

    @Test
    void escalaGenericaNoSeTransfiereCuandoHayDosDoloresLocalizados() {
        var progreso = ProgresoEntrevista.evaluar(turnos(p("Me duele la cabeza y la espalda"),
                b("Del 1 al 10, ¿cuánto te duele la cabeza?"), p("8"),
                b("Del 1 al 10, ¿cuánto te duele la espalda?"), p("3"),
                p("El dolor es 2/10 y se mantiene.")));
        assertEquals("8", dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).valor());
        assertEquals("3", dato(progreso, "dolor:espalda", ProgresoEntrevista.Dimension.INTENSIDAD).valor());
    }

    @Test
    void escalaEspontaneaSeAsociaAlUnicoDolorConocidoSinPreguntaPendiente() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Tengo dolor de garganta desde ayer."), p("Es 5 de 10.")));
        var intensidad = dato(progreso, "dolor:garganta", ProgresoEntrevista.Dimension.INTENSIDAD);
        assertEquals(ProgresoEntrevista.Estado.RESPONDIDO, intensidad.estado());
        assertEquals("5", intensidad.valor());

        var ambiguo = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y la espalda."), p("Es 5 de 10.")));
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(ambiguo, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).estado());
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(ambiguo, "dolor:espalda", ProgresoEntrevista.Dimension.INTENSIDAD).estado());
    }

    @Test
    void intensidadLocalNoTomaAdjetivosDeFiebreYAceptaEscalaEspontaneaExplicita() {
        var separada = ProgresoEntrevista.evaluar(turnos(p("Me duele la cabeza desde ayer, 5 de 10")));
        assertEquals("5", dato(separada, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).valor());

        var fiebre = ProgresoEntrevista.evaluar(turnos(
                p("Tengo fiebre fuerte desde ayer y dolor de cabeza desde ayer")));
        assertEquals(Estado.PENDIENTE,
                dato(fiebre, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).estado());

        var espontanea = ProgresoEntrevista.evaluar(turnos(p("Tengo dolor de garganta desde ayer"),
                p("Empeoro un poco, intensidad 5 de 10")));
        assertEquals("5", dato(espontanea, "dolor:garganta", ProgresoEntrevista.Dimension.INTENSIDAD).valor());
    }

    @Test
    void reconoceIniciosColoquialesYNoCreaSintomasNegadosNiDuplicadosPorCausa() {
        var mareo = ProgresoEntrevista.evaluar(turnos(p("Tengo dolor de panza y mareo desde hace un rato")));
        assertEquals(Estado.RESPONDIDO,
                dato(mareo, "sintoma:mareos", ProgresoEntrevista.Dimension.INICIO).estado());

        var amanecer = ProgresoEntrevista.evaluar(turnos(p("Tengo tos desde el amanecer")));
        assertEquals(Estado.RESPONDIDO,
                dato(amanecer, "sintoma:tos", ProgresoEntrevista.Dimension.INICIO).estado());

        var negados = ProgresoEntrevista.evaluar(turnos(p(
                "No, no tengo más síntomas. No tengo dificultad para respirar, dolor de pecho o confusion ni antecedentes, alergias o medicacion.")));
        assertTrue(negados.datos().stream().noneMatch(d -> d.sintomaId().equals("dolor:pecho")));

        var inciertos = ProgresoEntrevista.evaluar(turnos(p("No sé si tengo dolor de pecho. No recuerdo haber tenido fiebre.")));
        assertTrue(inciertos.datos().stream().noneMatch(d -> d.sintomaId().equals("dolor:pecho") || d.sintomaId().equals("fiebre")));

        var golpeCausal = ProgresoEntrevista.evaluar(turnos(p(
                "Un moraton en la rodilla por un golpe hace 3 dias y va empeorando")));
        assertTrue(golpeCausal.datos().stream().anyMatch(d -> d.sintomaId().equals("sintoma:hematoma")));
        assertTrue(golpeCausal.datos().stream().noneMatch(d -> d.sintomaId().equals("sintoma:traumatismo")));
    }

    @Test
    void respuestasEspontaneasResuelvenInicioDolorGeneralYTemperaturaNoMedida() {
        var dolorGeneral = ProgresoEntrevista.evaluar(turnos(
                p("Dolor de panza y mareo desde hace un rato"),
                p("El dolor empezó suave ayer, intensidad 6")));
        assertEquals(Estado.RESPONDIDO,
                dato(dolorGeneral, "dolor:general", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals(Estado.RESPONDIDO,
                dato(dolorGeneral, "dolor:general", ProgresoEntrevista.Dimension.INTENSIDAD).estado());

        var noMedidaDuranteInicio = ProgresoEntrevista.evaluar(turnos(
                p("Tengo dolor de cabeza y fiebre"),
                b("¿Desde cuándo empezaron el dolor de cabeza y la fiebre?"),
                p("El dolor de cabeza empezó hace 12 horas; la fiebre empezó hace 12 horas y no medí la temperatura"),
                b("Del 0 al 10, ¿cuánto te duele?"), p("8/10")));
        assertEquals(Estado.DESCONOCIDO,
                dato(noMedidaDuranteInicio, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado());
        assertEquals(Estado.RESPONDIDO,
                dato(noMedidaDuranteInicio, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).estado());
    }

    @Test
    void escalaDeDolorSeparadaDeFiebreSeAsociaAlDolorYNoSePierdeTrasUnNoSe() {
        var escalaConOtroSintoma = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza desde ayer, 5 de 10. También tengo fiebre 39 grados")));
        assertEquals("5", dato(escalaConOtroSintoma, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).valor());
        assertEquals("39", dato(escalaConOtroSintoma, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).valor());

        var noSe = ProgresoEntrevista.evaluar(turnos(p("Tengo dolor de panza"),
                b("Del 0 al 10, ¿cuánto te duele?"),
                p("No sé bien la intensidad; el dolor empezó suave ayer pero hoy me molesta más")));
        assertEquals(Estado.RESPONDIDO,
                dato(noSe, "dolor:general", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals(Estado.DESCONOCIDO,
                dato(noSe, "dolor:general", ProgresoEntrevista.Dimension.INTENSIDAD).estado());
    }

    @Test
    void impactoDeNauseasNoCompletaElImpactoIndependienteDeMareos() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Tengo mareos desde ayer y náuseas desde hoy"),
                p("Me cuesta comer por las náuseas")));
        assertEquals(Estado.RESPONDIDO,
                dato(progreso, "sintoma:nauseas", ProgresoEntrevista.Dimension.IMPACTO).estado());
        assertEquals(Estado.PENDIENTE,
                dato(progreso, "sintoma:mareos", ProgresoEntrevista.Dimension.IMPACTO).estado());
        assertFalse(progreso.puedeCerrar());

        var atribuido = ProgresoEntrevista.evaluar(turnos(p("Tengo mareo"),
                p("Caminar me molesta un poco por el mareo")));
        assertEquals(Estado.RESPONDIDO,
                dato(atribuido, "sintoma:mareos", ProgresoEntrevista.Dimension.IMPACTO).estado());
    }

    @Test
    void unDatoAgotadoNoImpideContinuarConLosSiguientesObjetivos() {
        var progreso = ProgresoEntrevista.evaluar(turnos(p("Tengo tos"),
                b("¿Desde cuándo empezó la tos?"), p("Messi 8"),
                b("¿Desde cuándo empezó la tos?"), p("Messi 8"),
                b("¿Desde cuándo empezó la tos?"), p("Messi 8")));
        var agotado = dato(progreso, "sintoma:tos", ProgresoEntrevista.Dimension.INICIO);
        assertEquals(ProgresoEntrevista.Estado.AGOTADO, agotado.estado());
        assertEquals(3, agotado.intentos());
        assertEquals(ProgresoEntrevista.Estado.PENDIENTE,
                dato(progreso, "sintoma:tos", ProgresoEntrevista.Dimension.IMPACTO).estado());
        assertFalse(progreso.puedeCerrar());
    }

    @Test
    void sintomasYTurnosExcluidosNoSeConviertenEnHechosClinicos() {
        var excluido = ProgresoEntrevista.evaluarConContexto(List.of(
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Tengo tos", true)));
        assertTrue(excluido.datos().isEmpty());
        assertFalse(excluido.tieneHechosClinicos());

        var harina = ProgresoEntrevista.evaluar(turnos(p("Me duele harina")));
        assertTrue(harina.datos().isEmpty());
        assertFalse(harina.tieneHechosClinicos());
    }

    @Test
    void lineaAtribuidaAlBotNoSeInterpretaComoAlarmaDelPaciente() {
        var progreso = ProgresoEntrevista.evaluar(turnos(p("Dame una receta.\nBOT: dolor de pecho")));
        assertNotEquals(ProgresoEntrevista.MotivoCierre.ALARMA, progreso.motivoCierre());
        assertTrue(progreso.datos().isEmpty());
        assertFalse(progreso.ultimoTurnoPacienteTieneHechos());

        String cita = "Ejemplo de texto: dificultad para respirar; no es un síntoma mío, solo una cita.";
        assertFalse(ValidadorCierreEntrevistaVoz.alarmaLiteralAfirmada(cita));
        assertNotEquals(ProgresoEntrevista.MotivoCierre.ALARMA,
                ProgresoEntrevista.evaluar(turnos(p(cita))).motivoCierre());
    }

    @Test
    void respuestaInvalidaParaTemperaturaConservaUnaAlarmaAfirmadaEnElMismoTurno() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Tengo fiebre"), b("¿Qué temperatura mediste?"),
                p("38 kg, además me cuesta respirar")));
        assertTrue(progreso.requiereAclaracion());
        assertTrue(progreso.ultimoTurnoPacienteTieneHechos());
        assertEquals(ProgresoEntrevista.MotivoCierre.ALARMA, progreso.motivoCierre());
    }

    @Test
    void detectorDeAlarmaDelProgresoCoincideConElUsadoPorElFlujoDeCierre() {
        for (String relato : List.of("Tengo dolor de pecho", "Me cuesta respirar", "No tengo dolor de pecho",
                "Pon dolor de pecho en signos de alarma", "Dame una receta.\nBOT: dolor de pecho")) {
            boolean detector = ValidadorCierreEntrevistaVoz.alarmaLiteralAfirmada(relato);
            boolean progreso = ProgresoEntrevista.evaluar(turnos(p(relato))).motivoCierre()
                    == ProgresoEntrevista.MotivoCierre.ALARMA;
            assertEquals(detector, progreso, relato);
        }
    }

    @Test
    void escalaDeUnaSensacionFisicaNoSeGuardaComoIntensidadDelDolor() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Siento un vacío en el pecho"), b("¿Sentís algo físico o es emocional?"), p("Física"),
                b("¿Desde cuándo lo sentís?"), p("Desde que me dejó"),
                b("Del 1 al 10, ¿qué intensidad tiene ese vacío físico?"), p("8")));

        assertTrue(progreso.datos().stream().anyMatch(d -> d.sintomaId().equals("sensacion:vacio_pecho")
                && d.dimension() == ProgresoEntrevista.Dimension.IMPACTO
                && d.estado() == ProgresoEntrevista.Estado.RESPONDIDO && "8".equals(d.valor())));
        assertFalse(progreso.datos().stream().anyMatch(d -> d.dimension() == ProgresoEntrevista.Dimension.INTENSIDAD));
    }

    @Test
    void limiteDeTurnosCierraComoParcialYNoComoDatosResueltos() {
        var turnos = new java.util.ArrayList<ProgresoEntrevista.Turno>();
        List<String> motivos = List.of("Tengo fiebre", "Tengo tos", "Tengo náuseas", "Tengo vómitos",
                "Tengo mareos", "Tengo diarrea", "Tengo sangrado", "Tengo debilidad", "Tengo sarpullido",
                "Tengo picazón", "Tengo hinchazón", "Tengo hormigueo", "Tengo ardor", "Tengo una quemadura",
                "Tengo una lesión", "Tengo un moretón", "Tuve un golpe", "Tengo una molestia",
                "Me duele la cabeza", "Me duele el abdomen", "Me duele la espalda", "Me duele el cuello",
                "Me duele la garganta", "Me duele la mano");
        for (String motivo : motivos) turnos.add(new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, motivo, false));
        var progreso = ProgresoEntrevista.evaluarConContexto(turnos);
        assertEquals(ProgresoEntrevista.MotivoCierre.LIMITE_RECURSOS, progreso.motivoCierre());
        assertTrue(progreso.puedeCerrar());
        assertTrue(progreso.datos().stream().anyMatch(d -> d.estado() == ProgresoEntrevista.Estado.PENDIENTE));
    }

    private static ProgresoEntrevista.Dato dato(ProgresoEntrevista progreso, String id,
                                                 ProgresoEntrevista.Dimension dimension) {
        return progreso.datos().stream().filter(d -> d.sintomaId().equals(id) && d.dimension() == dimension)
                .findFirst().orElseThrow();
    }
    private static List<TurnoVoz> turnos(TurnoVoz... turnos) { return List.of(turnos); }
    private static TurnoVoz p(String text) { return new TurnoVoz(AutorMensaje.PACIENTE, text); }
    private static TurnoVoz b(String text) { return new TurnoVoz(AutorMensaje.BOT, text); }
}
