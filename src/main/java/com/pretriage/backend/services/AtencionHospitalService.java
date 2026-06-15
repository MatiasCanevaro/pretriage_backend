package com.pretriage.backend.services;

import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AtencionHospitalService {

    private final RepoConsultasMedicas repoConsultasMedicas;
    private final RepoHospitales repoHospitales;

    private final PacienteService pacienteService;
    private final GooglePlacesService googlePlacesService;

    @Transactional
    public void seleccionarHospital(
            String auth0Id,
            String placeId) {

        Optional<Paciente> opPaciente = pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id);

        if(opPaciente.isEmpty()){
            throw  new AccessDeniedException(
                    "No tiene permisos para seleccionar el hospital de otro paciente");
        }
        Paciente paciente = opPaciente.get();

        ConsultaMedica consultaMedica =
                repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(paciente.getId(), EstadoConsulta.PENDIENTE)
                        .orElseGet(()-> {
                            ConsultaMedica consultaMedicaNueva = new ConsultaMedica();
                            consultaMedicaNueva.setPaciente(paciente);
                            consultaMedicaNueva.setEstadoConsulta(EstadoConsulta.HOSPITAL_SELECCIONADO);
                            consultaMedicaNueva.setFechaHoraCreacion(LocalDateTime.now());
                            return consultaMedicaNueva;
                        });


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
}
