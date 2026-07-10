package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.HospitalNoEncontradoException;
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
import java.util.NoSuchElementException;

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

        this.notificarCambio(consulta, gestor);
        repoGestorDeCola.save(gestor);//update cola dinámica
    }

    @Transactional
    public void sacarDeLaColaDelHospital(ConsultaMedica consulta){
        GestorDeCola gestorDeCola = this.obtenerOCrearColaDeConsulta(consulta);

        gestorDeCola.sacarConsultaMedicaDeLaCola(consulta);

        this.notificarCambio(consulta, gestorDeCola);
        repoGestorDeCola.save(gestorDeCola);//update cola dinámica
    }

    @Transactional
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
    private void notificarCambio(ConsultaMedica consulta, GestorDeCola gestorDeCola){
        Long hospitalId = consulta.getHospital().getId();

        List<AtencionMedica> atenciones = salaService.obtenerAtencionesMedicasActuales(hospitalId);

        List<TiempoEstimadoAtencionResponse> nuevosTiempos = tiempoService.calcularTodos(
                gestorDeCola,
                atenciones
        );

        publisher.publishEvent(
                new ColaModificadaEvent(
                        nuevosTiempos
                )
        );
    }

    public GestorDeCola obtenerColaDe(Hospital hospital) {
        Long idHospital = hospital.getId();
        return repoGestorDeCola.findByHospitalId(idHospital)
                .orElseThrow(() -> new NoSuchElementException("No existe el gestor de cola para el hospital con id "+ idHospital));
    }
}
