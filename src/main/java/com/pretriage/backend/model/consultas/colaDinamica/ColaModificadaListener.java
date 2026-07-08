package com.pretriage.backend.model.consultas.colaDinamica;


import com.pretriage.backend.services.TiempoEstimadoNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ColaModificadaListener {//TODO testear si funciona el listener y si el SSE mantiene actualizado al front

    private final TiempoEstimadoNotifier notifier;

    @EventListener
    public void actualizarCola(ColaModificadaEvent event){
        event.tiemposEstimados().forEach(
                notifier::notificar
        );
    }
}
