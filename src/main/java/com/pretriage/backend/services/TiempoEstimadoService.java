package com.pretriage.backend.services;

import com.pretriage.backend.model.consultas.AtencionMedica;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.GestorDeCola;
import jakarta.persistence.Transient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TiempoEstimadoService {


    @Value("${tiempo.estimado.atencion-triage.segundos}")
    private long TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE; //en segundos

    public List<LocalDateTime> calcularTiempoDeAtencionPara(ConsultaMedica consultaMedica,
                                                            List<AtencionMedica> atencionesMedicasActuales,
                                                            GestorDeCola gestorDeCola) {
        int posicion = gestorDeCola.obtenerPosicionDe(consultaMedica);

        if(posicion == -1){
            return List.of(); //empty list
        }

        LocalDateTime proximaSalaDisponible = this.obtenerProximaSalaDisponible(atencionesMedicasActuales);
        LocalDateTime tiempoEstimado = this.calcularTiempoBasadoEnPosicionYSalas(posicion, proximaSalaDisponible);


        List<LocalDateTime> rangoDeTiempoEstimadoDeAtencion = new ArrayList<>();
        rangoDeTiempoEstimadoDeAtencion.add(tiempoEstimado.minusMinutes(10));
        rangoDeTiempoEstimadoDeAtencion.add(tiempoEstimado.plusMinutes(10));

        return rangoDeTiempoEstimadoDeAtencion;
    }

    private LocalDateTime obtenerProximaSalaDisponible(List<AtencionMedica> atencionesMedicasActuales) {
        return atencionesMedicasActuales.stream()
                .map(AtencionMedica::getFechaHoraFinAtencion)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
    }

    private LocalDateTime calcularTiempoBasadoEnPosicionYSalas(int posicion, LocalDateTime proximaSalaDisponible) {

        LocalDateTime base = proximaSalaDisponible.isAfter(LocalDateTime.now())
                ? proximaSalaDisponible
                : LocalDateTime.now();

        return base.plusSeconds(posicion * TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);
    }

}
