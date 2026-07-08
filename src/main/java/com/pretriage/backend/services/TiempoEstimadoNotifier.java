package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TiempoEstimadoNotifier {

    private final Map<Long, SseEmitter> conexiones = new ConcurrentHashMap<>();

    private final Map<Long, TiempoEstimadoAtencionResponse> ultimoTiempoEnviado = new ConcurrentHashMap<>();

    public SseEmitter conectar(Long consultaId){

        SseEmitter emitter = new SseEmitter(0L);

        conexiones.put(consultaId, emitter);

        emitter.onCompletion(() -> {
            conexiones.remove(consultaId);
            ultimoTiempoEnviado.remove(consultaId);
        });
        emitter.onTimeout(() -> {
            conexiones.remove(consultaId);
            ultimoTiempoEnviado.remove(consultaId);
        });
        emitter.onError(e -> {
            conexiones.remove(consultaId);
            ultimoTiempoEnviado.remove(consultaId);
        });

        return emitter;
    }

    public void notificar(TiempoEstimadoAtencionResponse response){

        SseEmitter emitter = conexiones.get(response.getIdConsulta());

        if(emitter == null){
            return;
        }

        TiempoEstimadoAtencionResponse ultimo =
                ultimoTiempoEnviado.get(response.getIdConsulta());

        if(response.equals(ultimo)){
            return;// no hago nada si no cambio
        }

        try{//se avisa al front del cambio
            emitter.send(
                    SseEmitter.event()
                            .name("tiempo-estimado")
                            .data(response)
            );

            ultimoTiempoEnviado.put(
                    response.getIdConsulta(),
                    response
            );
        }catch(IOException e){

            emitter.complete();

            conexiones.remove(response.getIdConsulta());
            ultimoTiempoEnviado.remove(response.getIdConsulta());

            log.error("no se pudo enviar al front la actualización del tiempo estimado de atención para la consulta {}: {}",
                    response.getIdConsulta(),
                    e.getMessage());
        }
    }
}
