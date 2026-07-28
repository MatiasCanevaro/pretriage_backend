package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.CredencialRequest;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.services.validadoresObrasociales.ValidadorCredencialObraSocial;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ValidacionCredencialObraSocialService {

    public boolean validarCredencialObraSocial(CredencialRequest credencialRequest, Paciente paciente, 
            ValidadorCredencialObraSocial validador) {
        return validador.validar(credencialRequest, paciente);
    }

}
