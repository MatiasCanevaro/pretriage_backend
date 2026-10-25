package com.pretriage.backend.services.voz;

import com.pretriage.backend.controllers.dtos.ResumenEntrevistaVoz;
import com.pretriage.backend.controllers.dtos.TurnoVoz;
import com.pretriage.backend.model.chat.AutorMensaje;
import com.pretriage.backend.services.LenguajeClinicoChat;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Checks closure eligibility without trusting Gemini's summary as patient testimony. */
public final class ValidadorCierreEntrevistaVoz {
    private static final int MAX_RESPUESTAS = LenguajeClinicoChat.MAX_RESPUESTAS_PACIENTE;
    private static final int MAX_PREGUNTAS_CUANTIFICACION = 4;
    private static final String SINTOMAS = "dolor|duele|duelen|cefalea|molestia|fiebre|tos|vomitos?|nauseas?|mareos?|sangrado|diarrea|debilidad|moraton(?:es)?|moreton(?:es)?|hematomas?|golpes?|hinchazon|hormigueo|entumecimiento|heridas?|lesion(?:es)?|quemaduras?|queme|quemado|quemada|ardor|me arde|cortes?|cortado|cortada|picazon|me pica|ronchas?|erupcion|sarpullido|torcedura|torci|lastimado|lastimada";
    private static final String OTROS_SINTOMAS = "molestia|tos|vomitos?|nauseas?|mareos?|sangrado|diarrea|debilidad|moraton(?:es)?|moreton(?:es)?|hematomas?|golpes?|hinchazon|hormigueo|entumecimiento|heridas?|lesion(?:es)?|quemaduras?|queme|quemado|quemada|ardor|me arde|cortes?|cortado|cortada|picazon|me pica|ronchas?|erupcion|sarpullido|torcedura|torci|lastimado|lastimada";
    private static final String ALARMAS = "no puedo respirar|me cuesta respirar|dificultad respiratoria|dificultad para respirar|falta de aire|no puedo mover (?:el|un) brazo|boca (?:torcida|desviada)|debilidad unilateral|debilidad (?:subita|repentina)|me duele el pecho|dolor (?:de|en el) pecho|opresion (?:toracica|en el pecho)|disnea|perdida de (?:conciencia|conocimiento)|desmayos?|confusion|desorientad[oa]s?|convulsion(?:es)?|sangrado abundante|reaccion alergica";
    private static final Pattern INTENSIDAD = Pattern.compile("\\b(10|[0-9])\\s*(?:/|de|sobre)\\s*10\\b");
    private static final Pattern NEGACION = Pattern.compile("\\b(?:no|sin|niega|niego|descarta|descarto|ningun|ninguna|ninguno)\\b");
    private static final Pattern AFIRMACION_TRAS_NEGACION = Pattern.compile("\\b(?:pero|aunque|si|y)\\s+(?:me|tengo|siento|presento|aparecio|aparecen|noto)\\b");

    private ValidadorCierreEntrevistaVoz() { }

    /** Empty means closure may proceed; otherwise return a safe corrective tool response. */
    public static Optional<String> validar(List<TurnoVoz> historial, ResumenEntrevistaVoz resumen) {
        if (!estructuraValida(resumen)) {
            return Optional.of("Corrige el resumen: incluye todos los campos de texto y listas requeridos, "
                    + "sin textos vacios; usa no informado o listas vacias para datos ausentes y dolor entre 0 y 10 o null. No inventes datos.");
        }
        List<TurnoVoz> turnos = historial == null ? List.of() : historial;
        long respuestas = turnos.stream().filter(ValidadorCierreEntrevistaVoz::esRespuesta).count();
        if (respuestas == 0) {
            return Optional.of("Espera una respuesta transcripta del paciente antes de solicitar el cierre de la entrevista.");
        }
        boolean alarma = contieneAlarmaLiteral(turnos);
        // A summary can be fabricated or contain an instruction; only the patient's own
        // affirmed words may bypass the ordinary closure questions.
        if (alarma || respuestas >= MAX_RESPUESTAS) {
            return Optional.empty();
        }
        return validarSuficiencia(turnos, respuestas);
    }

