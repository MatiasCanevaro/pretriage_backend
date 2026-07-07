package com.pretriage.backend.model.consultas;

import java.time.LocalDateTime;
import java.util.*;

import com.pretriage.backend.model.hospitales.Hospital;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Getter
@Setter
@Entity
public class GestorDeCola {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToOne
    @JoinColumn(name = "id_hospital", referencedColumnName = "id")
    private Hospital hospital;

    @OneToMany
    @JoinColumn(name = "id_gestor_de_cola", referencedColumnName = "id")
    private List<ConsultaMedica> consultasEnEspera;

    @Value("${tiempo.estimado.atencion-triage.segundos}")
    private long TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE; //en segundos

    public GestorDeCola (){
        this.consultasEnEspera = new ArrayList<>();
    }


    public List<LocalDateTime> calcularTiempoDeAtencionPara(ConsultaMedica consultaMedica,
                                                            List<AtencionMedica> atencionesMedicasActuales) {

        reordenarColaPorPrioridad();

        int posicion = consultasEnEspera.indexOf(consultaMedica);

        if (posicion == -1) {
            return List.of(); //empty list
        }

        LocalDateTime proximaSalaDisponible = this.obtenerProximaSalaDisponible(atencionesMedicasActuales);
        LocalDateTime tiempoEstimado = this.calcularTiempoBasadoEnPosicionYSalas(posicion, proximaSalaDisponible);


        List<LocalDateTime> rangoDeTiempoEstimadoDeAtencion = new ArrayList<>();
        rangoDeTiempoEstimadoDeAtencion.add(tiempoEstimado.minusMinutes(10));
        rangoDeTiempoEstimadoDeAtencion.add(tiempoEstimado.plusMinutes(10));

        return rangoDeTiempoEstimadoDeAtencion;
    }

    private void reordenarColaPorPrioridad() {

        this.eliminarAtendidosDeLaCola();//TODO HACER QUE SE ELIMINE CUANDO EL MEDICO INDICA QUE YA LO ATENDIO

        this.consultasEnEspera.sort(
                Comparator
                        .comparingInt(
                                (ConsultaMedica consulta) ->
                                        obtenerPrioridad(consulta.getNivelDeGravedadBot())
                        )
                        .reversed()
                        .thenComparing(ConsultaMedica::getFechaHoraCreacion)
        );
    }

    private int obtenerPrioridad(NivelDeGravedad nivel) {

        return switch (nivel) {
            case RIESGO_VITAL_INMEDIATO -> 5;
            case MUY_URGENTE -> 4;
            case URGENTE -> 3;
            case NORMAL -> 2;
            case NO_URGENTE -> 1;
            case null -> 0; //aun el bot no hizo el triage
        };
    }

    public void agregarConsultaMedicaALaCola(ConsultaMedica consultaMedica) {
        this.consultasEnEspera.add(consultaMedica);
        reordenarColaPorPrioridad();
    }

    private void eliminarAtendidosDeLaCola(){
        this.consultasEnEspera.removeIf(consultaMedica -> consultaMedica.getEstadoConsulta().equals(EstadoConsulta.PACIENTE_NO_ASISTIO) ||
                consultaMedica.getEstadoConsulta().equals(EstadoConsulta.FINALIZADA));
    }

    private LocalDateTime calcularTiempoBasadoEnPosicionYSalas(int posicion, LocalDateTime proximaSalaDisponible) {

        LocalDateTime base = proximaSalaDisponible.isAfter(LocalDateTime.now())
                ? proximaSalaDisponible
                : LocalDateTime.now();

        return base.plusSeconds(posicion * TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);
    }

    private LocalDateTime obtenerProximaSalaDisponible(List<AtencionMedica> atencionesMedicasActuales) {
        return atencionesMedicasActuales.stream()
                .map(AtencionMedica::getFechaHoraFinAtencion)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
    }
}
