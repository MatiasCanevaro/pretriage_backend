package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.AtencionEnCursoException;
import com.pretriage.backend.exceptions.NoSePudoEstimarElHorarioDeAtencion;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
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

    private static final List<EstadoConsulta> ESTADOS_CONSULTA_ACTIVA = List.of(
            EstadoConsulta.PENDIENTE,
            EstadoConsulta.HOSPITAL_SELECCIONADO,
            EstadoConsulta.PRETRIAGE_FINALIZADO,
            EstadoConsulta.PRETRIAGE_EN_PROCESO);

    private static final List<EstadoConsulta> ESTADOS_CONSULTA_CON_HOSPITAL = List.of(
            EstadoConsulta.HOSPITAL_SELECCIONADO,
            EstadoConsulta.PRETRIAGE_EN_PROCESO,
            EstadoConsulta.PRETRIAGE_FINALIZADO);

    private final RepoConsultasMedicas repoConsultasMedicas;
    private final RepoHospitales repoHospitales;
    private final RepoGestoresDeColas repoGestorDeCola;

    private final PacienteService pacienteService;
    private final GooglePlacesService googlePlacesService;

    @Transactional
    public void seleccionarHospital(
            String auth0Id,
            String placeId) {

        Paciente paciente = this.obtenerPaciente(auth0Id);

        ConsultaMedica consultaMedica = obtenerOCrearConsultaParaSeleccionarHospital(paciente);


        Optional<Hospital> opHospital = repoHospitales.findByPlaceId(placeId);
        Hospital hospital;

        if(opHospital.isEmpty()){//evito llamar la api si ya lo tengo en la db dado que lo selecciono otro paciente
            hospital= googlePlacesService.obtenerHospitalDesdeGoogle(placeId);
            repoHospitales.save(hospital);// solo guardo los seleccionados por los pacientes.
        } else {
            hospital = opHospital.get();
        }

        consultaMedica.setHospital(hospital);

        consultaMedica.setEstadoConsulta(
                EstadoConsulta.HOSPITAL_SELECCIONADO);

        repoConsultasMedicas.save(consultaMedica);

    }
    private ConsultaMedica obtenerOCrearConsultaParaSeleccionarHospital(Paciente paciente) {
        Optional<ConsultaMedica> opConsultaMedica = repoConsultasMedicas
                .findFirstByPacienteIdAndEstadoConsultaIn(paciente.getId(), ESTADOS_CONSULTA_ACTIVA);

        if (opConsultaMedica.isPresent()) {
            ConsultaMedica consultaMedica = opConsultaMedica.get();
            if (consultaMedica.getEstadoConsulta() != EstadoConsulta.PENDIENTE) {
                throw new AtencionEnCursoException();
            }
            return consultaMedica;
        }

        ConsultaMedica consultaMedicaNueva = new ConsultaMedica();
        consultaMedicaNueva.setPaciente(paciente);
        consultaMedicaNueva.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaNueva.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        return consultaMedicaNueva;
    }

    @Transactional
    public TiempoEstimadoAtencionResponse obtenerTiempoEstimadoDeAtencion(String auth0Id){
        Paciente paciente = this.obtenerPaciente(auth0Id);
        ConsultaMedica consultaMedica = obtenerConsultaConHospitalSeleccionado(paciente);

        return calcularTiempoEstimadoDeAtencion(consultaMedica);
    }

    @Transactional
    public TiempoEstimadoAtencionResponse finalizarTriageEIngresarACola(String auth0Id, NivelDeGravedad nivelDeGravedadBot) {
        Paciente paciente = this.obtenerPaciente(auth0Id);
        ConsultaMedica consultaMedica = obtenerConsultaConHospitalSeleccionado(paciente);

        consultaMedica.setNivelDeGravedadBot(nivelDeGravedadBot);
        consultaMedica.setEstadoConsulta(EstadoConsulta.PRETRIAGE_FINALIZADO);
        repoConsultasMedicas.save(consultaMedica);
        this.ingresarALaColaDelHospital(consultaMedica);

        return calcularTiempoEstimadoDeAtencion(consultaMedica);
    }

    private ConsultaMedica obtenerConsultaConHospitalSeleccionado(Paciente paciente) {
        Optional<ConsultaMedica> opConsultaMedica =
                repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(paciente.getId(), ESTADOS_CONSULTA_CON_HOSPITAL);

        if(opConsultaMedica.isEmpty()){
            throw new NoSuchElementException("Se debe seleccionar primero un hospital o finalizar el pretriage para estimar su tiempo de atencion");
        }
        return opConsultaMedica.get();
    }

    private TiempoEstimadoAtencionResponse calcularTiempoEstimadoDeAtencion(ConsultaMedica consultaMedica) {
        GestorDeCola gestorDeCola= this.obtenerOCrearColaDeConsulta(consultaMedica);

        Optional<LocalDateTime> opTiempoEstimadoDeAtencion = gestorDeCola.calcularTiempoDeAtencionPara(consultaMedica);

        if(opTiempoEstimadoDeAtencion.isEmpty()){
            throw new NoSePudoEstimarElHorarioDeAtencion();
        }
        LocalDateTime tiempoEstimadoDeAtencion = opTiempoEstimadoDeAtencion.get();

        TiempoEstimadoAtencionResponse response = new TiempoEstimadoAtencionResponse();
        response.setFechaHoraAtencionEstimada(tiempoEstimadoDeAtencion);

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
                    repoGestorDeCola.save(gestorDeColaNuevo);
                    return gestorDeColaNuevo;
                });
    }
}





