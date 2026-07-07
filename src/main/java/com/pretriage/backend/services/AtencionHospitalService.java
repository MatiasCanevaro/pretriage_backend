package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.NoSePudoEstimarElHorarioDeAtencion;
import com.pretriage.backend.exceptions.NoSePudoObtenerHospital;
import com.pretriage.backend.model.consultas.*;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoGestoresDeColas;
import com.pretriage.backend.repositories.RepoHospitales;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AtencionHospitalService {

    private final RepoConsultasMedicas repoConsultasMedicas;
    private final RepoHospitales repoHospitales;
    private final RepoGestoresDeColas repoGestorDeCola;

    private final PacienteService pacienteService;
    private final GooglePlacesService googlePlacesService;
    private final SalaService salaService;

    @Transactional
    public void seleccionarHospital(
            String auth0Id,
            String placeId) {

        Paciente paciente = this.obtenerPaciente(auth0Id);
        Long pacienteId = paciente.getId();

        Optional<ConsultaMedica> opConsultaMedica =
                repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(pacienteId, EstadoConsulta.PENDIENTE);

        ConsultaMedica consultaMedica;
        consultaMedica = opConsultaMedica.orElseGet(
                () -> repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(pacienteId, EstadoConsulta.HOSPITAL_SELECCIONADO)
                .orElseGet(() -> {
                    ConsultaMedica consultaMedicaNueva = new ConsultaMedica();
                    consultaMedicaNueva.setPaciente(paciente);
                    consultaMedicaNueva.setEstadoConsulta(EstadoConsulta.PENDIENTE);
                    return consultaMedicaNueva;
                }));


        Optional<Hospital> opHospital = repoHospitales.findByPlaceId(placeId);
        Hospital hospital;

        if(opHospital.isEmpty()){//evito llamar la api si ya lo tengo en la db dado que lo selecciono otro paciente
            hospital= googlePlacesService.obtenerHospitalDesdeGoogle(placeId);

            if(hospital == null){
                throw new NoSePudoObtenerHospital();
            }else{
                repoHospitales.save(hospital);// solo guardo los seleccionados por los pacientes.
            }
        } else {
            hospital = opHospital.get();
        }
        if(consultaMedica.getEstadoConsulta().equals(EstadoConsulta.HOSPITAL_SELECCIONADO)){//si se quiere cambiar el hospital seleccionado
            this.sacarDeLaColaDelHospital(consultaMedica); //lo saco de la cola anterior
        }
        consultaMedica.setHospital(hospital);

        consultaMedica.setEstadoConsulta(
                EstadoConsulta.HOSPITAL_SELECCIONADO);

        repoConsultasMedicas.save(consultaMedica);

        this.ingresarALaColaDelHospital(consultaMedica);
    }

    @Transactional
    public TiempoEstimadoAtencionResponse obtenerTiempoEstimadoDeAtencion(String auth0Id){
        Paciente paciente = this.obtenerPaciente(auth0Id);

        Optional<ConsultaMedica> opConsultaMedica =
                repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(paciente.getId(), EstadoConsulta.HOSPITAL_SELECCIONADO);

        if(opConsultaMedica.isEmpty()){ //no deberia suceder nuca de todas maneras
            throw new NoSuchElementException("Se debe seleccionar primero un hospital para estimar su tiempo de atención");
        }
        ConsultaMedica consultaMedica = opConsultaMedica.get();

        GestorDeCola gestorDeCola= this.obtenerOCrearColaDeConsulta(consultaMedica);

        List<AtencionMedica> atencionesMedicasActuales = salaService.obtenerAtencionesMedicasActuales();

        List<LocalDateTime> rangoTiempoEstimadoAtencion = gestorDeCola.calcularTiempoDeAtencionPara(
                consultaMedica,
                atencionesMedicasActuales);

        if(rangoTiempoEstimadoAtencion.isEmpty()){
            throw new NoSePudoEstimarElHorarioDeAtencion();
        }

        TiempoEstimadoAtencionResponse response = new TiempoEstimadoAtencionResponse();
        response.setFechaHoraAtencionEstimadaDesde(rangoTiempoEstimadoAtencion.getFirst());
        response.setFechaHoraAtencionEstimadaHasta(rangoTiempoEstimadoAtencion.getLast());

        return response;
    }

    private Paciente obtenerPaciente(String auth0Id){
        Optional<Paciente> opPaciente = pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id);

        if(opPaciente.isEmpty()){
            throw  new AccessDeniedException(
                    "No tiene permisos para seleccionar el hospital de otro paciente");
        }

        return opPaciente.get();
    }

    @Transactional
    private void ingresarALaColaDelHospital(ConsultaMedica consultaMedica){
        GestorDeCola gestorDeCola= this.obtenerOCrearColaDeConsulta(consultaMedica);

        gestorDeCola.agregarConsultaMedicaALaCola(consultaMedica);
        repoGestorDeCola.save(gestorDeCola);//update cola dinámica
    }

    private GestorDeCola obtenerOCrearColaDeConsulta(ConsultaMedica consultaMedica){
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
    private void sacarDeLaColaDelHospital(ConsultaMedica consultaMedica){
        GestorDeCola gestorDeCola = this.obtenerOCrearColaDeConsulta(consultaMedica);

        gestorDeCola.sacarConsultaMedicaDeLaCola(consultaMedica);
        repoGestorDeCola.save(gestorDeCola);//update cola dinámica
    }
}
