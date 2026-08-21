package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.NoSePudoEstimarElHorarioDeAtencion;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.model.consultas.EstadoSesionMedica;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.repositories.RepoEntradasCola;
import com.pretriage.backend.repositories.RepoSesionesAtencionMedica;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EstimacionAtencionService {

    private static final String MENSAJE_SIN_MEDICOS_ACTIVOS = "No hay medicos atendiendo esta especialidad en este momento. La hora es una estimacion tentativa.";

    private final RepoEntradasCola repoEntradasCola;
    private final RepoSesionesAtencionMedica repoSesionesAtencionMedica;

    @Value("${pretriage.estimacion.minutos-promedio-atencion:10}")
    private int minutosPromedioAtencion;

    public TiempoEstimadoAtencionResponse calcularPara(ConsultaMedica consultaMedica) {
        EntradaCola entradaPaciente = repoEntradasCola.findByConsultaMedicaId(consultaMedica.getId())
                .filter(entrada -> entrada.getEstado() == EstadoEntradaCola.EN_COLA)
                .orElseThrow(NoSePudoEstimarElHorarioDeAtencion::new);

        List<EntradaCola> cola = repoEntradasCola
                .findByGestorDeColaIdAndEstadoOrderByPrioridadDescOrdenRelativoAscFechaHoraIngresoAsc(
                        entradaPaciente.getGestorDeCola().getId(),
                        EstadoEntradaCola.EN_COLA);

        int posicionBaseCero = buscarPosicion(cola, entradaPaciente);
        if (posicionBaseCero < 0) {
            throw new NoSePudoEstimarElHorarioDeAtencion();
        }

        int medicosActivos = repoSesionesAtencionMedica.countByHospitalIdAndEspecialidadIdAndEstado(
                consultaMedica.getHospital().getId(),
                consultaMedica.getEspecialidad().getId(),
                EstadoSesionMedica.ACTIVA);
        int medicosParaEstimacion = Math.max(medicosActivos, 1);
        int bloquesEspera = posicionBaseCero / medicosParaEstimacion;

        TiempoEstimadoAtencionResponse response = new TiempoEstimadoAtencionResponse();
        response.setConsultaId(consultaMedica.getId());
        response.setFechaHoraAtencionEstimada(
                LocalDateTime.now().plusMinutes((long) bloquesEspera * minutosPromedioAtencion));
        response.setHayMedicosActivos(medicosActivos > 0);
        response.setMedicosActivos(medicosActivos);
        response.setMedicosParaEstimacion(medicosParaEstimacion);
        response.setPacientesAntes(posicionBaseCero);
        response.setPosicionEnCola(posicionBaseCero + 1);
        response.setMinutosPromedioAtencion(minutosPromedioAtencion);

        Sala sala = consultaMedica.getSala(); // se hace set cuando el medico lo llama
        if (sala != null) {
            response.setCodigoSala(sala.getNombre());
        }

        if (medicosActivos == 0) {
            response.setMensaje(MENSAJE_SIN_MEDICOS_ACTIVOS);
        }
        return response;
    }

    private int buscarPosicion(List<EntradaCola> cola, EntradaCola entradaPaciente) {
        for (int i = 0; i < cola.size(); i++) {
            EntradaCola entrada = cola.get(i);
            if (entrada.getId() != null && entrada.getId().equals(entradaPaciente.getId())) {
                return i;
            }
            if (entrada.getConsultaMedica() != null
                    && entradaPaciente.getConsultaMedica() != null
                    && entrada.getConsultaMedica().getId() != null
                    && entrada.getConsultaMedica().getId().equals(entradaPaciente.getConsultaMedica().getId())) {
                return i;
            }
        }
        return -1;
    }
}
