package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.EspecialidadMedicaDTO;
import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.AtencionEnCursoException;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEntradasCola;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
import com.pretriage.backend.repositories.RepoGestoresDeColas;
import com.pretriage.backend.repositories.RepoHospitales;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AtencionHospitalService {

    private static final List<EstadoConsulta> ESTADOS_CONSULTA_ACTIVA = List.of(
            EstadoConsulta.PENDIENTE,
            EstadoConsulta.HOSPITAL_SELECCIONADO,
            EstadoConsulta.PRETRIAGE_FINALIZADO,
            EstadoConsulta.PRETRIAGE_EN_PROCESO,
            EstadoConsulta.EN_COLA,
            EstadoConsulta.LLAMADO,
            EstadoConsulta.EN_ESPERA,
            EstadoConsulta.ATRASADO,
            EstadoConsulta.EN_ATENCION);

    private static final List<EstadoConsulta> ESTADOS_CONSULTA_CON_HOSPITAL = List.of(
            EstadoConsulta.HOSPITAL_SELECCIONADO,
            EstadoConsulta.PRETRIAGE_EN_PROCESO,
            EstadoConsulta.PRETRIAGE_FINALIZADO,
            EstadoConsulta.EN_COLA,
            EstadoConsulta.LLAMADO,
            EstadoConsulta.EN_ESPERA,
            EstadoConsulta.ATRASADO,
            EstadoConsulta.EN_ATENCION);

    private final RepoConsultasMedicas repoConsultasMedicas;
    private final RepoHospitales repoHospitales;
    private final RepoGestoresDeColas repoGestorDeCola;
    private final RepoEntradasCola repoEntradasCola;
    private final RepoEspecialidadesMedicas repoEspecialidadesMedicas;

    private final EstimacionAtencionService estimacionAtencionService;

    private final PacienteService pacienteService;
    private final GooglePlacesService googlePlacesService;

    public List<HospitalCercanoDTO> buscarHospitalesCercanos(Double latitud, Double longitud, String codigoEspecialidad) {
        EspecialidadMedica especialidad = obtenerEspecialidad(codigoEspecialidad);
        List<HospitalCercanoDTO> hospitalesCercanos = googlePlacesService.buscarHospitales(latitud, longitud);
        List<String> placeIds = hospitalesCercanos.stream()
                .map(HospitalCercanoDTO::getPlaceId)
                .toList();

        if (placeIds.isEmpty()) {
            return List.of();
        }

        Map<String, Hospital> hospitalesPorPlaceId = repoHospitales
                .findByPlaceIdInAndEspecialidadesCodigo(placeIds, especialidad.getCodigo())
                .stream()
                .collect(Collectors.toMap(Hospital::getPlaceId, Function.identity()));

        return hospitalesCercanos.stream()
                .filter(hospitalCercano -> hospitalesPorPlaceId.containsKey(hospitalCercano.getPlaceId()))
                .map(hospitalCercano -> completarEspecialidades(hospitalCercano, hospitalesPorPlaceId.get(hospitalCercano.getPlaceId())))
                .toList();
    }

    @Transactional
    public void seleccionarHospital(String auth0Id, String placeId, String codigoEspecialidad) {
        Paciente paciente = this.obtenerPaciente(auth0Id);
        EspecialidadMedica especialidad = obtenerEspecialidad(codigoEspecialidad);
        ConsultaMedica consultaMedica = obtenerOCrearConsultaParaSeleccionarHospital(paciente);

        Optional<Hospital> opHospital = repoHospitales.findByPlaceId(placeId);
        Hospital hospital;

        if(opHospital.isEmpty()){
            hospital= googlePlacesService.obtenerHospitalDesdeGoogle(placeId);
            if (hospital == null) {
                throw new NoSuchElementException("Hospital inexistente");
            }
            repoHospitales.save(hospital);
        } else {
            hospital = opHospital.get();
        }

        validarHospitalAtiendeEspecialidad(hospital, especialidad);

        consultaMedica.setHospital(hospital);
        consultaMedica.setEspecialidad(especialidad);
        consultaMedica.setEstadoConsulta(EstadoConsulta.HOSPITAL_SELECCIONADO);
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
        consultaMedica.setEstadoConsulta(EstadoConsulta.EN_COLA);
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
        return estimacionAtencionService.calcularPara(consultaMedica);
    }

    private Paciente obtenerPaciente(String auth0Id){
        Optional<Paciente> opPaciente = pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id);

        if(opPaciente.isEmpty()){
            throw  new AccessDeniedException("No tiene permisos para seleccionar el hospital de otro paciente");
        }

        return opPaciente.get();
    }

    @Transactional
    private void ingresarALaColaDelHospital(ConsultaMedica consultaMedica){
        GestorDeCola gestorDeCola= this.obtenerOCrearColaDeConsulta(consultaMedica);
        gestorDeCola.agregarConsultaMedicaALaCola(consultaMedica);
        repoGestorDeCola.save(gestorDeCola);
        this.obtenerOCrearEntradaCola(gestorDeCola, consultaMedica);
    }

    private GestorDeCola obtenerOCrearColaDeConsulta(ConsultaMedica consultaMedica){
        Hospital hospital = consultaMedica.getHospital();
        EspecialidadMedica especialidad = consultaMedica.getEspecialidad();

        if (hospital == null || especialidad == null) {
            throw new NoSuchElementException("Se debe seleccionar hospital y especialidad antes de ingresar a la cola");
        }

        return repoGestorDeCola.findByHospitalIdAndEspecialidadId(hospital.getId(), especialidad.getId())
                .orElseGet(()->{
                    GestorDeCola gestorDeColaNuevo = new GestorDeCola();
                    gestorDeColaNuevo.setHospital(hospital);
                    gestorDeColaNuevo.setEspecialidad(especialidad);
                    repoGestorDeCola.save(gestorDeColaNuevo);
                    return gestorDeColaNuevo;
                });
    }

    private EntradaCola obtenerOCrearEntradaCola(GestorDeCola gestorDeCola, ConsultaMedica consultaMedica) {
        return repoEntradasCola.findByConsultaMedicaId(consultaMedica.getId())
                .orElseGet(() -> {
                    EntradaCola entrada = new EntradaCola();
                    entrada.setGestorDeCola(gestorDeCola);
                    entrada.setConsultaMedica(consultaMedica);
                    entrada.setEstado(EstadoEntradaCola.EN_COLA);
                    entrada.setPrioridad(gestorDeCola.obtenerPrioridad(consultaMedica.getNivelDeGravedadBot()));
                    entrada.setOrdenRelativo(obtenerSiguienteOrdenRelativo(gestorDeCola));
                    entrada.setFechaHoraIngreso(LocalDateTime.now());
                    return repoEntradasCola.save(entrada);
                });
    }

    private long obtenerSiguienteOrdenRelativo(GestorDeCola gestorDeCola) {
        return repoEntradasCola.findFirstByGestorDeColaIdOrderByOrdenRelativoDesc(gestorDeCola.getId())
                .map(entrada -> entrada.getOrdenRelativo() + 1)
                .orElse(1L);
    }

    private EspecialidadMedica obtenerEspecialidad(String codigoEspecialidad) {
        return repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)
                .orElseThrow(() -> new NoSuchElementException("Especialidad medica inexistente"));
    }

    private void validarHospitalAtiendeEspecialidad(Hospital hospital, EspecialidadMedica especialidad) {
        boolean atiendeEspecialidad = hospital.getEspecialidades().stream()
                .anyMatch(especialidadHospital -> especialidadHospital.getCodigo().equals(especialidad.getCodigo()));

        if (!atiendeEspecialidad) {
            throw new NoSuchElementException("El hospital no atiende la especialidad seleccionada");
        }
    }

    private HospitalCercanoDTO completarEspecialidades(HospitalCercanoDTO hospitalCercano, Hospital hospital) {
        hospitalCercano.setEspecialidades(hospital.getEspecialidades().stream()
                .map(this::mapearEspecialidadADTO)
                .toList());
        return hospitalCercano;
    }

    private EspecialidadMedicaDTO mapearEspecialidadADTO(EspecialidadMedica especialidad) {
        EspecialidadMedicaDTO dto = new EspecialidadMedicaDTO();
        dto.setCodigo(especialidad.getCodigo());
        dto.setNombre(especialidad.getNombre());
        return dto;
    }
}

