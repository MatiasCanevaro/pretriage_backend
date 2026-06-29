package com.pretriage.backend.model.consultas;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.pretriage.backend.model.hospitales.Hospital;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.Setter;
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


    public Optional<LocalDateTime> calcularTiempoDeAtencionPara(ConsultaMedica consultaMedica) {

        reordenarColaPorPrioridad();

        int posicion = consultasEnEspera.indexOf(consultaMedica);

        if (posicion == -1) {
            return Optional.empty();
        }

        return Optional.of(
                LocalDateTime.now()
                        .plusSeconds(posicion * TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE)// asumiendo que las cosultas son siempre a futuro
        );
    }

    private void reordenarColaPorPrioridad() {

        this.eliminarAtendidosDeLaCola();

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
        if (nivel == null) {
            return 2;
        }

        return switch (nivel) {
            case RIESGO_VITAL_INMEDIATO -> 5;
            case MUY_URGENTE -> 4;
            case URGENTE -> 3;
            case NORMAL -> 2;
            case NO_URGENTE -> 1;
        };
    }

    public void agregarConsultaMedicaALaCola(ConsultaMedica consultaMedica) {
        if (!this.consultasEnEspera.contains(consultaMedica)) {
            this.consultasEnEspera.add(consultaMedica);
        }
        reordenarColaPorPrioridad();
    }

    private void eliminarAtendidosDeLaCola(){
        this.consultasEnEspera.removeIf(consultaMedica ->
                consultaMedica.getEstadoConsulta().equals(EstadoConsulta.FINALIZADA));
    }
}


