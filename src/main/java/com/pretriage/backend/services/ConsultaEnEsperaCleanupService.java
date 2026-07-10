package com.pretriage.backend.services;

import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultaEnEsperaCleanupService {

    private final RepoConsultasMedicas repoConsultasMedicas;


    @Value("${tiempo.limite.en-espera.segundos}")
    private int tiempoLimiteEnEsperaSegundos;

    @Scheduled(fixedRate = 300000) // Ejecuta cada 5 minutos (en milisegundos)
    @Transactional
    public void limpiarConsultasVencidas() {
        LocalDateTime limite = LocalDateTime.now().minusSeconds(tiempoLimiteEnEsperaSegundos);

        // Buscar consultas en EN_ESPERA que superaron el tiempo límite
        List<ConsultaMedica> consultasVencidas = repoConsultasMedicas
                .findAllByEstadoConsultaAndFechaHoraPuestaEnEsperaBefore(EstadoConsulta.EN_ESPERA, limite);

        consultasVencidas.forEach(consulta ->{
            log.warn("La consulta {} del paciente con id {} se pasa a estado no asistió", consulta.getId(), consulta.getPaciente().getId());
            consulta.setEstadoConsulta(EstadoConsulta.PACIENTE_NO_ASISTIO);
        });

        repoConsultasMedicas.saveAll(consultasVencidas);
    }
}
