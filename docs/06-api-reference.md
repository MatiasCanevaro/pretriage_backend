# API Reference

This is a practical reference for the main flows. It is not a full OpenAPI replacement.

## Auth

### Register

```http
POST /api/register
```

Public self-registration. Only patient accounts are allowed; staff or admin
roles are rejected with `403`.

Body:

```json
{
  "nombre": "Juan",
  "apellido": "Perez",
  "numeroDocumento": "30111222",
  "tipoDocumento": "DNI",
  "tipoUsuario": "Paciente",
  "email": "user@example.com",
  "password": "secret",
  "rol": "USER"
}
```

Creates the Auth0 account and the local patient record. The password must
contain between 8 and 72 characters including uppercase, lowercase, numeric and
symbol characters.

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
GET /api/especialidades
```

Returns available medical specialties sorted by name.

## Hospitals

### Nearby Hospitals Filtered By Specialty

```http
GET /api/hospitales/cercanos?latitud=-34.6&longitud=-58.4&codigoEspecialidad=CLINICA_MEDICA&transporte=transporte-publico
```

Returns nearby hospitals that support the selected specialty. Each hospital includes `tiempoEstimadoArriboMejorRuta` (estimated arrival time of the best route for the transport mode; `transporte` is optional, defaults to `transporte-publico`, and the field is null when no route can be computed).

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

Assigns the hospital and specialty to the active consultation and enters it into
the queue (`EN_COLA` + `EntradaCola`) with default priority (`NORMAL`). The AI
triage chat is optional; when it finishes, the queue priority is updated with the
pretriage result.

Returns `204 No Content`. The dynamic estimate is available via `GET /api/atencion/tiempo-estimado` (`TiempoEstimadoAtencionResponse` with `consultaId`, `fechaHoraAtencionEstimada`, `posicionEnCola`, `pacientesAntes`, `minutosPromedioAtencion`, `hayMedicosActivos`, `medicosActivos`, `medicosParaEstimacion`, `codigoSala` from `Sala.nombre` — `null` until the doctor calls the patient — and `mensaje` when `hayMedicosActivos=false`).

### Get Selected Hospital

```http
GET /api/atencion/hospital
```

Returns the hospital selected in the active consultation of the authenticated
patient, together with its formatted address. `direccion` is `null` when the
hospital has no stored `Direccion`.

Example response:

```json
{
  "idHospital": 1,
  "placeId": "google-place-id",
  "nombre": "Hospital Central",
  "direccion": "Av. Siempre Viva 742, CABA, Buenos Aires"
}
```

### Calculate Arrival Time to Hospital

```http
GET /api/hospitales/{idHospital}/tiempo-arribo?transporte=transporte-publico&latitud=-34.6&longitud=-58.4
```

Returns list with routes with estimated travel time from patient's location to the specified hospital.

## User Profile

### Get Profile

```http
GET /api/perfil
```

Returns the authenticated patient profile: identity, document, birth date,
genders, contact, stored address, weight and height.

### Update Profile

```http
PUT /api/perfil
```

Body:

```json
{
  "nombre": "Juan",
  "apellido": "Perez",
  "tipoDocumento": "DNI",
  "numeroDocumento": "30111222",
  "fechaNacimiento": "1990-05-10",
  "generoBiologico": "MASCULINO",
  "generoConElQueSeIdentifica": "MASCULINO",
  "email": "user@example.com",
  "telefono": "1155551234",
  "calle": "Av. Siempre Viva",
  "alturaDireccion": "742",
  "piso": "3",
  "codigoPostal": "1414",
  "ciudad": "CABA",
  "provincia": "Buenos Aires",
  "peso": 75.5,
  "alturaPersona": 180
}
```

If the patient has no stored address, a `Direccion` is created; otherwise the
stored address is updated with the submitted values.

## Estimated Attention Time

```http
GET /api/atencion/tiempo-estimado
```

Returns dynamic estimate based on `EntradaCola` (`EN_COLA` only, ordered by `prioridad DESC`, `ordenRelativo ASC`, `fechaHoraIngreso ASC`) and active doctor sessions (`SesionAtencionMedica` `ACTIVA`; if none, estimates with one virtual doctor and `hayMedicosActivos=false`). Response is `TiempoEstimadoAtencionResponse` with `consultaId`, `fechaHoraAtencionEstimada`, `posicionEnCola`, `pacientesAntes`, `minutosPromedioAtencion`, `hayMedicosActivos`, `medicosActivos`, `medicosParaEstimacion`, `codigoSala` (`Sala.nombre`, `null` until `LLAMADO`/`EN_ATENCION` when a room is assigned), and `mensaje` when no doctors are active. See also `POST /api/atencion/hospital` which returns the same payload at queue entry.

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

### List Assignments

```http
GET /api/medico/asignaciones
```

Returns the authenticated doctor's hospital and specialty assignments.

### List Rooms

```http
GET /api/hospitales/{hospitalId}/salas?codigoEspecialidad={codigoEspecialidad}
```

Returns the active rooms of a hospital for the given specialty.

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
GET /api/medico/pacientes/{pacienteId}/ultimos-reportes?limite=5
```