    /** Text and voice use the same patient evidence, including answers to preceding questions. */
    public static boolean tieneDatosSuficientes(List<TurnoVoz> historial) {
        if (historial == null) return false;
        long respuestas = historial.stream().filter(ValidadorCierreEntrevistaVoz::esRespuesta).count();
        if (respuestas == 0) return false;
        return contieneAlarmaLiteral(historial) || respuestas >= MAX_RESPUESTAS
                || validarSuficiencia(historial, respuestas).isEmpty();
    }

    private static Optional<String> validarSuficiencia(List<TurnoVoz> turnos, long respuestas) {
        if (respuestas == 0) {
            return Optional.of("Espera una respuesta transcripta del paciente antes de solicitar el cierre de la entrevista.");
        }
        if (terminarSolicitado(turnos)) return Optional.empty();
        if (!tieneMotivoConcreto(turnos)) {
            return Optional.of("El motivo aun no se entiende. Hace una pregunta breve para que el paciente explique que siente o que le ocurre; no enumeres sintomas ni opciones.");
        }
        Cuantificacion cuantificacion = analizarCuantificacion(turnos);
        if (cuantificacion.preguntaPendiente()) {
            return Optional.of("Espera la respuesta a la pregunta de cuantificacion pendiente; no agregues otra pregunta en este turno.");
        }
        if (cuantificacion.preguntas() < MAX_PREGUNTAS_CUANTIFICACION) {
            if (!cuantificacion.inicioResuelto()) {
                return Optional.of("Cuantifica brevemente desde cuándo empezó cada síntoma ya relatado que siga pendiente: "
                        + cuantificacion.sintomasInicioPendientes() + ". Pregunta solo inicio/duración y no apliques la respuesta de un síntoma a otro.");
            }
            if (!cuantificacion.gravedadResuelta()) {
                return Optional.of("Cuantifica la intensidad del dolor de 0 a 10 si el paciente relató dolor, o el impacto funcional del síntoma concreto que siga pendiente. Pregunta solo esa variable y no extrapoles datos entre síntomas.");
            }
            if (cuantificacion.hayFiebre() && !cuantificacion.temperaturaIntentada()) {
                return Optional.of("Si el paciente midio la temperatura, pregunta cuanto marco; acepta 'no se' o 'no la medi' como desconocido y no insistas. Pregunta solo esa variable.");
            }
        }
        boolean cuantificacionCompleta = cuantificacion.inicioResuelto() && cuantificacion.gravedadResuelta()
                && (!cuantificacion.hayFiebre() || cuantificacion.temperaturaIntentada());
        if (cuantificacionCompleta && cuantificacion.preguntas() == 0) return Optional.empty();
        if (cuantificacionCompleta || cuantificacion.preguntas() >= MAX_PREGUNTAS_CUANTIFICACION) {
            if (rechazoGlobalExplicito(turnos)) return Optional.empty();
            int ultimaInvitacion = ultimaInvitacionAbierta(turnos);
            if (ultimaInvitacion < 0) {
                return Optional.of("Hace una sola invitacion abierta para que agregue algo mas sobre lo que ya le pasa; no preguntes por sintomas nuevos ni por una lista de datos.");
            }
            return respondioDespues(turnos, ultimaInvitacion) ? Optional.empty()
                    : Optional.of("Espera la respuesta a la unica invitacion abierta. No la repitas ni explores sintomas nuevos.");
        }
        return Optional.of("Continua con la cuantificacion pendiente de los sintomas que ya relato; no repitas dimensiones conocidas y no investigues sintomas nuevos.");
    }

