package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.AtencionMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class RepoAtencionMedicaTest {

    @Autowired
    private RepoAtencionMedica repoAsignacionMedica;


    @Autowired
    private TestEntityManager entityManager;

    @Test
    void sePuedeObtenerTodasLasAtencionesMedicasActuales(){

        LocalDateTime ahora = LocalDateTime.now();

        Hospital hospitalMock = new Hospital();
        hospitalMock.setNombre("Hospital random");
        hospitalMock = entityManager.persist(hospitalMock);

        AtencionMedica atencionMedica1 = new AtencionMedica();
        atencionMedica1.setFechaHoraInicioAtencion(ahora);
        atencionMedica1.setFechaHoraFinAtencion(ahora.plusMinutes(15));
        atencionMedica1.setHospital(hospitalMock);

        AtencionMedica atencionMedica2 = new AtencionMedica();
        atencionMedica2.setFechaHoraInicioAtencion(ahora);
        atencionMedica2.setFechaHoraFinAtencion(ahora.plusMinutes(30));
        atencionMedica2.setHospital(hospitalMock);

        AtencionMedica atencionMedica3 = new AtencionMedica();
        atencionMedica3.setFechaHoraInicioAtencion(ahora.minusMinutes(15));
        atencionMedica3.setFechaHoraFinAtencion(ahora);
        atencionMedica3.setHospital(hospitalMock);

        AtencionMedica atencionMedica4 = new AtencionMedica();
        atencionMedica4.setFechaHoraInicioAtencion(ahora.minusMinutes(20));
        atencionMedica4.setFechaHoraFinAtencion(ahora.minusMinutes(5));
        atencionMedica4.setHospital(hospitalMock);

        entityManager.persist(atencionMedica1);
        entityManager.persist(atencionMedica2);
        entityManager.persist(atencionMedica3);
        entityManager.persist(atencionMedica4);
        entityManager.flush();

        Optional<List<AtencionMedica>> opAtencionMedicaList = this.repoAsignacionMedica
                .findAllByfechaHoraFinAtencionAfterAndIdHospital(ahora, hospitalMock.getId());

        assertTrue(opAtencionMedicaList.isPresent());
        List<AtencionMedica> atencionMedicasResult = opAtencionMedicaList.get();
        assertEquals(3, atencionMedicasResult.size());
        assertEquals(atencionMedica1, atencionMedicasResult.get(0));
        assertEquals(atencionMedica2, atencionMedicasResult.get(1));
        assertEquals(atencionMedica3, atencionMedicasResult.get(2));
    }

}
