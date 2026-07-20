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
POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/ausente
```

### List Available Patients

```http
GET /api/medico/sesiones/{sesionId}/pacientes-disponibles
```

Returns ordered `EntradaCola.EN_COLA` consultations for the session hospital and specialty.

### Attention History

```http
GET /api/medico/atenciones
```

### Confirm Patient Present

```http
POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/presente
```

Creates an `AtencionMedica.EN_CURSO` historical record.

### Finish Attention

```http
POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/finalizar
```

Finalizes the consultation, queue entry, and historical attention in one operation.

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

## Health Insurance (Patient)

### List Credentials

```http
GET /api/obrasocial/credenciales
```

Returns all health insurance credentials for the authenticated patient.

### Add Credential

```http
POST /api/obrasocial/credenciales
```

Body:

```json
{
  "numeroAfiliado": "12345678",
  "nombreObraSocial": "OSDE",
  "plan": "classic",
  "fechaVencimiento": "2045-12-31"
}
```

### Delete Credential

```http
DELETE /api/obrasocial/credenciales/{idCredencial}
```

### Update Credential

```http
PUT /api/obrasocial/credenciales/{idCredencial}
```

Body:

```json
{
  "numeroAfiliado": "12345678",
  "nombreObraSocial": "OSDE",
  "plan": "classic",
  "fechaVencimiento": "2045-12-31"
}
```

## Health Insurance (Admin)

### Add Health Insurance

```http
POST /api/obrasocial
```

Body:

```json
{
  "nombre": "OSDE"
}
```

### Delete Health Insurance

```http
DELETE /api/obrasocial/{idObraSocial}
```

## Health Insurance (Receptionist)

### List Patient Credentials

```http
GET /api/pacientes/{idPaciente}/obrasocial/credenciales
```

### Add Patient Credential

```http
POST /api/pacientes/{idPaciente}/obrasocial/credencial
```

Body:

```json
{
  "numeroAfiliado": "12345678",
  "nombreObraSocial": "OSDE",
  "plan": "classic",
  "fechaVencimiento": "2045-12-31"
}
```

### Delete Patient Credential

```http
DELETE /api/pacientes/{idPaciente}/obrasocial/credenciales/{idCredencial}
```

### Update Patient Credential

```http
PUT /api/pacientes/{idPaciente}/obrasocial/credenciales/{idCredencial}
```

Body:

```json
{
  "numeroAfiliado": "12345678",
  "nombreObraSocial": "OSDE",
  "plan": "classic",
  "fechaVencimiento": "2045-12-31"
}
```

## Medical Studies (Patient)

### Upload Study

```http
POST /api/estudios
```

Multipart form with file and JSON body:

```json
{
  "tipoArchivo": "Radiografía",
  "descripcion": "Radiografía de tórax"
}
```

### Delete Study

```http
DELETE /api/estudios/{idEstudio}
```

### List All Studies

```http
GET /api/estudios
```

Returns metadata for all medical studies of the authenticated patient.

### Get Study Metadata

```http
GET /api/estudios/{idEstudio}
```

### Download Study File

```http
GET /api/estudios/{idEstudio}/file
```

Downloads the actual file (PDF, image, etc.) for the specified study.

## Rooms (Admin)

### Create Room

```http
POST /api/hospitales/{hospitalId}/especialidades/{idEspecialidadMedica}/salas
```

Body:

```json
{
  "nombre": "Sala 1"
}
```

### Delete Room

```http
DELETE /api/salas/{salaId}
```
