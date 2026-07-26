package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.model.consultas.*;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEntradasCola;
import com.pretriage.backend.repositories.RepoGestoresDeColas;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class IngresoColaService {
    private final RepoConsultasMedicas repoConsultasMedicas;
    private final RepoGestoresDeColas repoGestoresDeColas;
    private final RepoEntradasCola repoEntradasCola;

    private final EstimacionAtencionService estimacionAtencionService;

    @Transactional
    public TiempoEstimadoAtencionResponse ingresar(ConsultaMedica consulta, NivelDeGravedad prioridad) {
        if (consulta.getHospital() == null || consulta.getEspecialidad() == null) {
            throw new NoSuchElementException("La consulta debe tener hospital y especialidad");
        }
        consulta.setNivelDeGravedadBot(prioridad);
        consulta.setEstadoConsulta(EstadoConsulta.EN_COLA);
        repoConsultasMedicas.save(consulta);
        GestorDeCola gestor = repoGestoresDeColas
                .findByHospitalIdAndEspecialidadId(consulta.getHospital().getId(), consulta.getEspecialidad().getId())
                .orElseGet(() -> {
                    GestorDeCola nuevo = new GestorDeCola();
                    nuevo.setHospital(consulta.getHospital());
                    nuevo.setEspecialidad(consulta.getEspecialidad());
                    return repoGestoresDeColas.save(nuevo);
                });
        gestor.agregarConsultaMedicaALaCola(consulta);
        repoGestoresDeColas.save(gestor);
        repoEntradasCola.findByConsultaMedicaId(consulta.getId()).orElseGet(() -> {
            EntradaCola entrada = new EntradaCola();
            entrada.setGestorDeCola(gestor);
            entrada.setConsultaMedica(consulta);
            entrada.setEstado(EstadoEntradaCola.EN_COLA);
            entrada.setPrioridad(gestor.obtenerPrioridad(prioridad));
            entrada.setOrdenRelativo(repoEntradasCola.findFirstByGestorDeColaIdOrderByOrdenRelativoDesc(gestor.getId())
                    .map(actual -> actual.getOrdenRelativo() + 1).orElse(1L));
            entrada.setFechaHoraIngreso(LocalDateTime.now());
            return repoEntradasCola.save(entrada);
        });
        return estimacionAtencionService.calcularPara(consulta);
    }
}