Gets up to `limite` most recent active medical records for quick triage reference, ordered by `fechaSubida` DESC. `limite` is optional, defaults to `5`; only studies with `activo=true` are returned. Values `< 1` return `400`.

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

> Credential load and update operations validate the health insurance in runtime:
> `CredencialService` resolves the validator for the requested obra social through
> `FabricaValidadoresCredencialesObraSocial` and passes it to
> `ValidacionCredencialObraSocialService` (polymorphism by parameter).
> Each implementation of `ValidadorCredencialObraSocial` declares the obra social
> it covers via `getObraSocial()`. Real integrations with each health insurance
> are out of scope; the only implementation is `MockValidadorCredencialObraSocial`
> (demo, covers `OSDE` and always accepts the credential).
> If no validator is configured for the requested obra social, the API responds
> `400` with `{ "error": "No hay un validador configurado para la obra social ..." }`.
> If the selected validator rejects the credential, the API responds `400` with
> `{ "error": "La credencial de obra social no es válida" }` and nothing is persisted.

#### Add a real validator

1. Implement `ValidadorCredencialObraSocial` (e.g. `ValidadorCredencialIOMA`).
2. Annotate it with `@Component`/`@Service` and return the obra social name from
   `getObraSocial()` (e.g. `"IOMA"`).
3. From then on, `FabricaValidadoresCredencialesObraSocial` picks that
   implementation automatically for that obra social; no other wiring is needed.

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

> Applies the same runtime-resolved credential validation as the patient flow on
> load and update operations. See the patient section above for behavior and error
> format.

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

## Hospital Configuration (Hospital Admin)

All operations are scoped to the hospital in the URL and require an active
`ADMIN_HOSPITAL` membership for that hospital.

### Get Configuration

```http
GET /api/admin/hospitales/{hospitalId}/configuracion
```

Returns the hospital's enabled specialties and rooms.

### Enable or Disable Specialty

```http
POST /api/admin/hospitales/{hospitalId}/configuracion/especialidades/{especialidadId}
DELETE /api/admin/hospitales/{hospitalId}/configuracion/especialidades/{especialidadId}
```

### Create Room

```http
POST /api/admin/hospitales/{hospitalId}/configuracion/salas
```

Body:

```json
{
  "nombre": "Consultorio 3",
  "especialidadId": 1
}
```

### Update Room

```http
PUT /api/admin/hospitales/{hospitalId}/configuracion/salas/{salaId}
```

Uses the same body as room creation.

### Activate or Deactivate Room

```http
PATCH /api/admin/hospitales/{hospitalId}/configuracion/salas/{salaId}/estado
```

Body:

```json
{
  "activa": false
}
```
