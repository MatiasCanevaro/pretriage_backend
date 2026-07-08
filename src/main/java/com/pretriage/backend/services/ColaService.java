package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.model.consultas.AtencionMedica;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.colaDinamica.ColaModificadaEvent;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.repositories.RepoGestoresDeColas;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ColaService {

    private final ApplicationEventPublisher publisher;

    private final RepoGestoresDeColas repoGestorDeCola;

    private final SalaService salaService;
    private final TiempoEstimadoService tiempoService;

    @Transactional
    public void agregarConsulta(ConsultaMedica consulta){

        GestorDeCola gestor = this.obtenerOCrearColaDeConsulta(consulta);
        gestor.agregarConsultaMedicaALaCola(consulta);

        gestor.agregarConsultaMedicaALaCola(consulta);

        this.notificarCambio(consulta);
        repoGestorDeCola.save(gestor);//update cola dinámica
    }

    @Transactional
    public void sacarDeLaColaDelHospital(ConsultaMedica consulta){
        GestorDeCola gestorDeCola = this.obtenerOCrearColaDeConsulta(consulta);

        gestorDeCola.sacarConsultaMedicaDeLaCola(consulta);

        this.notificarCambio(consulta);
        repoGestorDeCola.save(gestorDeCola);//update cola dinámica
    }

    public GestorDeCola obtenerOCrearColaDeConsulta(ConsultaMedica consultaMedica){
        Hospital hospital = consultaMedica.getHospital();

        return repoGestorDeCola.findByHospitalId(hospital.getId()) // si no existe lo creo
                .orElseGet(()->{
                    GestorDeCola gestorDeColaNuevo = new GestorDeCola();
                    gestorDeColaNuevo.setHospital(hospital);
                    gestorDeColaNuevo.agregarConsultaMedicaALaCola(consultaMedica);
                    repoGestorDeCola.save(gestorDeColaNuevo);
                    return gestorDeColaNuevo;
                });
    }

    @Transactional
    private void notificarCambio(ConsultaMedica consulta){
        Long hospitalId = consulta.getHospital().getId();

        GestorDeCola gestor =repoGestorDeCola.findByHospitalId(hospitalId).orElseThrow();

        List<AtencionMedica> atenciones = salaService.obtenerAtencionesMedicasActuales(hospitalId);

        List<TiempoEstimadoAtencionResponse> nuevosTiempos = tiempoService.calcularTodos(
                gestor,
                atenciones
        );

        publisher.publishEvent(
                new ColaModificadaEvent(
                        nuevosTiempos
                )
        );
    }
}
