package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.EsperaNuevaConsultaCalculo;
import com.pretriage.backend.controllers.dtos.EspecialidadMedicaDTO;
import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.controllers.dtos.HospitalSeleccionadoResponse;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoArriboHospitalResponse;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.AtencionEnCursoException;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
import com.pretriage.backend.model.hospitales.Direccion;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
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
    private final RepoEspecialidadesMedicas repoEspecialidadesMedicas;

    private final EstimacionAtencionService estimacionAtencionService;
    private final IngresoColaService ingresoColaService;

    private final PacienteService pacienteService;
    private final GooglePlacesService googlePlacesService;

    public List<HospitalCercanoDTO> buscarHospitalesCercanos(Double latitud, Double longitud, String codigoEspecialidad,
            String transporte, String auth0Id) {
        return buscarHospitalesCercanos(latitud, longitud, codigoEspecialidad, transporte, auth0Id, "distancia");
    }

    public List<HospitalCercanoDTO> buscarHospitalesCercanos(Double latitud, Double longitud, String codigoEspecialidad,
            String transporte, String auth0Id, String ordenarPor) {
        this.obtenerPaciente(auth0Id);// valida si es un paciente válido

        String transporteEfectivo = transporte != null ? transporte : "transporte-publico";
        String ordenarPorEfectivo = ordenarPor != null ? ordenarPor : "distancia";
        if (!ordenarPorEfectivo.equals("distancia") && !ordenarPorEfectivo.equals("tiempo-atencion")) {
            throw new IllegalArgumentException("Parametro ordenarPor invalido");
        }

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

        List<HospitalCercanoDTO> resultado = hospitalesCercanos.stream()
                .filter(hospitalCercano -> hospitalesPorPlaceId.containsKey(hospitalCercano.getPlaceId()))
                .map(hospitalCercano -> {
                    Hospital hospital = hospitalesPorPlaceId.get(hospitalCercano.getPlaceId());
                    hospitalCercano.setIdHospital(hospital.getId());

                    hospitalCercano.setTiempoEstimadoArriboMejorRuta(
                            googlePlacesService.calcularTiempoEstimadoArriboMejorRuta(
                                    hospital, transporteEfectivo, latitud, longitud));

                    EsperaNuevaConsultaCalculo espera = estimacionAtencionService.calcularEsperaParaNuevaConsulta(
                            hospital.getId(), especialidad.getId());
                    hospitalCercano.setPacientesEnCola(espera.pacientesEnCola());
                    hospitalCercano.setMinutosEsperaEstimados(espera.minutosEspera());
                    hospitalCercano.setFechaHoraAtencionEstimada(espera.fechaHoraAtencionEstimada());
                    hospitalCercano.setHayMedicosActivos(espera.hayMedicosActivos());
                    hospitalCercano.setDisponible(espera.hayMedicosActivos());

                    return completarEspecialidades(hospitalCercano, hospital);
                })
                .filter(HospitalCercanoDTO::isDisponible)
                .toList();

        if (ordenarPorEfectivo.equals("tiempo-atencion")) {
            return resultado.stream()
                    .sorted(Comparator.comparing(HospitalCercanoDTO::getMinutosEsperaEstimados,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(HospitalCercanoDTO::getTiempoEstimadoArriboMejorRuta,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(HospitalCercanoDTO::getNombre,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }

        return resultado;
    }

    @Transactional
    public void seleccionarHospital(String auth0Id, String placeId, String codigoEspecialidad) {
        Paciente paciente = this.obtenerPaciente(auth0Id);
        EspecialidadMedica especialidad = obtenerEspecialidad(codigoEspecialidad);
        ConsultaMedica consultaMedica = obtenerOCrearConsultaParaSeleccionarHospital(paciente);

        Optional<Hospital> opHospital = repoHospitales.findByPlaceId(placeId);
        Hospital hospital;

        if (opHospital.isEmpty()) {
            hospital = googlePlacesService.obtenerHospitalDesdeGoogle(placeId);
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
        ingresoColaService.ingresar(consultaMedica, NivelDeGravedad.NORMAL);
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
    public TiempoEstimadoAtencionResponse obtenerTiempoEstimadoDeAtencion(String auth0Id) {
        ConsultaMedica consultaMedica = obtenerConsultaConHospitalSeleccionado(auth0Id);
        return calcularTiempoEstimadoDeAtencion(consultaMedica);
    }

    @Transactional
    public HospitalSeleccionadoResponse obtenerHospitalSeleccionado(String auth0Id) {
        ConsultaMedica consultaMedica = obtenerConsultaConHospitalSeleccionado(auth0Id);
        Hospital hospital = consultaMedica.getHospital();

        HospitalSeleccionadoResponse response = new HospitalSeleccionadoResponse();
        response.setIdHospital(hospital.getId());
        response.setPlaceId(hospital.getPlaceId());
        response.setNombre(hospital.getNombre());
        Direccion direccion = hospital.getDireccion();
        response.setDireccion(direccion != null ? direccion.formateada() : null);
        return response;
    }

    @Transactional
    public TiempoEstimadoAtencionResponse finalizarTriageEIngresarACola(String auth0Id,
            NivelDeGravedad nivelDeGravedadBot) {
        return finalizarTriageEIngresarACola(auth0Id, nivelDeGravedadBot, null);
    }

    @Transactional
    public TiempoEstimadoAtencionResponse finalizarTriageEIngresarACola(
            String auth0Id, NivelDeGravedad nivelDeGravedadBot, String resumenPretriageJson) {
        Paciente paciente = this.obtenerPaciente(auth0Id);
        ConsultaMedica consultaMedica = obtenerConsultaConHospitalSeleccionado(paciente);
        consultaMedica.setResumenPretriageJson(resumenPretriageJson);
        repoConsultasMedicas.save(consultaMedica);
        return ingresoColaService.ingresar(consultaMedica, nivelDeGravedadBot);
    }

    @Transactional
    public List<TiempoEstimadoArriboHospitalResponse> calcularTiempoArriboHospital(
            String auth0Id, Long idHospital, String transporte, Double latitud, Double longitud) {
        this.obtenerPaciente(auth0Id);// valido que sea paciente

        Hospital hospital = this.obtenerHospital(idHospital);

        if (!googlePlacesService.esTransporteValido(transporte)) {
            throw new IllegalArgumentException("Transporte no valido");
        }

        return googlePlacesService.calcularTiempoArriboHospital(hospital, transporte, latitud, longitud);
    }

    private ConsultaMedica obtenerConsultaConHospitalSeleccionado(String auth0Id) {
        Paciente paciente = obtenerPaciente(auth0Id);
        return obtenerConsultaConHospitalSeleccionado(paciente);
    }

    private ConsultaMedica obtenerConsultaConHospitalSeleccionado(Paciente paciente) {
        Optional<ConsultaMedica> opConsultaMedica = repoConsultasMedicas
                .findFirstByPacienteIdAndEstadoConsultaIn(paciente.getId(), ESTADOS_CONSULTA_CON_HOSPITAL);

        if (opConsultaMedica.isEmpty()) {
            throw new NoSuchElementException(
                    "Se debe seleccionar primero un hospital o finalizar el pretriage para estimar su tiempo de atencion");
        }
        return opConsultaMedica.get();
    }

    private TiempoEstimadoAtencionResponse calcularTiempoEstimadoDeAtencion(ConsultaMedica consultaMedica) {
        return estimacionAtencionService.calcularPara(consultaMedica);
    }

    private Paciente obtenerPaciente(String auth0Id) {
        Optional<Paciente> opPaciente = pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id);

        if (opPaciente.isEmpty()) {
            throw new AccessDeniedException("No tiene permisos para seleccionar el hospital de otro paciente");
        }

        return opPaciente.get();
    }

    private EspecialidadMedica obtenerEspecialidad(String codigoEspecialidad) {
        return repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)
                .orElseThrow(() -> new NoSuchElementException("Especialidad medica inexistente"));
    }

    private void validarHospitalAtiendeEspecialidad(Hospital hospital, EspecialidadMedica especialidad) {
        List<EspecialidadMedica> especialidadesHospital = hospital.getEspecialidades();

        if (especialidadesHospital.isEmpty()) {
            throw new NoSuchElementException(
                    "En el hospital seleccionado no se cargaron las especialidades o no cuenta con ninguna especialidad en urgencias");
        }

        boolean atiendeEspecialidad = especialidadesHospital.stream()
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

    private Hospital obtenerHospital(Long idHospital) {
        Optional<Hospital> opHospital = repoHospitales.findById(idHospital);
        if (opHospital.isEmpty()) {
            throw new NoSuchElementException("No existe el Hospital con id: " + idHospital);
        }
        return opHospital.get();
    }
}
