package com.pretriage.backend.services;

import com.pretriage.backend.exceptions.NoEixstenSalasActivasException;
import com.pretriage.backend.model.consultas.AtencionMedica;
import com.pretriage.backend.repositories.RepoAtencionMedica;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalaService {

    private final RepoAtencionMedica repoAsignacionMedica;

    public List<AtencionMedica> obtenerAtencionesMedicasActuales(Long idHospital) {
        return this.repoAsignacionMedica.findAllByfechaHoraFinAtencionAfterAndIdHospital(LocalDateTime.now(), idHospital)
                .orElseThrow(NoEixstenSalasActivasException::new);//no deberia pasar
    }

}
