# Generated Domain Model

This file is generated from JPA entity classes under:

```text
src/main/java/com/pretriage/backend/model
```

Regenerate with:

```powershell
python scripts\generate_domain_diagram.py
```

## Entities

- `AdmisionRecepcion`: `src/main/java/com/pretriage/backend/model/recepcion/AdmisionRecepcion.java`
- `AsignacionMedicoHospital`: `src/main/java/com/pretriage/backend/model/personas/AsignacionMedicoHospital.java`
- `AtencionMedica`: `src/main/java/com/pretriage/backend/model/consultas/AtencionMedica.java`
- `Chat`: `src/main/java/com/pretriage/backend/model/chat/Chat.java`
- `ConsultaMedica`: `src/main/java/com/pretriage/backend/model/consultas/ConsultaMedica.java`
- `Coordenada`: `src/main/java/com/pretriage/backend/model/hospitales/Coordenada.java`
- `Credencial`: `src/main/java/com/pretriage/backend/model/hospitales/Credencial.java`
- `Direccion`: `src/main/java/com/pretriage/backend/model/hospitales/Direccion.java`
- `EntradaCola`: `src/main/java/com/pretriage/backend/model/consultas/EntradaCola.java`
- `EspecialidadMedica`: `src/main/java/com/pretriage/backend/model/hospitales/EspecialidadMedica.java`
- `GestorDeCola`: `src/main/java/com/pretriage/backend/model/consultas/GestorDeCola.java`
- `Hospital`: `src/main/java/com/pretriage/backend/model/hospitales/Hospital.java`
- `Medico`: `src/main/java/com/pretriage/backend/model/personas/Medico.java`
- `Mensaje`: `src/main/java/com/pretriage/backend/model/chat/Mensaje.java`
- `ObraSocial`: `src/main/java/com/pretriage/backend/model/hospitales/ObraSocial.java`
- `Paciente`: `src/main/java/com/pretriage/backend/model/personas/Paciente.java`
- `Recepcionista`: `src/main/java/com/pretriage/backend/model/personas/Recepcionista.java`
- `Sala`: `src/main/java/com/pretriage/backend/model/hospitales/Sala.java`
- `SesionAtencionMedica`: `src/main/java/com/pretriage/backend/model/consultas/SesionAtencionMedica.java`
- `SesionRecepcion`: `src/main/java/com/pretriage/backend/model/recepcion/SesionRecepcion.java`
- `Sintoma`: `src/main/java/com/pretriage/backend/model/consultas/Sintoma.java`
- `UsuarioAuth`: `src/main/java/com/pretriage/backend/model/personas/UsuarioAuth.java`

## Mermaid ER Diagram

