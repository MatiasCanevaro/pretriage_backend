package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TriageAiResponse;
import com.pretriage.backend.controllers.dtos.TriageResultDTO;
import com.pretriage.backend.model.chat.AutorMensaje;
import com.pretriage.backend.model.chat.Chat;
import com.pretriage.backend.model.chat.EstadoClasificacionChat;
import com.pretriage.backend.model.chat.Mensaje;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Conversation regressions: quantify the reported complaint without a symptom checklist. */
class ChatCuantificacionFlowTest {
    private final RepoChat chats = mock(RepoChat.class);
    private final TriageIaClient provider = mock(TriageIaClient.class);
    private final AtencionHospitalService queue = mock(AtencionHospitalService.class);
    private final Chat chat = new Chat(new Paciente());
    private final ChatService service = new ChatService(chats, mock(RepoPacientes.class),
            mock(RepoConsultasMedicas.class), mock(RepoEntradasCola.class), queue, provider, new ObjectMapper());

    private void prepare() {
        chat.setId(91L);
        chat.agregarMensaje(new Mensaje("¿Cuál es el principal motivo de tu consulta?", AutorMensaje.BOT, null));
        when(chats.findByIdAndPacienteUsuarioAuthId(91L, "synthetic")).thenReturn(Optional.of(chat));
        when(provider.consultar(anyString(), anyString(), eq(true))).thenReturn(classification());
    }

    @Test
    void negarOtrosSintomasNoEvitaCuantificarLosYaInformados() {
        prepare();
        chat.agregarMensaje(new Mensaje("Me duele la cabeza y tengo fiebre", AutorMensaje.PACIENTE, chat.getPaciente()));
        chat.agregarMensaje(new Mensaje("¿Hay algo más que quieras contar?", AutorMensaje.BOT, null));
        when(provider.consultarContinuacion(anyString(), anyString(), any()))
                .thenReturn(question("¿Desde cuándo tenés esos síntomas?"),
                        question("Del 0 al 10, ¿cuánto te duele la cabeza?"),
                        question("¿Qué temperatura marcó el termómetro?"));

        send("No tengo otros síntomas");
        assertFalse(chat.isFinalizado(), "Negar otros síntomas no aporta duración ni intensidad");
        send("Los dos empezaron ayer");
        assertFalse(chat.isFinalizado(), "Sigue pendiente la afectación");
        send("8 de 10");
        assertFalse(chat.isFinalizado(), "La escala de dolor no mide la fiebre");
        send("No me medí la temperatura");

        assertTrue(chat.isFinalizado());
        assertEquals(EstadoClasificacionChat.COMPLETADA, chat.getEstadoClasificacion());
        verify(provider, times(3)).consultarContinuacion(anyString(), anyString(), any());
        verify(provider, times(1)).consultar(anyString(), anyString(), eq(true));
        verify(queue, times(1)).finalizarTriageEIngresarACola(eq("synthetic"), any(), anyString(), same(chat));
        assertEquals(1, chat.getMensajes().stream().filter(message -> message.getContenido()
                .equals("¿Hay algo más que quieras contar?")).count(), "No repetir la invitación");
    }

    @Test
    void rechazoExplicitoDeContinuarCierraSinForzarCuantificacion() {
        prepare();
        chat.agregarMensaje(new Mensaje("Me duele la cabeza y tengo fiebre", AutorMensaje.PACIENTE, chat.getPaciente()));

        send("No quiero seguir respondiendo");

        assertTrue(chat.isFinalizado());
        verify(provider, never()).consultarContinuacion(anyString(), anyString(), any());
        verify(provider, times(1)).consultar(anyString(), anyString(), eq(true));
        assertEquals(2, chat.getMensajes().stream().filter(m -> m.getAutor() == AutorMensaje.PACIENTE).count());
    }

    @Test
    void inicioDeLaCefaleaNoSustituyeElInicioDeLaFiebre() {
        prepare();
        when(provider.consultarContinuacion(anyString(), anyString(), any()))
                .thenReturn(question("¿Desde cuándo tenés fiebre?"));

        send("Me duele la cabeza desde ayer, 5 de 10. También tengo fiebre de 39 grados. No tengo otros síntomas.");

        assertFalse(chat.isFinalizado(), "El inicio de la fiebre sigue desconocido");
        verify(provider, never()).consultar(anyString(), anyString(), eq(true));
        verifyNoInteractions(queue);
        send("La fiebre empezó hoy");
        assertTrue(chat.isFinalizado());
        verify(provider, times(1)).consultarContinuacion(anyString(), anyString(), any());
        verify(queue, times(1)).finalizarTriageEIngresarACola(eq("synthetic"), any(), anyString(), same(chat));
    }

    private void send(String content) { service.enviarMensaje("91", "synthetic", content); }
    private TriageAiResponse question(String message) { return new TriageAiResponse(false, message, null); }
    private TriageAiResponse classification() {
        return new TriageAiResponse(true, "Gracias. Ya registré tus respuestas para el equipo médico.",
                new TriageResultDTO("dolor de cabeza y fiebre", List.of("dolor de cabeza", "fiebre"),
                        "no informado", "no informado", null, List.of(), List.of(), List.of(), List.of(),
                        "no informado", "", 3, false, "Consultá al equipo médico para la evaluación presencial."));
    }
}