    private record Cuantificacion(boolean inicioDolorResuelto, boolean inicioFiebreResuelto,
                                  boolean inicioOtroResuelto, boolean dolorResuelto, boolean otroResuelto,
                                  boolean temperaturaIntentada, boolean hayDolor, boolean hayFiebre, boolean hayOtro, int preguntas,
                                  boolean preguntaPendiente) {
        boolean inicioResuelto() { return inicioDolorResuelto && inicioFiebreResuelto && inicioOtroResuelto; }
        boolean gravedadResuelta() { return (dolorResuelto || (!hayDolor && hayFiebre && temperaturaIntentada)) && otroResuelto; }
        String sintomasInicioPendientes() {
            if (hayDolor && !inicioDolorResuelto && hayFiebre && !inicioFiebreResuelto) return "el dolor y la fiebre, cada uno por separado";
            if (hayDolor && !inicioDolorResuelto) return "el dolor";
            if (hayOtro && !inicioOtroResuelto) return "el otro síntoma relatado";
            return "la fiebre";
        }
    }

    private static Cuantificacion analizarCuantificacion(List<TurnoVoz> turnos) {
        boolean inicioDolor = false, inicioFiebre = false, inicioOtro = false, dolorDato = false, otroDato = false, tempDato = false;
        boolean inicioDolorIntentado = false, inicioFiebreIntentado = false, inicioOtroIntentado = false;
        boolean dolorIntentado = false, otroIntentado = false, tempIntentada = false;
        boolean hayFiebre = false, hayDolor = false, hayOtro = false, pendiente = false;
        int preguntas = 0;
        boolean preguntaInicio = false, preguntaGravedad = false, preguntaImpacto = false, preguntaTemperatura = false;
        boolean destinoDolor = false, destinoFiebre = false, destinoOtro = false;
        String textoPregunta = "";
        for (TurnoVoz turno : turnos) {
            if (turno == null || turno.contenido() == null) continue;
            String texto = normalizar(turno.contenido());
            if (turno.autor() == AutorMensaje.BOT) {
                preguntaInicio = contiene(texto, "desde cuando|cuando (?:empezo|empezaron|comenzo|comenzaron)|hace cuanto|desde hace cuanto|inicio|duracion");
                preguntaGravedad = contiene(texto, "intensidad|cuanto te duele|(?:del|de) (?:0|cero) (?:al|a) (?:10|diez)|impacto|te impide|limita|funcional")
                        || (!preguntaInicio && contiene(texto, "que tanto|afecta|actividades|hacer tus cosas"));
                preguntaImpacto = preguntaGravedad && contiene(texto, "impacto|te impide|limita|funcional|que tanto|afecta|actividades|hacer tus cosas");
                preguntaTemperatura = contiene(texto, "temperatura|cuantos grados");
                int dimensiones = (preguntaInicio ? 1 : 0) + (preguntaGravedad ? 1 : 0) + (preguntaTemperatura ? 1 : 0);
                preguntas += dimensiones;
                pendiente = dimensiones > 0;
                textoPregunta = texto;
                boolean nombraDolor = sintomaMencionado(texto, "dolor|duele|duelen|cefalea");
                boolean nombraFiebre = sintomaMencionado(texto, "fiebre|febril");
                destinoDolor = nombraDolor || (!nombraFiebre && hayDolor && !hayFiebre);
                destinoFiebre = nombraFiebre || (!nombraDolor && hayFiebre && !hayDolor);
                boolean nombraOtro = sintomaMencionado(texto, OTROS_SINTOMAS);
                destinoOtro = nombraOtro || (!nombraDolor && !nombraFiebre && hayOtro && !hayDolor && !hayFiebre);
                if (!nombraDolor && !nombraFiebre) {
                    destinoDolor = hayDolor && !hayFiebre;
                    destinoFiebre = hayFiebre && !hayDolor;
                    destinoOtro = hayOtro && !hayDolor && !hayFiebre;
                }
                if (preguntaInicio && contiene(texto, "ambos|los dos|sintomas")) {
                    destinoDolor = hayDolor;
                    destinoFiebre = hayFiebre;
                    destinoOtro = hayOtro;
                }
                if (preguntaGravedad && contiene(texto, "afecta|actividades|hacer tus cosas")) {
                    destinoDolor = hayDolor;
                    destinoOtro = hayOtro;
                }
                if (preguntaTemperatura) destinoFiebre = true;
            } else if (esRespuesta(turno)) {
                String[] segmentos = texto.split("[;!?\\n]|\\.(?!\\d)|,(?!\\d)|\\bpero\\b|\\bsin embargo\\b|\\baunque\\b|\\by\\b");
                for (String segmento : segmentos) {
                    boolean mencionaDolor = sintomaAfirmado(segmento, "dolor|duele|duelen|cefalea");
                    boolean mencionaFiebre = sintomaAfirmado(segmento, "fiebre|febril");
                    boolean mencionaOtro = sintomaAfirmado(segmento, OTROS_SINTOMAS);
                    hayDolor |= mencionaDolor;
                    hayFiebre |= mencionaFiebre;
                    hayOtro |= mencionaOtro;
                    boolean temporal = inicioInformado(segmento) || contiene(segmento, "\\b\\d+\\s*(?:minutos?|horas?|dias?|semanas?|meses?)\\b");
                    if (temporal) {
                        inicioDolor |= mencionaDolor;
                        inicioFiebre |= mencionaFiebre;
                        inicioOtro |= mencionaOtro;
                        if (!mencionaDolor && !mencionaFiebre && !mencionaOtro && contiene(segmento, "ambos|los dos|los dos sintomas|ambos sintomas")) {
                            inicioDolor |= hayDolor;
                            inicioFiebre |= hayFiebre;
                            inicioOtro |= hayOtro;
                        }
                        if (!mencionaDolor && !mencionaFiebre && preguntaInicio) {
                            inicioDolor |= destinoDolor;
                            inicioFiebre |= destinoFiebre;
                            inicioOtro |= destinoOtro;
                        }
                        if (!mencionaDolor && !mencionaFiebre && !mencionaOtro && !preguntaInicio) {
                            // A standalone temporal clause can complete the only symptom
                            // affirmed so far, but never transfers onset between symptoms.
                            inicioDolor |= hayDolor && !hayFiebre && !hayOtro;
                            inicioFiebre |= hayFiebre && !hayDolor && !hayOtro;
                            inicioOtro |= hayOtro && !hayDolor && !hayFiebre;
                        }
                    }
                    boolean intensidad = INTENSIDAD.matcher(segmento).find()
                            || contiene(segmento, "\\b(?:leve|moderad\\w*|fuerte|intens\\w*)\\b")
                            || impactoFuncional(segmento);
                    if (intensidad) {
                        dolorDato |= mencionaDolor;
                        otroDato |= mencionaOtro;
                        if (!mencionaDolor && !mencionaFiebre && preguntaGravedad) dolorDato |= destinoDolor;
                        if (!mencionaDolor && !mencionaFiebre && preguntaGravedad) otroDato |= destinoOtro;
                        if (!mencionaDolor && !mencionaFiebre && !preguntaGravedad) {
                            dolorDato |= hayDolor && !hayFiebre && !hayOtro;
                            otroDato |= hayOtro && !hayDolor && !hayFiebre;
                        }
                    }
                    if (preguntaGravedad && destinoDolor && extraerIntensidad(segmento, textoPregunta) != null) dolorDato = true;
                    if (preguntaImpacto && respuestaImpactoFuncional(segmento)) {
                        dolorDato |= destinoDolor;
                        otroDato |= destinoOtro;
                    }
                    boolean temp = contiene(segmento, "\\b\\d{2}(?:[.,]\\d)?\\s*(?:grados?|°)\\b|\\btemperatura\\s*(?:de|:)?\\s*\\d{2}")
                            || (preguntaTemperatura && segmento.matches("(?:\\d{2}(?:[.,]\\d)?)(?:\\s*(?:grados?|°))?[.!]?"));
                    tempDato |= temp && (mencionaFiebre || preguntaTemperatura);
                    if (!mencionaDolor && !mencionaFiebre && preguntaInicio && contiene(segmento, "no se|no recuerdo|desconozco|no quiero|prefiero no|no puedo decir")) {
                        inicioDolorIntentado |= destinoDolor;
                        inicioFiebreIntentado |= destinoFiebre;
                        inicioOtroIntentado |= destinoOtro;
                    }
                    if (!mencionaDolor && !mencionaFiebre && !mencionaOtro && !preguntaInicio
                            && contiene(segmento, "no se desde cuando|no recuerdo desde cuando|desconozco desde cuando|no se cuando empezo|no recuerdo cuando empezo|no recuerdo (?:si fue|si empezo) (?:ayer|anoche|hoy|anteayer)")) {
                        inicioDolorIntentado |= hayDolor && !hayFiebre && !hayOtro;
                        inicioFiebreIntentado |= hayFiebre && !hayDolor && !hayOtro;
                        inicioOtroIntentado |= hayOtro && !hayDolor && !hayFiebre;
                    }
                    if (!mencionaDolor && !mencionaFiebre && !mencionaOtro && preguntaGravedad && contiene(segmento, "no se|no recuerdo|no quiero|prefiero no|no puedo decir")) {
                        dolorIntentado |= destinoDolor;
                        otroIntentado |= destinoOtro;
                    }
                    if (preguntaTemperatura && contiene(segmento, "no se|no medi|no la medi|no me medi|no recuerdo|no quiero|prefiero no")) tempIntentada = true;
                    if (contiene(segmento, "no se desde cuando|no recuerdo desde cuando|desconozco desde cuando|no se cuando empezo|no recuerdo cuando empezo|no recuerdo (?:si fue|si empezo) (?:ayer|anoche|hoy|anteayer)")) {
                        inicioDolorIntentado |= mencionaDolor || (preguntaInicio && destinoDolor);
                        inicioFiebreIntentado |= mencionaFiebre || (preguntaInicio && destinoFiebre);
                        inicioOtroIntentado |= mencionaOtro || (preguntaInicio && destinoOtro);
                    }
                    if (contiene(segmento, "no me (?:la )?medi|no medi (?:la )?(?:temperatura)?|no se (?:mi )?temperatura|no recuerdo (?:mi )?temperatura")
                            && (mencionaFiebre || hayFiebre)) tempIntentada = true;
                    if (contiene(segmento, "no se (?:cuanto|que tan)|no recuerdo (?:cuanto|que tan)|no quiero (?:decir|responder) (?:la )?(?:intensidad|cuanto)|prefiero no (?:decir|responder) (?:la )?(?:intensidad|cuanto)")) {
                        dolorIntentado |= mencionaDolor || (preguntaGravedad && destinoDolor);
                        otroIntentado |= mencionaOtro || (preguntaGravedad && destinoOtro);
                        dolorIntentado |= hayDolor && !hayFiebre && !hayOtro;
                        otroIntentado |= hayOtro && !hayDolor && !hayFiebre;
                    }
                }
                pendiente = false;
                preguntaInicio = preguntaGravedad = preguntaImpacto = preguntaTemperatura = false;
                destinoDolor = destinoFiebre = destinoOtro = false;
                textoPregunta = "";
            }
        }
        return new Cuantificacion(!hayDolor || inicioDolor || inicioDolorIntentado,
                !hayFiebre || inicioFiebre || inicioFiebreIntentado,
                !hayOtro || inicioOtro || inicioOtroIntentado,
                !hayDolor || dolorDato || dolorIntentado,
                !hayOtro || otroDato || otroIntentado,
                tempDato || tempIntentada, hayDolor, hayFiebre, hayOtro, preguntas, pendiente);
    }

