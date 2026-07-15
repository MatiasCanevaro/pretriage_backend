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

### Recover Current Session

```http
GET /api/medico/sesiones/actual
```

Returns the authenticated doctor's active or paused session together with the
currently called or in-attention consultation. Both values are nullable.

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
POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/ausente
```

### List Available Patients

```http
GET /api/medico/sesiones/{sesionId}/pacientes-disponibles
```

Returns ordered `EntradaCola.EN_COLA` consultations for the session hospital and specialty,
including the effective priority, patient name and surname. For queued patients the effective priority is the preliminary backend classification. Room fields remain null until the consultation is called.

### Attention History

```http
GET /api/medico/atenciones
```

### Confirm Patient Present

```http
POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/presente
```

Creates an `AtencionMedica.EN_CURSO` historical record.

### Read Pretriage And Priority Review

```http
GET /api/medico/sesiones/{sesionId}/consultas/{consultaId}/pretriaje
```

Available only to the doctor/session that owns an `EN_ATENCION` consultation.
Returns the normalized clinical summary, preliminary and effective priorities,
and `PENDIENTE`, `CONFIRMADA`, or `CORREGIDA` review state.

### Confirm Or Correct Priority

```http
PUT /api/medico/sesiones/{sesionId}/consultas/{consultaId}/revision-prioridad
```

Confirm with `{ "decision": "CONFIRMAR" }`. Correct with
`{ "decision": "CORREGIR", "prioridad": "NORMAL", "motivo": "optional" }`.
The corrected priority must differ from the preliminary priority. The operation
is idempotent for an identical payload and preserves every genuine change.

### Finish Attention

```http
POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/finalizar
```

Finalizes the consultation, queue entry, and historical attention in one operation.
Returns `409` until priority has been reviewed.

### Clinical History Access

During attention, doctors can view patient's previous medical records:

#### Get All Medical Records

```http
GET /api/medico/pacientes/{pacienteId}/historial-clinico
```

Retrieves all medical records (PDFs, images) for a specific patient. Returns list of medical histories with metadata.

#### Download Specific Record

```http
GET /api/medico/pacientes/{pacienteId}/historial-clinico/{estudioId}/archivo
```

Downloads or previews a specific medical record file (e.g., radiology scans, previous reports).

#### Get Record Metadata

```http
GET /api/medico/pacientes/{pacienteId}/historial-clinico/{estudioId}/reporte
```

Retrieves structured information about a specific medical record (type, date, description).

#### Get Recent Records

```http
GET /api/medico/pacientes/{pacienteId}/ultimos-reportes?limit=10
```

Gets the most recent medical records for quick triage reference.

Pause, close, and call-next operations are rejected while the doctor has a patient `LLAMADO` or `EN_ATENCION`.

## Real-Time Estimated Attention

```http
GET /api/atencion/tiempos/suscribirse/{consultaId}
Accept: text/event-stream
```

Authenticated SSE stream with `tiempo-estimado` and `heartbeat` events. The patient must own the consultation.

## Reception Admission

See docs/09-reception-admission.md for the complete reception API and rules.
