# API Reference

This is a practical reference for the main flows. It is not a full OpenAPI replacement.

## Auth

### Login

```http
POST /api/login
```

Body:

```json
{
  "email": "user@example.com",
  "password": "secret"
}
```

Returns token data.

## Specialties

### List Specialties

```http
GET /api/especialidades-medicas
```

Returns available medical specialties.

## Hospitals

### Nearby Hospitals Filtered By Specialty

```http
GET /api/hospitales/cercanos?latitud=-34.6&longitud=-58.4&codigoEspecialidad=CLINICA_MEDICA
```

Returns nearby hospitals that support the selected specialty.

### Select Hospital

```http
POST /api/atencion/hospital
```

Body:

```json
{
  "placeId": "google-place-id",
  "codigoEspecialidad": "CLINICA_MEDICA"
}
```

## Estimated Attention Time

```http
GET /api/atencion/tiempo-estimado
```

Returns dynamic estimate based on `EntradaCola` and active doctor sessions.

## Chat

### Start Chat

```http
POST /api/chat
```

### Send Message

```http
POST /api/chat/{id}/mensajes
```

Body:

```json
{
  "contenido": "Tengo fiebre desde ayer"
}
```

When triage finalizes, response includes `atencionEstimada`.

### Get Chat

```http
GET /api/chat/{id}
```

## Patient Queue State

```http
GET /api/paciente/consulta/estado
```

When patient is `EN_COLA`, response includes dynamic estimated attention time.

### Patient Temporarily Leaves Queue

```http
POST /api/paciente/consulta/ausentarme
```

### Patient Confirms Delay

```http
POST /api/paciente/consulta/estoy-atrasado
```

### Patient Confirms Still Attending

```http
POST /api/paciente/consulta/sigo-asistiendo
```

### Patient Arrives

```http
POST /api/paciente/consulta/llegue
```

## Doctor

### Start Session

```http
POST /api/medico/sesiones
```

Doctor selects hospital/specialty/room.

### Pause Session

```http
POST /api/medico/sesiones/{id}/pausar
```

### Resume Session

```http
POST /api/medico/sesiones/{id}/reanudar
```

### Close Session

```http
POST /api/medico/sesiones/{id}/cerrar
```

### Call Next Patient

```http
POST /api/medico/sesiones/{id}/llamar-proximo
```

### Mark Patient Absent

```http
POST /api/medico/sesiones/{id}/marcar-ausente
```
