package com.pretriage.backend.model.consultas;

import java.util.*;

import com.pretriage.backend.model.hospitales.Hospital;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.Setter;

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


    public GestorDeCola (){
        this.consultasEnEspera = new ArrayList<>();
    }


    public int obtenerPosicionDe(ConsultaMedica consultaMedica){
        return this.consultasEnEspera.indexOf(consultaMedica);
    }

    private void reordenarColaPorPrioridad() {

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

    @Transactional
    public void sacarConsultaMedicaDeLaCola(ConsultaMedica consultaMedica){
        this.consultasEnEspera.removeIf(consultaEnEspera -> consultaEnEspera.getId().equals(consultaMedica.getId()));
    }


}