```mermaid
erDiagram
    CHAT {
        Long id
        LocalDateTime fechaHoraCreacion
        boolean finalizado
        String resultadoTriageJson
    }
    MENSAJE {
        Long id
        String contenido
        AutorMensaje autor
        LocalDateTime fechaHoraEnvio
    }
    ATENCION_MEDICA {
        Long id
        EstadoAtencionMedica estado
        LocalDateTime fechaHoraInicio
        LocalDateTime fechaHoraFin
    }
    CONSULTA_MEDICA {
        Long id
        LocalDateTime fechaHoraCreacion
        String codigoLlamado
        NivelDeGravedad nivelDeGravedadBot
        NivelDeGravedad nivelDeGravedadMedico
        EstadoConsulta estadoConsulta
    }
    ENTRADA_COLA {
        Long id
        EstadoEntradaCola estado
        TipoPausaCola tipoPausa
        int prioridad
        long ordenRelativo
        LocalDateTime fechaHoraIngreso
        LocalDateTime fechaHoraLlamado
        LocalDateTime fechaHoraSalidaTemporal
        LocalDateTime fechaHoraUltimaRepregunta
        LocalDateTime fechaHoraLimiteRespuesta
    }
    GESTOR_DE_COLA {
        Long id
        long TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE
    }
    SESION_ATENCION_MEDICA {
        Long id
        EstadoSesionMedica estado
        LocalDateTime fechaHoraInicio
        LocalDateTime fechaHoraFin
        LocalDateTime fechaHoraPausa
    }
    SINTOMA {
        Long id
        String nombre
    }
    COORDENADA {
        Long id
        Double latitud
        Double longitud
    }
    CREDENCIAL {
        Long id
        String numeroAfiliado
        String plan
        LocalDate fechaVencimiento
    }
    DIRECCION {
        Long id
        String calle
        String altura
        String piso
        String codigoPostal
    }
    ESPECIALIDAD_MEDICA {
        Long id
        String codigo
        String nombre
    }
    HOSPITAL {
        Long id
        String placeId
        String nombre
    }
    OBRA_SOCIAL {
        Long id
        String nombre
        boolean virgente
    }
    SALA {
        Long id
        String nombre
        boolean activa
    }
    ASIGNACION_MEDICO_HOSPITAL {
        Long id
    }
    MEDICO {
        Long id
        String matricula
    }
    PACIENTE {
        Long id
        Genero generoBiologico
        Genero generoConElQueSeIdentifica
        String nombre
        String apellido
        String numeroDocumento
        TipoDocumento tipoDocumento
        OrigenRegistroPaciente origenRegistro
        LocalDate fechaNacimiento
        String telefono
        String correoElectronico
        Double peso
        Integer altura
    }
    RECEPCIONISTA {
        Long id
    }
    USUARIO_AUTH {
        String id
        String nombre
        String apellido
        String numeroDocumento
        TipoDocumento tipoDocumento
        String correoElectronico
        RolSistema rol
    }
    ADMISION_RECEPCION {
        Long id
        EstadoAdmisionRecepcion estado
        String formularioJson
        String resultadoTriageJson
        LocalDateTime fechaHoraInicio
        LocalDateTime fechaHoraFinalizacion
        LocalDateTime fechaHoraCancelacion
    }
    SESION_RECEPCION {
        Long id
        EstadoSesionRecepcion estado
        LocalDateTime fechaHoraInicio
        LocalDateTime fechaHoraFin
    }
    CHAT ||--o{ MENSAJE : mensajes
    CHAT ||--|| PACIENTE : paciente
    MENSAJE }o--|| PACIENTE : pacienteAutor
    ATENCION_MEDICA ||--|| CONSULTA_MEDICA : consultaMedica
    ATENCION_MEDICA }o--|| SESION_ATENCION_MEDICA : sesionAtencionMedica
    CONSULTA_MEDICA }o--|| HOSPITAL : hospital
    CONSULTA_MEDICA }o--|| ESPECIALIDAD_MEDICA : especialidad
    CONSULTA_MEDICA }o--|| MEDICO : medico
    CONSULTA_MEDICA }o--|| SALA : sala
    CONSULTA_MEDICA }o--|| PACIENTE : paciente
    CONSULTA_MEDICA ||--o{ SINTOMA : sintomasBot
    CONSULTA_MEDICA ||--o{ MENSAJE : chat
    ENTRADA_COLA }o--|| GESTOR_DE_COLA : gestorDeCola
    ENTRADA_COLA ||--|| CONSULTA_MEDICA : consultaMedica
    GESTOR_DE_COLA }o--|| HOSPITAL : hospital
    GESTOR_DE_COLA }o--|| ESPECIALIDAD_MEDICA : especialidad
    GESTOR_DE_COLA ||--o{ CONSULTA_MEDICA : consultasEnEspera
    GESTOR_DE_COLA ||--o{ ENTRADA_COLA : entradas
    SESION_ATENCION_MEDICA }o--|| MEDICO : medico
    SESION_ATENCION_MEDICA }o--|| HOSPITAL : hospital
    SESION_ATENCION_MEDICA }o--|| ESPECIALIDAD_MEDICA : especialidad
    SESION_ATENCION_MEDICA }o--|| SALA : sala
    CREDENCIAL }o--|| OBRA_SOCIAL : obraSocial
    CREDENCIAL }o--|| PACIENTE : paciente
    DIRECCION ||--|| COORDENADA : coordenada
    HOSPITAL ||--o{ RECEPCIONISTA : recepcionistas
    HOSPITAL ||--o{ SALA : salas
    HOSPITAL ||--|| DIRECCION : direccion
    OBRA_SOCIAL ||--o{ CREDENCIAL : credenciales
    SALA }o--|| HOSPITAL : hospital
    SALA }o--|| ESPECIALIDAD_MEDICA : especialidad
    ASIGNACION_MEDICO_HOSPITAL }o--|| MEDICO : medico
    ASIGNACION_MEDICO_HOSPITAL }o--|| HOSPITAL : hospital
    ASIGNACION_MEDICO_HOSPITAL }o--|| ESPECIALIDAD_MEDICA : especialidad
    MEDICO ||--|| USUARIO_AUTH : usuarioAuth
    MEDICO ||--o{ ASIGNACION_MEDICO_HOSPITAL : asignaciones
    PACIENTE ||--|| USUARIO_AUTH : usuarioAuth
    PACIENTE ||--|| COORDENADA : coordenadaActual
    PACIENTE ||--|| DIRECCION : direccion
    RECEPCIONISTA ||--|| USUARIO_AUTH : usuarioAuth
    ADMISION_RECEPCION ||--|| CONSULTA_MEDICA : consultaMedica
    ADMISION_RECEPCION }o--|| SESION_RECEPCION : sesionRecepcion
    SESION_RECEPCION }o--|| RECEPCIONISTA : recepcionista
    SESION_RECEPCION }o--|| HOSPITAL : hospital
```