    private static boolean sintomaMencionado(String texto, String sintomas) {
        return Pattern.compile("\\b(?:" + sintomas + ")\\b").matcher(texto).find();
    }

    private static boolean sintomaAfirmado(String texto, String sintomas) {
        for (String segmento : texto.split("[,;.!?\\n]|\\bpero\\b|\\bsin embargo\\b|\\baunque\\b")) {
            var sintoma = Pattern.compile("\\b(?:" + sintomas + ")\\b").matcher(segmento);
            while (sintoma.find()) {
                String prefijo = segmento.substring(0, sintoma.start());
                var negacion = NEGACION.matcher(prefijo);
                int ultimaNegacion = -1;
                while (negacion.find()) ultimaNegacion = negacion.start();
                if (ultimaNegacion < 0 || AFIRMACION_TRAS_NEGACION.matcher(prefijo.substring(ultimaNegacion)).find()) return true;
            }
        }
        return false;
    }

    private static boolean impactoFuncional(String texto) {
        return contiene(texto, "\\b(?:no puedo|me cuesta|me resulta dificil|no logro) (?:hacer|cumplir|seguir|realizar|caminar|trabajar|dormir|comer|mis tareas|mis actividades)|\\bno puedo (?:hacer mis tareas|trabajar|hacer mis actividades)\\b");
    }

