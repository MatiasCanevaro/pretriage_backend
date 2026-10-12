package com.pretriage.backend.repositories;

import com.pretriage.backend.model.chat.AutorMensaje;
import com.pretriage.backend.model.chat.Chat;
import com.pretriage.backend.model.chat.Mensaje;
import com.pretriage.backend.model.personas.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL regression: a closed interview must not prevent a new one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class ChatHistoryPersistenceTest {
    @Autowired
    private RepoChat chats;
    @Autowired
    private RepoPacientes pacientes;

    @Test
    void pacientePuedeCrearOtroChatSinPerderElHistorialDelAnterior() {
        Paciente paciente = pacientes.saveAndFlush(new Paciente());
        Chat anterior = new Chat(paciente);
        anterior.agregarMensaje(new Mensaje("Mensaje de la consulta anterior", AutorMensaje.PACIENTE, paciente));
        anterior.setFinalizado(true);
        chats.saveAndFlush(anterior);

        Chat nuevo = new Chat(paciente);
        nuevo.agregarMensaje(new Mensaje("Nuevo saludo", AutorMensaje.BOT, null));
        chats.saveAndFlush(nuevo);

        assertNotEquals(anterior.getId(), nuevo.getId());
        assertFalse(chats.findById(nuevo.getId()).orElseThrow().isFinalizado());
        Chat historial = chats.findById(anterior.getId()).orElseThrow();
        assertTrue(historial.isFinalizado());
        assertEquals("Mensaje de la consulta anterior", historial.getMensajes().getFirst().getContenido());
        assertEquals(2, chats.findAll().stream()
                .filter(chat -> chat.getPaciente().getId().equals(paciente.getId())).count());
    }
}
