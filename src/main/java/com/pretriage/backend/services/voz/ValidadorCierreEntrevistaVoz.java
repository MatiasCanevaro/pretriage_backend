package com.pretriage.backend.services.voz;

import com.pretriage.backend.controllers.dtos.ResumenEntrevistaVoz;
import com.pretriage.backend.controllers.dtos.TurnoVoz;
import com.pretriage.backend.model.chat.AutorMensaje;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Checks closure eligibility without trusting Gemini's summary as patient testimony. */
public final class ValidadorCierreEntrevistaVoz {
    private static final int MAX_RESPUESTAS = 12;
    private static final String SINTOMAS = "dolor|duele|duelen|cefalea|molestia|fiebre|tos|vomitos?|nauseas?|mareos?|sangrado|diarrea|debilidad";
    private static final String ALARMAS = "dificultad para respirar|falta de aire|dolor de pecho|opresion toracica|disnea|perdida de (?:conciencia|conocimiento)|desmayos?|confusion|convulsion(?:es)?|sangrado abundante|reaccion alergica";
    private static final String CONTEXTO = "antecedentes|enfermedad(?:es)?|medicacion|medicamentos?|alergias?|embarazo|no tomo";
    private static final String EVOLUCION = "empeor\\w*|mejor\\w*|no baja|se mantiene|igual|sin cambios|fuerte|leve|moderado|intenso";
    private static final Pattern INTENSIDAD = Pattern.compile("\\b(10|[0-9])\\s*(?:/|de|sobre)\\s*10\\b");
    private static final Pattern NEGACION = Pattern.compile("\\b(?:no|sin|niega|niego|descarta|descarto|ningun|ninguna|ninguno)\\b");

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
        boolean alarmaResumen = !signosAlarmaSignificativos(resumen).isEmpty();
        if (alarma || alarmaResumen || respuestas >= MAX_RESPUESTAS) {
            return Optional.empty();
        }
        if (respuestas < 3) {
            return Optional.of("Continua la entrevista: aun faltan respuestas del paciente. Confirma inicio, gravedad o evolucion, signos de alarma y antecedentes antes de cerrar.");
        }
        boolean sintomas = false, inicio = false, gravedad = false, alarmasExploradas = false, contexto = false;
        String pregunta = "";
        for (TurnoVoz turno : turnos) {
            if (turno == null || turno.contenido() == null) continue;
            String texto = normalizar(turno.contenido());
            if (turno.autor() == AutorMensaje.BOT) {
                pregunta = texto;
            } else if (esRespuesta(turno)) {
                sintomas |= contiene(texto, SINTOMAS);
                inicio |= inicioInformado(texto);
                gravedad |= extraerIntensidad(texto, pregunta) != null || contiene(texto, EVOLUCION)
                        || contiene(texto, "39(?:[.,]\\d+)?");
                alarmasExploradas |= contiene(texto, ALARMAS + "|sangrado|empeoramiento importante")
                        || (contiene(pregunta, ALARMAS + "|signos de alarma") && esNegacionBreve(texto));
                contexto |= contiene(texto, CONTEXTO)
                        || (contiene(pregunta, CONTEXTO) && esNegacionBreve(texto));
                pregunta = "";
            }
        }
        if (sintomas && inicio && gravedad && alarmasExploradas && contexto) return Optional.empty();
        return Optional.of("Continua la entrevista y confirma con el paciente los datos pendientes: "
                + (!sintomas ? "motivo y sintomas; " : "")
                + (!inicio ? "inicio; " : "")
                + (!gravedad ? "gravedad o evolucion; " : "")
                + (!alarmasExploradas ? "signos de alarma; " : "")
                + (!contexto ? "antecedentes, medicacion y alergias; " : "")
                + "no completes datos por inferencia ni cierres todavia.");
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
        return resumen.signosAlarma().stream().filter(ValidadorCierreEntrevistaVoz::textoValido)
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
        var respuestaNumerica = Pattern.compile("^(10|[0-9])[.!]?$").matcher(texto);
        if (contiene(pregunta, "intensidad|del 0 al 10|de 0 a 10") && respuestaNumerica.matches()) {
            return Integer.parseInt(respuestaNumerica.group(1));
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
        return texto.matches("(?:no|ninguno|ninguna|nada)(?:,? (?:nada|ninguno|ninguna|de eso|de esos|de esas))?[.!]?");
    }

    private static boolean inicioInformado(String texto) {
        for (String segmento : texto.split("[.!?;]")) {
            if (!contiene(segmento, "no se|no recuerdo|desconozco|no informado|no informada")
                    && contiene(segmento, "ayer|hoy|anteayer|hace \\S+ (?:minutos?|horas?|dias?|semanas?|meses?)|empezo|comenzo")) return true;
        }
        return false;
    }

    private static boolean alarmaAfirmada(String texto) {
        // Split sentences and explicit contrasts so a previous denial does not negate a new affirmation.
        for (String segmento : texto.split("[.!?;\\n]|\\bpero\\b|\\bsin embargo\\b")) {
            var alarma = Pattern.compile("\\b(?:" + ALARMAS + ")\\b").matcher(segmento);
            while (alarma.find()) {
                String prefijo = segmento.substring(0, alarma.start());
                if (!NEGACION.matcher(prefijo).find() && !contiene(segmento, "hace anos|en la infancia|antecedentes?")) return true;
            }
        }
        return false;
    }

    private static boolean alarmaResumenSignificativa(String texto) {
        if (NEGACION.matcher(texto).find() || contiene(texto, "desconocido|desconocida|no informado|no informada|pendiente|ninguno|ninguna|negado|negados|ausente|ausentes|no aplica")) return false;
        // A mere placeholder such as 'alarma' is not evidence of an emergency.
        return contiene(texto, ALARMAS)
                || (texto.split("\\s+").length >= 2 && !texto.matches("(?:signos? de )?alarma(?:s)?(?: presentes?)?"));
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip();
    }
}