    private static boolean respuestaImpactoFuncional(String texto) {
        return contiene(texto, "\\b(?:mucho|muchisimo|bastante|poco|muy poco|nada|un poco|si|no)\\b");
    }


    private static boolean respondioDespues(List<TurnoVoz> turnos, int indice) {
        for (int i = indice + 1; i < turnos.size(); i++) if (esRespuesta(turnos.get(i))) return true;
        return false;
    }

    public static boolean tieneMotivoConcreto(List<TurnoVoz> turnos) {
        if (turnos == null) return false;
        for (TurnoVoz turno : turnos) {
            if (!esRespuesta(turno)) continue;
            String texto = normalizar(turno.contenido());
            for (String segmento : texto.split("[,;.!?\\n]|\\bpero\\b|\\bsin embargo\\b|\\baunque\\b")) {
                if (tieneSintomaAfirmado(segmento)) return true;
            }
        }
        return false;
    }

    private static boolean tieneSintomaAfirmado(String segmento) {
        var sintoma = Pattern.compile("\\b(?:" + SINTOMAS + ")\\b").matcher(segmento);
        while (sintoma.find()) {
            String prefijo = segmento.substring(0, sintoma.start());
            var negacion = NEGACION.matcher(prefijo);
            int ultimaNegacion = -1;
            while (negacion.find()) ultimaNegacion = negacion.start();
            if (ultimaNegacion < 0 || AFIRMACION_TRAS_NEGACION.matcher(prefijo.substring(ultimaNegacion)).find()) return true;
        }
        return false;
    }

