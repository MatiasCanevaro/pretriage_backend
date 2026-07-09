package com.pretriage.backend.model.consultas.colaDinamica;


import com.pretriage.backend.services.TiempoEstimadoNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ColaModificadaListener {

    private final TiempoEstimadoNotifier notifier;

    @EventListener
    public void actualizarCola(ColaModificadaEvent event){

        log.info("Evento recibido con tiempos:  {}", event.tiemposEstimados());

        event.tiemposEstimados().forEach(
                notifier::notificar
        );
    }
}
