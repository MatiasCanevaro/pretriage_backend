package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TiempoEstimadoNotifier {

    private final Map<Long, List<SseEmitter>> conexiones = new ConcurrentHashMap<>();

    private final Map<Long, TiempoEstimadoAtencionResponse> ultimoTiempoEnviado = new ConcurrentHashMap<>();

    public SseEmitter conectar(Long consultaId){

        SseEmitter emitter = new SseEmitter(0L);

        if(conexiones.containsKey(consultaId)){//si ya se conecto antes ...
            List<SseEmitter> emittersPrev =conexiones.get(consultaId);
            emittersPrev.add(emitter);
        } else { // si no se conecto antes
            List<SseEmitter> emittersNuevo = new ArrayList<>();
            emittersNuevo.add(emitter);
            conexiones.put(consultaId, emittersNuevo);
        }


        log.info("Cliente conectado {}", consultaId);

        emitter.onCompletion(() -> {
            conexiones.remove(consultaId);
            ultimoTiempoEnviado.remove(consultaId);
            log.info("Cliente desconectado (completion) {}", consultaId);
        });
        emitter.onTimeout(() -> {
            conexiones.remove(consultaId);
            ultimoTiempoEnviado.remove(consultaId);
            log.info("Cliente desconectado (timeout) {}", consultaId);
        });
        emitter.onError(e -> {
            conexiones.remove(consultaId);
            ultimoTiempoEnviado.remove(consultaId);
            log.info("Cliente desconectado (error) {}", consultaId);
        });

        return emitter;
    }

    public void notificar(TiempoEstimadoAtencionResponse response){

        List<SseEmitter> emitters = conexiones.get(response.getIdConsulta());

        if(emitters == null || emitters.isEmpty()){
            return;
        }

        TiempoEstimadoAtencionResponse ultimo =
                ultimoTiempoEnviado.get(response.getIdConsulta());

        if(response.equals(ultimo)){
            return;// no hago nada si no cambio
        }

        try{//se avisa al front del cambio
            log.info("Enviando actualización {}", response.getIdConsulta());
            for(SseEmitter emitter : emitters){
                emitter.send(
                        SseEmitter.event()
                                .name("tiempo-estimado")
                                .data(response)
                );
            }
            ultimoTiempoEnviado.put(
                    response.getIdConsulta(),
                    response
            );
        }catch(IOException e){
            for(SseEmitter emitter : emitters){
                emitter.complete();
            }

            conexiones.remove(response.getIdConsulta());
            ultimoTiempoEnviado.remove(response.getIdConsulta());

            log.error("no se pudo enviar al front la actualización del tiempo estimado de atención para la consulta {}:\n {}",
                    response.getIdConsulta(),
                    e.getMessage());
        }
    }

    @Scheduled(fixedRate = 30000)
    public void enviarHeartbeat(){//TODO probar heartbear si anda

        Iterator<Map.Entry<Long, List<SseEmitter>>> iterator =
                conexiones.entrySet().iterator();

        while(iterator.hasNext()) {

            Map.Entry<Long, List<SseEmitter>> entry =
                    iterator.next();

            List<SseEmitter> emitters = entry.getValue();
            emitters.forEach((emitter -> {
                        try {
                            emitter.send(
                                    SseEmitter.event()
                                            .name("heartbeat")
                                            .comment("keep-alive")
                            );
                        } catch (Exception ex) {
                            emitter.complete();
                            emitters.remove(emitter);
                        }
                    }));
            if(emitters.isEmpty()){
                iterator.remove();
            }
        }
    }
}