    public static boolean tieneInvitacionAbierta(List<TurnoVoz> turnos) {
        return ultimaInvitacionAbierta(turnos) >= 0;
    }

    public static boolean rechazoGlobalExplicito(List<TurnoVoz> turnos) {
        if (turnos == null) return false;
        boolean invitacionAbierta = false;
        boolean rechazoGlobal = false;
        for (TurnoVoz turno : turnos) {
            if (turno == null || turno.contenido() == null) continue;
            String texto = normalizar(turno.contenido());
            if (turno.autor() == AutorMensaje.BOT) {
                invitacionAbierta = esInvitacionAbierta(texto);
            } else if (esRespuesta(turno)) {
                rechazoGlobal |= (invitacionAbierta && esNegacionBreve(texto))
                        || contiene(texto, "no tengo (?:nada mas|mas sintomas|otros sintomas)|no hay (?:nada mas|otros sintomas)|nada mas que agregar|no quiero (?:agregar|seguir|contar)|no deseo (?:agregar|seguir|contar)");
                invitacionAbierta = false;
            }
        }
        return rechazoGlobal;
    }

    private static boolean terminarSolicitado(List<TurnoVoz> turnos) {
        return turnos != null && turnos.stream().filter(ValidadorCierreEntrevistaVoz::esRespuesta)
                .map(t -> normalizar(t.contenido()))
                .anyMatch(t -> contiene(t, "no quiero (?:seguir|continuar|responder|contestar)|no deseo (?:seguir|continuar|responder)|quiero terminar|prefiero terminar|terminemos|dejemoslo aca"));
    }

