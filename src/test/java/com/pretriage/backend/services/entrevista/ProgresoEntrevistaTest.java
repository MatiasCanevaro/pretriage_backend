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
    void preguntaNaturalPorHorasODiasVinculaLaDuracionAlDolorYPermiteCerrarTrasLaInvitacion() {
        var preguntaInicio = ProgresoEntrevista.evaluar(turnos(p("Me duele la cabeza")));
        assertTrue(preguntaInicio.validarPreguntaPropuesta(
                "¿Podrías precisarme aproximadamente cuántas horas o días hace que empezó ese dolor?").isEmpty());

        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza"),
                b("¿Podrías precisarme aproximadamente cuántas horas o días hace que empezó ese dolor?"),
                p("hace 2 años y 1 día"),
                b("¿Este dolor te impide realizar tus actividades habituales?"), p("Sí."),
                b("¿Hay algo más que quieras contar?"), p("No")));

        var inicio = dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO);
        assertEquals(Estado.RESPONDIDO, inicio.estado());
        assertEquals("hace 2 anos y 1 dia", inicio.valor());
        var impacto = dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.IMPACTO);
        assertEquals(Estado.RESPONDIDO, impacto.estado());
        assertEquals("si.", impacto.valor());
        assertEquals(ProgresoEntrevista.MotivoCierre.DATOS_RESUELTOS, progreso.motivoCierre());
    }

    @Test
    void duracionRelativaSeConservaLiteralYSinTransferirseAOtroSintoma() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y la espalda"),
                b("¿Desde cuándo empezó el dolor de cabeza?"), p("Desde que me dejó")));

        assertEquals("desde que me dejo", dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).valor());
        assertEquals(Estado.PENDIENTE,
                dato(progreso, "dolor:espalda", ProgresoEntrevista.Dimension.INICIO).estado());
    }

    @Test
    void referenciaRelativaDesdeQueSeFueResuelveDolorGenericoSoloBajoPreguntaDeInicio() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Es un dolor reprofundo y tengo fiebre"),
                b("¿Podrías decirme cuántas horas o días hace que empezó ese dolor?"),
                p("Desde que se fue")));

        assertEquals("desde que se fue",
                dato(progreso, "dolor:general", ProgresoEntrevista.Dimension.INICIO).valor());
        assertEquals(Estado.PENDIENTE,
                dato(progreso, "fiebre", ProgresoEntrevista.Dimension.INICIO).estado());

        var espontaneo = ProgresoEntrevista.evaluar(turnos(p("Tengo dolor de cabeza desde que se fue")));
        assertEquals(Estado.PENDIENTE,
                dato(espontaneo, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).estado());
    }

    @Test
    void desdeQueMeDejoConservaLaResolucionEspontaneaDelSintomaNombradoSinTransferencia() {
        var explicito = ProgresoEntrevista.evaluar(turnos(p("Me duele la cabeza desde que me dejó")));
        var inicioExplicito = dato(explicito, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO);
        assertEquals(Estado.RESPONDIDO, inicioExplicito.estado());
        assertEquals("desde que me dejo", inicioExplicito.valor());

        var separados = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza; tengo fiebre desde que me dejó")));
        assertEquals(Estado.PENDIENTE,
                dato(separados, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals("desde que me dejo",
                dato(separados, "fiebre", ProgresoEntrevista.Dimension.INICIO).valor());
    }

    @Test
    void incertidumbreDeFechaNoBorraInicioRelativoAfirmadoEnLaRespuestaDeInicio() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Tengo tos"),
                b("¿Desde cuándo empezó la tos?"),
                p("No sé la fecha exacta; la tos empezó desde que mi pareja se mudó a otra ciudad.")));

        var inicio = dato(progreso, "sintoma:tos", ProgresoEntrevista.Dimension.INICIO);
        assertEquals(Estado.RESPONDIDO, inicio.estado());
        assertEquals("desde que mi pareja se mudo a otra ciudad", inicio.valor());
    }

    @Test
    void incertidumbreONegacionDelInicioNoSeConviertenEnTiempoAfirmado() {
        var incierto = ProgresoEntrevista.evaluar(turnos(
                p("Tengo tos"), b("¿Desde cuándo empezó la tos?"),
                p("No sé si la tos empezó desde que mi pareja se mudó a otra ciudad.")));
        var inicioIncierto = dato(incierto, "sintoma:tos", ProgresoEntrevista.Dimension.INICIO);
        assertNotEquals(Estado.RESPONDIDO, inicioIncierto.estado());

        var negado = ProgresoEntrevista.evaluar(turnos(
                p("Tengo tos"), b("¿Desde cuándo empezó la tos?"),
                p("La tos no empezó desde que mi pareja se mudó a otra ciudad.")));
        var inicioNegado = dato(negado, "sintoma:tos", ProgresoEntrevista.Dimension.INICIO);
        assertNotEquals(Estado.RESPONDIDO, inicioNegado.estado());
    }

    @Test
    void fraseExactaConNoLoSeYDesdeQueEllaMeDejoConservaElInicioAfirmado() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Tengo una molestia"), b("¿Desde cuándo empezó esta molestia?"),
                p("No lo sé, lo siento desde que ella me dejó.")));

        var inicio = dato(progreso, "sintoma:molestia", ProgresoEntrevista.Dimension.INICIO);
        assertEquals(Estado.RESPONDIDO, inicio.estado());
        assertEquals("desde que ella me dejo", inicio.valor());
    }

    @Test
    void incertidumbreYNegacionSobreEllaNoAfirmanElInicio() {
        var sentimientoHipotetico = ProgresoEntrevista.evaluar(turnos(
                p("Tengo una molestia"), b("¿Desde cuándo empezó esta molestia?"),
                p("No sé si lo siento desde que ella me dejó.")));
        assertNotEquals(Estado.RESPONDIDO,
                dato(sentimientoHipotetico, "sintoma:molestia", ProgresoEntrevista.Dimension.INICIO).estado());

        var incierto = ProgresoEntrevista.evaluar(turnos(
                p("Tengo una molestia"), b("¿Desde cuándo empezó esta molestia?"),
                p("No sé si empezó desde que ella me dejó.")));
        assertNotEquals(Estado.RESPONDIDO,
                dato(incierto, "sintoma:molestia", ProgresoEntrevista.Dimension.INICIO).estado());

        var negado = ProgresoEntrevista.evaluar(turnos(
                p("Tengo una molestia"), b("¿Desde cuándo empezó esta molestia?"),
                p("La molestia no empezó desde que ella me dejó.")));
        assertNotEquals(Estado.RESPONDIDO,
                dato(negado, "sintoma:molestia", ProgresoEntrevista.Dimension.INICIO).estado());
    }

    @Test
    void respuestaExcluidaALaPreguntaNaturalNoResuelveNiTransfiereElInicio() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Me duele la cabeza y tengo fiebre", false),
                bt("¿Podrías decirme cuántas horas o días hace que empezó el dolor de cabeza?"),
                pt("hace 2 años y 1 día", true)));

        assertEquals(Estado.PENDIENTE,
                dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals(Estado.PENDIENTE,
                dato(progreso, "fiebre", ProgresoEntrevista.Dimension.INICIO).estado());
        assertEquals(1, dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO).intentos());
    }

    @Test
    void aclaracionAceptaParafrasisAbiertaDelMismoInicioPeroNoIntroduceOtroSintoma() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza"), b("¿Desde cuándo empezó el dolor de cabeza?"), p("Quizás")));
        assertEquals(ProgresoEntrevista.TipoObjetivo.ACLARACION, progreso.objetivoDelTurno().tipo());
        assertTrue(progreso.validarPreguntaPropuesta(
                "¿Podrías contarme con tus palabras cuánto tiempo lleva ese dolor de cabeza?").isEmpty());
        assertEquals("SINTOMA_NO_COINCIDE", progreso.validarPreguntaPropuesta(
                "¿Podrías contarme con tus palabras cuánto tiempo lleva ese dolor de espalda?").orElseThrow());
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
    void objetivoYValidadorDePreguntaApuntanAlMismoDatoPendiente() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y tengo fiebre"),
                b("¿Cuándo comenzaron ambos síntomas?"), p("Ambos comenzaron ayer"),
                p("Tengo fiebre de 39 grados")));
        assertEquals(new ProgresoEntrevista.ObjetivoTurno(ProgresoEntrevista.TipoObjetivo.DATO,
                "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD), progreso.objetivoDelTurno());
        assertTrue(progreso.validarPreguntaPropuesta("Del 1 al 10, ¿cuánto te duele la cabeza?").isEmpty());
        assertTrue(progreso.validarPreguntaPropuesta("¿Qué tan fuerte sentís el dolor de cabeza?").isEmpty(),
                "La paráfrasis reconocible del mismo síntoma se delega al verifier objetivo");
        assertTrue(progreso.validarPreguntaPropuesta("¿Te cuesta caminar por el dolor de cabeza?").isEmpty(),
                "El impacto funcional del mismo dolor puede responder la dimensión de intensidad");
        assertEquals("DIMENSION_NO_COINCIDE",
                progreso.validarPreguntaPropuesta("¿Cuánto marcó el termómetro?").orElseThrow());
        assertEquals("SINTOMA_NO_COINCIDE",
                progreso.validarPreguntaPropuesta("¿Cuánto te duele la espalda?").orElseThrow());
        assertEquals("PREGUNTA_PREMATURA",
                progreso.validarPreguntaPropuesta("¿Hay algo más que quieras agregar?").orElseThrow());
        assertTrue(progreso.validarPreguntaPropuesta("¿Podrías explicarme mejor lo que sientes?").isEmpty(),
                "El phrasing no reconocido se delega al verifier semántico");
    }

    @Test
    void preguntaNaturalDeFuerzaDeDolorRecibeLaEscalaSinTransferirAOtroDolor() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza"), b("¿Qué tan fuerte sentís el dolor de cabeza?"), p("6/10")));
        assertEquals("6", dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).valor());

        var separados = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza y la espalda"), b("¿Qué tan fuerte sentís el dolor de cabeza?"), p("6/10")));
        assertEquals("6", dato(separados, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).valor());
        assertEquals(Estado.PENDIENTE,
                dato(separados, "dolor:espalda", ProgresoEntrevista.Dimension.INTENSIDAD).estado());
    }

    @Test
    void preguntaDeAlarmaRespiratoriaNoSeAsociaAlImpactoDelDolor() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza"), b("¿Te cuesta respirar?"), p("No tengo otros síntomas")));
        assertEquals(new ProgresoEntrevista.ObjetivoTurno(ProgresoEntrevista.TipoObjetivo.DATO,
                "dolor:cabeza", ProgresoEntrevista.Dimension.INICIO), progreso.objetivoDelTurno());
        assertTrue(progreso.instruccionSiguientePaso().contains("desde cuándo empezó"));
    }

    @Test
    void objetivoInvitacionYObjetivoCierreNoAdelantanLaConversacion() {
        var invitacion = ProgresoEntrevista.evaluar(turnos(p("Dolor de cabeza desde ayer, intensidad 5/10")));
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION, invitacion.objetivoDelTurno().tipo());
        assertTrue(invitacion.validarPreguntaPropuesta("¿Hay algo más que quieras contar?").isEmpty());
        assertTrue(invitacion.validarPreguntaPropuesta(
                "¿Hay algo más que quieras contar además del dolor de cabeza?").isEmpty(),
                "La invitación puede referirse a un síntoma que el paciente ya mencionó");
        assertEquals("SINTOMAS_SUGERIDOS",
                invitacion.validarPreguntaPropuesta("¿Hay algo más, por ejemplo tos?").orElseThrow());
        assertEquals("CIERRE_PREMATURO",
                invitacion.validarPreguntaPropuesta("El caso ya fue registrado.").orElseThrow());

        var cerrado = ProgresoEntrevista.evaluar(turnos(p("Dolor de cabeza desde ayer, intensidad 5/10"),
                b("¿Hay algo más que quieras contar?"), p("No")));
        assertEquals(ProgresoEntrevista.TipoObjetivo.CERRAR, cerrado.objetivoDelTurno().tipo());
        assertEquals("PREGUNTA_TRAS_CIERRE",
                cerrado.validarPreguntaPropuesta("¿Desde cuándo empezó el dolor de cabeza?").orElseThrow());

        var aclaracion = ProgresoEntrevista.evaluar(turnos(p("No entiendo qué quieres decir")));
        assertEquals(ProgresoEntrevista.TipoObjetivo.ACLARACION, aclaracion.objetivoDelTurno().tipo());
        assertTrue(aclaracion.validarPreguntaPropuesta("¿Puedes explicar con tus palabras qué te ocurre?").isEmpty());
        assertTrue(aclaracion.validarPreguntaPropuesta("¿Cómo describirías la molestia?").isEmpty(),
                "Una aclaración abierta no introduce un síntoma solo por usar un sustantivo genérico");
        assertEquals("ACLARACION_FUERA_DE_OBJETIVO",
                aclaracion.validarPreguntaPropuesta("¿Tienes fiebre?").orElseThrow());
        assertEquals("ACLARACION_FUERA_DE_OBJETIVO",
                aclaracion.validarPreguntaPropuesta("¿Tienes tos?").orElseThrow());
    }

    @Test
    void aclaracionTrasPreguntaDeImpactoConservaLaDimensionEfectivaDelDolor() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Me duele la cabeza"),
                b("¿Te cuesta caminar por el dolor de cabeza?"),
                p("Quizás")));

        assertTrue(progreso.requiereAclaracion());
        assertEquals(new ProgresoEntrevista.ObjetivoTurno(ProgresoEntrevista.TipoObjetivo.ACLARACION,
                "dolor:cabeza", ProgresoEntrevista.Dimension.IMPACTO), progreso.objetivoDelTurno());
        assertTrue(progreso.validarPreguntaPropuesta(
                "¿Qué tanto te afecta caminar por el dolor de cabeza?").isEmpty());
        assertEquals("DIMENSION_NO_COINCIDE", progreso.validarPreguntaPropuesta(
                "¿Del 1 al 10, cuánto te duele la cabeza?").orElseThrow());
    }

    @Test
    void confirmacionDeMedicionEsSubpasoUnicoYNoGastaIntentosDeTemperatura() {
        var confirmada = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Tengo fiebre desde ayer", false),
                bt("¿Pudiste medirte la fiebre con un termómetro o solo sientes temperatura alta?"),
                pt("Sí, la medí", false)));
        var temperatura = dato(confirmada, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA);
        assertEquals(Estado.PENDIENTE, temperatura.estado());
        assertEquals(0, temperatura.intentos());
        assertEquals(new ProgresoEntrevista.ObjetivoTurno(ProgresoEntrevista.TipoObjetivo.DATO,
                "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA), confirmada.objetivoDelTurno());
        assertEquals("CONFIRMACION_MEDICION_REPETIDA", confirmada.validarPreguntaPropuesta(
                "¿Pudiste medirte la fiebre con un termómetro o solo sientes temperatura alta?").orElseThrow());

        var noMedida = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Tengo fiebre desde ayer", false),
                bt("¿Pudiste medirte la fiebre con un termómetro o solo sientes temperatura alta?"),
                pt("No la medí", false)));
        assertEquals(Estado.DESCONOCIDO,
                dato(noMedida, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado());
    }

    @Test
    void variantesComunesDeConfirmacionNoConsumenIntentosNiConfundenLaPreguntaPorElValor() {
        List<String> preguntas = List.of("¿Mediste la temperatura?", "¿Pudiste medir la temperatura?",
                "¿Te tomaste la temperatura?", "¿Pudiste tomar la temperatura?");
        List<String> respuestas = List.of("Sí, me la medí", "Sí, me la medí", "Sí, me la tomé", "Sí");
        for (int i = 0; i < preguntas.size(); i++) {
            var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                    pt("Tengo fiebre", false), bt(preguntas.get(i)), pt(respuestas.get(i), false)));
            var temperatura = dato(progreso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA);
            assertEquals(Estado.PENDIENTE, temperatura.estado(), preguntas.get(i));
            assertEquals(0, temperatura.intentos(), preguntas.get(i));
        }

        var afirmacionSinSi = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Tengo fiebre", false), bt("¿Pudiste medir la temperatura?"), pt("Me la medí", false)));
        var temperaturaConfirmada = dato(afirmacionSinSi, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA);
        assertEquals(Estado.PENDIENTE, temperaturaConfirmada.estado());
        assertEquals(0, temperaturaConfirmada.intentos());

        var preguntaNumerica = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Tengo fiebre", false), bt("¿Qué temperatura marcó el termómetro?"),
                pt("Sí, me la medí", false)));
        var temperatura = dato(preguntaNumerica, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA);
        assertEquals(Estado.PENDIENTE, temperatura.estado());
        assertEquals(1, temperatura.intentos(), "Una respuesta afirmativa no satisface la pregunta por grados");
    }

    @Test
    void negativasDeConfirmacionDeMedicionQuedanComoDesconocidas() {
        for (String respuesta : List.of("No tengo termómetro", "No me tomé la temperatura", "No me la medí")) {
            var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                    pt("Tengo fiebre", false), bt("¿Te tomaste la temperatura?"), pt(respuesta, false)));
            assertEquals(Estado.DESCONOCIDO,
                    dato(progreso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado(), respuesta);
            assertEquals(0,
                    dato(progreso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).intentos(), respuesta);

            var preguntaDirecta = ProgresoEntrevista.evaluarConContexto(List.of(
                    pt("Tengo fiebre", false), bt("¿Qué temperatura marcó el termómetro?"), pt(respuesta, false)));
            assertEquals(Estado.DESCONOCIDO,
                    dato(preguntaDirecta, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado(), respuesta);
        }
    }

    @Test
    void confirmacionYaDadaNoSeRepiteDuranteLaAclaracionDelValor() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Tengo fiebre", false),
                bt("¿Pudiste medir la temperatura?"), pt("Sí, me la medí", false),
                bt("¿Qué temperatura marcó el termómetro?"), pt("38 kg", true)));
        assertEquals(new ProgresoEntrevista.ObjetivoTurno(ProgresoEntrevista.TipoObjetivo.ACLARACION,
                "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA), progreso.objetivoDelTurno());
        assertEquals("CONFIRMACION_MEDICION_REPETIDA", progreso.validarPreguntaPropuesta(
                "¿Te tomaste la temperatura?").orElseThrow());
    }

    @Test
    void chat21DosRespuestasInvalidasNoAgotanElValorYLaTerceraValidaSeRegistra() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Me duele la cabeza y tengo fiebre", false),
                bt("¿Desde cuándo te duele la cabeza?"), pt("Desde ayer", false),
                bt("¿Desde cuándo tienes fiebre?"), pt("Desde ayer también", false),
                bt("¿Pudiste medirte la fiebre con un termómetro o solo sientes temperatura alta?"),
                pt("Sí, la medí", false),
                bt("¿Qué temperatura marcó el termómetro?"), pt("38 kg", true),
                bt("¿Qué temperatura marcó el termómetro?"), pt("38 Calvin Klein", true)));
        assertEquals(Estado.PENDIENTE, dato(progreso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado());
        assertEquals(2, dato(progreso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).intentos());
        assertEquals(0, dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD).intentos());

        progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Me duele la cabeza y tengo fiebre", false),
                bt("¿Desde cuándo te duele la cabeza?"), pt("Desde ayer", false),
                bt("¿Desde cuándo tienes fiebre?"), pt("Desde ayer también", false),
                bt("¿Pudiste medirte la fiebre con un termómetro o solo sientes temperatura alta?"),
                pt("Sí, la medí", false),
                bt("¿Qué temperatura marcó el termómetro?"), pt("38 kg", true),
                bt("¿Qué temperatura marcó el termómetro?"), pt("38 Calvin Klein", true),
                bt("¿Qué temperatura marcó el termómetro?"), pt("38", false)));
        var temperaturaRecuperada = dato(progreso, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA);
        assertEquals(Estado.RESPONDIDO, temperaturaRecuperada.estado());
        assertEquals("38", temperaturaRecuperada.valor());
        assertEquals(3, temperaturaRecuperada.intentos());
    }

    @Test
    void valorExcluidoMantieneAclaracionLigadaAlDatoDeTemperatura() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Tengo fiebre desde ayer", false),
                bt("¿Qué temperatura marcó el termómetro?"),
                pt("38 kg", true)));
        assertTrue(progreso.requiereAclaracion());
        assertEquals(new ProgresoEntrevista.ObjetivoTurno(ProgresoEntrevista.TipoObjetivo.ACLARACION,
                "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA), progreso.objetivoDelTurno());
        assertTrue(progreso.instruccionSiguientePaso().toLowerCase(java.util.Locale.ROOT).contains("temperatura"));
    }

    @Test
    void datoAgotadoPuedeRecuperarseSoloConRespuestaValidaAUnaPreguntaExplicita() {
        List<ProgresoEntrevista.Turno> base = List.of(
                pt("Tengo fiebre desde ayer", false),
                bt("¿Qué temperatura marcó el termómetro?"), pt("38 kg", true),
                bt("¿Qué temperatura marcó el termómetro?"), pt("38 Calvin Klein", true),
                bt("¿Qué temperatura marcó el termómetro?"), pt("Messi 8", true));
        var agotado = ProgresoEntrevista.evaluarConContexto(base);
        assertEquals(Estado.AGOTADO, dato(agotado, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).estado());
        assertEquals(3, dato(agotado, "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA).intentos());

        var bareSinPregunta = new java.util.ArrayList<>(base);
        bareSinPregunta.add(pt("38", false));
        assertEquals(Estado.AGOTADO, dato(ProgresoEntrevista.evaluarConContexto(bareSinPregunta), "fiebre",
                ProgresoEntrevista.Dimension.TEMPERATURA).estado());

        var excluidaEnRecuperacion = new java.util.ArrayList<>(base);
        excluidaEnRecuperacion.add(bt("¿Qué temperatura marcó el termómetro?"));
        excluidaEnRecuperacion.add(pt("38", true));
        assertEquals(Estado.AGOTADO, dato(ProgresoEntrevista.evaluarConContexto(excluidaEnRecuperacion), "fiebre",
                ProgresoEntrevista.Dimension.TEMPERATURA).estado());

        var recuperada = new java.util.ArrayList<>(base);
        recuperada.add(bt("¿Qué temperatura marcó el termómetro?"));
        recuperada.add(pt("38", false));
        var temperatura = dato(ProgresoEntrevista.evaluarConContexto(recuperada), "fiebre", ProgresoEntrevista.Dimension.TEMPERATURA);
        assertEquals(Estado.RESPONDIDO, temperatura.estado());
        assertEquals("38", temperatura.valor());
        assertEquals(3, temperatura.intentos());

        var espontanea = new java.util.ArrayList<>(base);
        espontanea.add(pt("Tengo fiebre de 38 grados", false));
        assertEquals(Estado.RESPONDIDO, dato(ProgresoEntrevista.evaluarConContexto(espontanea), "fiebre",
                ProgresoEntrevista.Dimension.TEMPERATURA).estado());
    }

    @Test
    void dolorAgotadoSeRecuperaConUnDatoEspontaneoExplicitoDelMismoDolor() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                pt("Me duele la cabeza desde ayer", false),
                bt("Del 1 al 10, ¿cuánto te duele la cabeza?"), pt("Messi 8", true),
                bt("Del 1 al 10, ¿cuánto te duele la cabeza?"), pt("Messi 8", true),
                bt("Del 1 al 10, ¿cuánto te duele la cabeza?"), pt("Messi 8", true),
                pt("El dolor de cabeza sigue igual y es 6/10", false)));
        var intensidad = dato(progreso, "dolor:cabeza", ProgresoEntrevista.Dimension.INTENSIDAD);
        assertEquals(Estado.RESPONDIDO, intensidad.estado());
        assertEquals("6", intensidad.valor());
        assertEquals(3, intensidad.intentos());
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
    void aclaracionAbiertaConservaElObjetivoAmbiguoYUnaRespuestaFisicaNoInventaDolor() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Siento un vacío en el pecho"),
                b("¿Podés describir con tus palabras qué sentís con ese vacío?"),
                p("Es físico")));

        assertEquals(ProgresoEntrevista.MotivoCierre.EN_CURSO, progreso.motivoCierre());
        assertTrue(progreso.datos().stream().anyMatch(d -> d.sintomaId().equals("sensacion:vacio_pecho")
                && d.estado() == ProgresoEntrevista.Estado.PENDIENTE));
        assertFalse(progreso.datos().stream().anyMatch(d -> d.sintomaId().startsWith("dolor:")));
        assertEquals(ProgresoEntrevista.TipoObjetivo.DATO, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void aclaracionAbiertaPuedeConfirmarUnMotivoEmocionalSinCrearUnaSensacionFisica() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Siento un vacío en el pecho"),
                b("¿Podés contarme con tus palabras qué significa para vos ese vacío?"),
                p("Es emocional")));

        assertEquals(ProgresoEntrevista.MotivoCierre.EN_CURSO, progreso.motivoCierre());
        assertTrue(progreso.datos().isEmpty());
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void negacionDeUnaCategoriaNoAfirmaLaCategoriaOpuesta() {
        for (String respuesta : List.of("No es físico", "No es emocional")) {
            var progreso = ProgresoEntrevista.evaluar(turnos(
                    p("Siento un vacío en el pecho"),
                    b("¿Podés describir con tus palabras qué sentís con ese vacío?"),
                    p(respuesta)));

            assertTrue(progreso.requiereAclaracion(), respuesta);
            assertTrue(progreso.datos().isEmpty(), respuesta);
            assertEquals(ProgresoEntrevista.TipoObjetivo.ACLARACION, progreso.objetivoDelTurno().tipo(), respuesta);
        }
    }

    @Test
    void aclaracionAbiertaAceptaUnSintomaRealSinConvertirLaFraseAmbiguaEnHallazgo() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Siento un vacío en el pecho"),
                b("¿Podés contarme con tus palabras qué te pasa?"),
                p("También me duele la cabeza")));

        assertTrue(progreso.datos().stream().anyMatch(d -> d.sintomaId().equals("dolor:cabeza")));
        assertFalse(progreso.datos().stream().anyMatch(d -> d.sintomaId().equals("sensacion:vacio_pecho")));
    }

    @Test
    void aclaracionAbiertaNoReemplazaElObjetivoDeUnDatoPendiente() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Tengo tos"), b("¿Desde cuándo empezó la tos?"), p("Messi 8"),
                b("¿Desde cuándo empezó la tos?"), p("Desde ayer")));

        assertEquals("desde ayer", dato(progreso, "sintoma:tos", ProgresoEntrevista.Dimension.INICIO).valor());
        assertEquals(ProgresoEntrevista.TipoObjetivo.DATO, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void respuestaEmocionalTrasContextoExcluidoPermiteInvitacionSinCrearSensacion() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podés describir con tus palabras qué sentís?"),
                pt("Es emocional", false)));

        assertTrue(progreso.datos().isEmpty());
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void respuestaFisicaSinRepetirDatoExcluidoNoConfirmaSensacion() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podés describir con tus palabras qué sentís?"),
                pt("Es físico", false)));

        assertTrue(progreso.datos().isEmpty());
        assertTrue(progreso.requiereAclaracion());
        assertEquals(ProgresoEntrevista.TipoObjetivo.ACLARACION, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void reiteracionAutocontenidaDelVacioFisicoEsUnHallazgoDelPaciente() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podés describir con tus palabras qué sentís?"),
                pt("La sensación de vacío es física", false)));

        assertTrue(progreso.datos().stream().anyMatch(d -> d.sintomaId().equals("sensacion:vacio_pecho")));
        assertFalse(progreso.datos().stream().anyMatch(d -> d.sintomaId().startsWith("dolor:")));
    }

    @Test
    void negacionDeLaCategoriaFisicaNoRegistraElVacioComoSensacionFisica() {
        for (String relato : List.of("Siento un vacío que no es una sensación física",
                "Siento un vacío en el pecho que no es físico")) {
            var progreso = ProgresoEntrevista.evaluar(turnos(p(relato)));

            assertTrue(progreso.datos().isEmpty(), relato);
            assertTrue(progreso.requiereAclaracion(), relato);
        }
    }

    @Test
    void chat25RespuestaTrasContextoExcluidoNoCreaDolorYPermiteInvitacion() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                bt("Hola"),
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podrías explicarme con tus palabras qué querés decir o qué sentís con eso de \"un vacío en el pecho\"?"),
                pt("No sé, lo siento desde que me dejó", false)));

        assertTrue(progreso.datos().isEmpty());
        assertFalse(progreso.datos().stream().anyMatch(d -> d.sintomaId().startsWith("dolor:")));
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void invitacionSemanticaNoCubiertaPorRegexPermiteCerrarTrasLaRespuesta() {
        var historial = new java.util.ArrayList<ProgresoEntrevista.Turno>(List.of(
                bt("Hola"),
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podrías explicarme con tus palabras qué querés decir o qué sentís con eso de \"un vacío en el pecho\"?"),
                pt("No sé, empecé a sentir mucha tristeza cuando mi pareja se mudó a otra ciudad", false)));
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION,
                ProgresoEntrevista.evaluarConContexto(historial).objetivoDelTurno().tipo());

        historial.add(bt("¿Hay algún otro detalle que quieras compartir?"));
        historial.add(pt("No", false));
        var progreso = ProgresoEntrevista.evaluarConContexto(historial);

        assertTrue(progreso.puedeCerrar());
        assertEquals(ProgresoEntrevista.MotivoCierre.DATOS_RESUELTOS, progreso.motivoCierre());
    }

    @Test
    void preguntaAjenaNoSeInterpretaComoInvitacionAunqueElObjetivoCanonicoLoSea() {
        var historial = new java.util.ArrayList<ProgresoEntrevista.Turno>(List.of(
                bt("Hola"),
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podrías explicarme con tus palabras qué querés decir o qué sentís con eso de \"un vacío en el pecho\"?"),
                pt("No sé, empecé a sentir mucha tristeza cuando mi pareja se mudó a otra ciudad", false)));
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION,
                ProgresoEntrevista.evaluarConContexto(historial).objetivoDelTurno().tipo());

        historial.add(bt("¿A qué hora te acostás?"));
        historial.add(pt("No", false));
        var progreso = ProgresoEntrevista.evaluarConContexto(historial);

        assertFalse(progreso.puedeCerrar());
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void preguntaPorUnaSensacionNoEsInvitacionPorIncluirAlgoYSolicitarDescripcion() {
        var historial = new java.util.ArrayList<ProgresoEntrevista.Turno>(List.of(
                bt("Hola"),
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podrías explicarme con tus palabras qué querés decir o qué sentís con eso de \"un vacío en el pecho\"?"),
                pt("No sé, empecé a sentir mucha tristeza cuando mi pareja se mudó a otra ciudad", false)));
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION,
                ProgresoEntrevista.evaluarConContexto(historial).objetivoDelTurno().tipo());

        historial.add(bt("¿Podrías contarme si sentís algo en el brazo?"));
        historial.add(pt("No", false));
        var progreso = ProgresoEntrevista.evaluarConContexto(historial);

        assertFalse(progreso.puedeCerrar());
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void preguntaSemanticaNoReemplazaUnDatoPendientePorInvitacion() {
        var progreso = ProgresoEntrevista.evaluar(turnos(
                p("Tengo tos"), b("¿Desde cuándo empezó la tos?"), p("Desde ayer"),
                b("¿Hay algún otro detalle que quieras compartir?"), p("No")));

        assertFalse(progreso.puedeCerrar());
        assertEquals(ProgresoEntrevista.TipoObjetivo.DATO, progreso.objetivoDelTurno().tipo());
        assertEquals(Estado.PENDIENTE, dato(progreso, "sintoma:tos", ProgresoEntrevista.Dimension.IMPACTO).estado());
    }

    @Test
    void preguntaDeRegistroNoSeInterpretaComoInvitacionSemantica() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                bt("Hola"),
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podrías explicarme con tus palabras qué querés decir o qué sentís con eso de \"un vacío en el pecho\"?"),
                pt("No sé, empecé a sentir mucha tristeza cuando mi pareja se mudó a otra ciudad", false),
                bt("La entrevista ya quedó registrada. ¿Querés continuar?"),
                pt("No", false)));

        assertFalse(progreso.puedeCerrar());
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void respuestaExcluidaANuevaInvitacionSemanticaNoLaDaPorContestada() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                bt("Hola"),
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "Siento un vacío en el pecho", true),
                bt("¿Podrías explicarme con tus palabras qué querés decir o qué sentís con eso de \"un vacío en el pecho\"?"),
                pt("No sé, empecé a sentir mucha tristeza cuando mi pareja se mudó a otra ciudad", false),
                bt("¿Hay algún otro detalle que quieras compartir?"),
                new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, "No", true)));

        assertFalse(progreso.puedeCerrar());
        assertEquals(ProgresoEntrevista.TipoObjetivo.INVITACION, progreso.objetivoDelTurno().tipo());
    }

    @Test
    void preguntaSemanticaSinMotivoPrevioNoSeInterpretaComoInvitacion() {
        var progreso = ProgresoEntrevista.evaluarConContexto(List.of(
                bt("¿Hay algún otro detalle que quieras compartir?"), pt("No", false)));

        assertFalse(progreso.puedeCerrar());
        assertEquals(ProgresoEntrevista.TipoObjetivo.ACLARACION, progreso.objetivoDelTurno().tipo());
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
    private static ProgresoEntrevista.Turno pt(String text, boolean excluido) {
        return new ProgresoEntrevista.Turno(AutorMensaje.PACIENTE, text, excluido);
    }
    private static ProgresoEntrevista.Turno bt(String text) {
        return new ProgresoEntrevista.Turno(AutorMensaje.BOT, text, false);
    }
}