    private static int ultimaInvitacionAbierta(List<TurnoVoz> turnos) {
        int ultima = -1;
        if (turnos == null) return ultima;
        for (int i = 0; i < turnos.size(); i++) {
            TurnoVoz turno = turnos.get(i);
            if (turno != null && turno.autor() == AutorMensaje.BOT && turno.contenido() != null
                    && esInvitacionAbierta(normalizar(turno.contenido()))) ultima = i;
        }
        return ultima;
    }

    private static boolean esInvitacionAbierta(String texto) {
        return contiene(texto, "algo mas|otra cosa|algo para agregar|algo que quieras (?:contar|agregar)|queres agregar algo|quieres agregar algo");
    }

    public static String instruccionDatosPendientes(List<TurnoVoz> turnos) {
        List<TurnoVoz> historial = turnos == null ? List.of() : turnos;
        return validarSuficiencia(historial, historial.stream().filter(ValidadorCierreEntrevistaVoz::esRespuesta).count())
                .orElse("La entrevista ya tiene datos suficientes.");
    }

    private static boolean estructuraValida(ResumenEntrevistaVoz r) {
        return r != null && textoValido(r.motivoConsulta()) && textoValido(r.inicio())
                && textoValido(r.evolucion()) && textoValido(r.posibilidadEmbarazo()) && textoValido(r.observaciones())
                && listaValida(r.sintomas()) && listaValida(r.signosAlarma()) && listaValida(r.antecedentesRelevantes())
                && listaValida(r.medicamentos()) && listaValida(r.alergias())
                && (r.intensidadDolor() == null || (r.intensidadDolor() >= 0 && r.intensidadDolor() <= 10));
    }

    /** Reuses the same alarm filtering when the caller builds a conservative fallback. */
    public static List<String> signosAlarmaSignificativos(ResumenEntrevistaVoz resumen) {
        if (resumen == null || resumen.signosAlarma() == null) return List.of();
        return signosAlarmaAfirmados(resumen.signosAlarma());
    }

    public static List<String> signosAlarmaAfirmados(List<String> signos) {
        if (signos == null) return List.of();
        return signos.stream().filter(ValidadorCierreEntrevistaVoz::textoValido)
                .filter(signo -> alarmaResumenSignificativa(normalizar(signo))).toList();
    }

    /** The same literal evidence must authorize early closure and guard its classification. */
    public static boolean contieneAlarmaLiteral(List<TurnoVoz> historial) {
        return historial != null && historial.stream().filter(ValidadorCierreEntrevistaVoz::esRespuesta)
                .anyMatch(turno -> alarmaAfirmada(normalizar(turno.contenido())));
    }

    /** Latest explicit pain rating, including a numeric answer to the preceding scale question. */
    public static Integer ultimaIntensidadLiteral(List<TurnoVoz> historial) {
        if (historial == null) return null;
        Integer ultima = null;
        String pregunta = "";
        for (TurnoVoz turno : historial) {
            if (turno == null || turno.contenido() == null) continue;
            String texto = normalizar(turno.contenido());
            if (turno.autor() == AutorMensaje.BOT) {
                pregunta = texto;
            } else if (esRespuesta(turno)) {
                Integer intensidad = extraerIntensidad(texto, pregunta);
                if (intensidad != null) ultima = intensidad;
                pregunta = "";
            }
        }
        return ultima;
    }

    private static Integer extraerIntensidad(String texto, String pregunta) {
        var matcher = INTENSIDAD.matcher(texto);
        Integer ultima = null;
        while (matcher.find()) ultima = Integer.parseInt(matcher.group(1));
        if (ultima != null) return ultima;
        String respuesta = texto.replaceFirst("^(?:un|una)\\s+", "").replaceAll("[.!]$", "").strip();
        if (contiene(pregunta, "intensidad|(?:0|1|cero|uno) (?:al|a) (?:10|diez)")) {
            if (respuesta.matches("10|[0-9]")) return Integer.parseInt(respuesta);
            List<String> numeros = List.of("cero", "uno", "dos", "tres", "cuatro", "cinco",
                    "seis", "siete", "ocho", "nueve", "diez");
            int indice = numeros.indexOf(respuesta);
            if (indice >= 0) return indice;
        }
        return null;
    }

    private static boolean esRespuesta(TurnoVoz turno) {
        return turno != null && turno.autor() == AutorMensaje.PACIENTE && textoValido(turno.contenido());
    }

    private static boolean textoValido(String texto) { return texto != null && !texto.isBlank(); }
    private static boolean listaValida(List<String> lista) {
        return lista != null && lista.stream().allMatch(ValidadorCierreEntrevistaVoz::textoValido);
    }
    private static boolean contiene(String texto, String alternativas) {
        return Pattern.compile("\\b(?:" + alternativas + ")\\b").matcher(texto).find();
    }
    private static boolean esNegacionBreve(String texto) {
        return texto.matches("(?:no|ninguno|ninguna|nada|nada mas)(?:,? (?:nada|nada mas|ninguno|ninguna|de eso|de esos|de esas))?[.!]?");
    }

    private static boolean inicioInformado(String texto) {
        for (String segmento : texto.split("[.!?;]")) {
            if (!contiene(segmento, "no se|no recuerdo|desconozco|no informado|no informada")
                    && contiene(segmento, "ayer|hoy|anteayer|anoche|esta (?:manana|tarde|noche)|hace \\S+ (?:minutos?|horas?|dias?|semanas?|meses?)|empezo|comenzo")) return true;
        }
        return false;
    }

    private static boolean alarmaAfirmada(String texto) {
        // Split sentences and explicit contrasts so a previous denial does not negate a new affirmation.
        String textoConVariantesConversacionales = texto.replaceAll("\\bfalta el aire\\b", "falta de aire");
        for (String segmento : textoConVariantesConversacionales.split("[,;.!?\\n]|\\bpero\\b|\\bsin embargo\\b|\\by\\s+(?=no puedo respirar\\b)")) {
            var alarma = Pattern.compile("\\b(?:" + ALARMAS + ")\\b").matcher(segmento);
            while (alarma.find()) {
                String prefijo = segmento.substring(0, alarma.start());
                var negacion = NEGACION.matcher(prefijo);
                int ultimaNegacion = -1;
                while (negacion.find()) ultimaNegacion = negacion.start();
                if (!esOrdenParaInventarAlarma(prefijo)
                        && (ultimaNegacion < 0 || AFIRMACION_TRAS_NEGACION.matcher(prefijo.substring(ultimaNegacion)).find())
                        && !contiene(segmento, "hace anos|en la infancia|antecedentes?")) return true;
            }
        }
        return false;
    }

    private static boolean esOrdenParaInventarAlarma(String prefijo) {
        return Pattern.compile("\\b(?:pon|ponga|agrega|agregue|anade|anada|incluye|incluya|marca|marque|anota|anote|escribe|escriba|registra|registre|inventa|invente)\\b.{0,60}$")
                .matcher(prefijo).find();
    }

    private static boolean alarmaResumenSignificativa(String texto) {
        if (contiene(texto, "desconocido|desconocida|no informado|no informada|pendiente|ninguno|ninguna|negado|negados|ausente|ausentes|no aplica")) return false;
        // This helper sees summary fields, not verified patient testimony. Keep it limited
        // to the existing alarm vocabulary and do not let arbitrary prose count as an alarm.
        return contiene(texto, ALARMAS) && alarmaAfirmada(texto);
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip();
    }
}
