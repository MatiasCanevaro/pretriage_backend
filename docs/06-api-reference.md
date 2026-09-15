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

Returns token data. The response (`LoginResponseDTO`) contains `token` (`access_token`), `refreshToken`, and `renovarTokenEn` (seconds until `access_token` expiry, from Auth0 `expires_in`). Login requests `offline_access` scope so Auth0 always returns a rotating `refresh_token`.

### Renovar Sesión (Refresh con Rotación)

```http
POST /api/renovar
```

Público (no requiere Bearer). Implementa refresh token con rotación delegada a Auth0: cada uso genera un nuevo `refresh_token` e invalida el anterior.

Body (`RefreshTokenRequest`):

```json
{
  "refreshToken": "v1.Mk2x...long-refresh-token"
}
```

Validación: `refreshToken` es `@NotBlank` -> `400` con `{ "refreshToken": "es obligatorio el uso de refreshToken para renovar el acceso" }` si está vacío/blanco/ausente.

Success `200`:

```json
{
  "token": "eyJhbGciOi...",
  "refreshToken": "v1.NuevoRefreshTokenRotado",
  "renovarTokenEn": 86400
}
```

Errores:

* `401 { "error": "El refresh token es inválido o expiró. Iniciá sesión nuevamente." }` cuando Auth0 responde `invalid_grant` / `invalid_request` / `Unknown or invalid refresh token` (token expirado, revocado o ya rotado). Mapeado por `AuthService.renovarTokenUsuario` -> `RefreshTokenInvalidoException` -> `GlobalExceptionHandler` `401`.
* `400` para otros fallos de Auth0 (configuración, red) con mensaje de `NoSePudoCrearUsuario`.

Frontend debe guardar `refreshToken` de login y de cada `/renovar` (rotación) y reemplazar el anterior. Ante `401` debe forzar re-login y descartar el refresh almacenado. No hay persistencia local de refresh en backend (stateless proxy a Auth0).

### Solicitar Token de Cambio de Contraseña

```http
POST /api/auth/cambio-contrasenia/solicitar-token
```

Público (no requiere Bearer). No enumera usuarios: siempre responde `200` genérico por privacidad.

Body (`SolicitarTokenCambioContraseniaRequest`):

```json
{
  "email": "user@example.com"
}
```

Validación: `@NotBlank @Email` -> `400` si formato inválido.

Success `200` (siempre, exista o no el usuario):

```json
{
  "message": "Si el correo existe, le enviamos un token al correo indicado, por favor, revise su mail e ingrese el token"
}
```

Cuando el email existe: valida rate limit (`pretriage.cambio-contrasenia.max-solicitudes-por-hora` en ventana `pretriage.cambio-contrasenia.ventana-horas`, defecto `3/1h`) -> `429 { "error": "Superaste el límite..." }` si se supera; invalida `PENDIENTE` previos a `INVALIDADO`, crea `CambioContraseniaToken` (`PENDIENTE`, `fechaHoraCreacion=now`, `fechaHoraExpiracion=now+pretriage.cambio-contrasenia.expiracion-minutos` defecto `15`), token opaco `TokenService` (SecureRandom 32 bytes base64url), y envía email vía `PasswordResetEmailPort` (`smtp`/`local`) con token, fecha de vencimiento `dd/MM/yyyy HH:mm America/Argentina/Buenos_Aires` y aviso "si no fuiste vos ignorá / no compartas". Cuando el email no existe: no crea token ni envía email, pero responde el mismo `200`.

El token se guarda en claro (plain) para que el aviso del email tenga sentido; no se hashea.

### Validar Token de Cambio de Contraseña

```http
GET /api/auth/cambio-contrasenia/validar?token={token}
```

Público. Valida estado y expiración.

Success `200`:

```json
{
  "valido": true,
  "message": "Token válido"
}
```

Errores `400 { "error": "el token no es válido o venció, por favor, solicite un nuevo token o revise si escribió bien el token" }` cuando: token inexistente, `estado != PENDIENTE` (`CAMBIADO`/`EXPIRO`/`INVALIDADO`), o `expiro()==true` (se marca `EXPIRO` y persiste). El frontend debe mostrar el error en rojo y ofrecer botón "solicitar nuevo token".

### Cambiar Contraseña

```http
POST /api/auth/cambio-contrasenia
```

Público. El frontend debe verificar que ambas contraseñas coincidan y enviar solo la nueva.

Body (`CambiarContraseniaRequest`):

```json
{
  "token": "43-char-base64url-token",
  "nuevaContrasenia": "NuevaPass123!"
}
```

Validación: `token @NotBlank`, `nuevaContrasenia @NotBlank @Size(8,72)` -> `400` con mapa de campos.

Success `200`:

```json
{
  "message": "Contraseña cambiada con éxito"
}
```

Flujo backend (`CambioContraseniaService.cambiarContraseña`): `obtenerCambioContraseniaToken` -> `validarToken` (`expiro()` -> `EXPIRO`) -> `llamarApiCambioContrasenia(auth0Id, nuevaPass)` (`PATCH /api/v2/users/{auth0Id}` con `{ "password": nuevaPass, "connection": "Username-Password-Authentication" }` y M2M `Bearer` de `AuthService` pattern `client_credentials` -> `update:users` scope). Mapea `PasswordStrengthError` -> `400` con mensaje amigable. Si OK: `estado=CAMBIADO`, persiste, `INVALIDADO` para otros `PENDIENTE` del usuario. El backend toma `auth0Id` del token, no del body.

Errores:

* `400 { "error": "el token no es válido o venció..." }` (mismo que validar)
* `400 { "error": "La contraseña es demasiado débil..." }`
* `500/400 { "error": "No se pudo cambiar la contraseña..." }` para fallos Auth0 de red/config.

Luego el frontend redirige a login con mensaje de éxito.

Config (`application.properties`):

```properties
pretriage.cambio-contrasenia.expiracion-minutos=${PRETRIAGE_CAMBIO_CONTRASENIA_EXPIRACION:15}
pretriage.cambio-contrasenia.max-solicitudes-por-hora=${PRETRIAGE_CAMBIO_MAX_POR_HORA:3}
pretriage.cambio-contrasenia.ventana-horas=${PRETRIAGE_CAMBIO_VENTANA_HORAS:1}
pretriage.cambio-contrasenia.email.mode=${PRETRIAGE_CAMBIO_EMAIL_MODE:${PRETRIAGE_INVITATIONS_EMAIL_MODE:local}}
pretriage.cambio-contrasenia.email.from=${PRETRIAGE_CAMBIO_EMAIL_FROM:${PRETRIAGE_INVITATIONS_EMAIL_FROM:no-reply@pretriage.local}}
```

Endpoints públicos en `SpringSecurityConfig` (`/api/auth/cambio-contrasenia/**`).

## Specialties

### List Specialties

```http
GET /api/especialidades
```

Returns available medical specialties sorted by name.

## Hospitals

### Nearby Hospitals Filtered By Specialty

```http
GET /api/hospitales/cercanos?latitud=-34.6&longitud=-58.4&codigoEspecialidad=CLINICA_MEDICA&transporte=transporte-publico&ordenarPor=distancia
```

Returns nearby hospitals that support the selected specialty. Only hospitals available for attention (`disponible=true` — at least one `SesionAtencionMedica.ACTIVA` for that specialty) are returned; an empty list means the frontend must show "no hay hospitales disponibles" and is not an error. Each hospital includes `tiempoEstimadoArriboMejorRuta` (estimated arrival time of the best route for the transport mode; `transporte` is optional, defaults to `transporte-publico`, and the field is null when no route can be computed) plus the estimated attention wait for a new arrival: `pacientesEnCola`, `minutosEsperaEstimados`, `fechaHoraAtencionEstimada`, `disponible` (always `true` in the response). `ordenarPor` is optional, defaults to `distancia` (Google Places proximity); valid values live in `AtencionHospitalService.ORDENES_VALIDOS` (`distancia`, `tiempo-atencion` y combinados `distancia|tiempo-atencion`/`tiempo-atencion|distancia`, orden indistinto, extensible con `&`); `tiempo-atencion` orders by lower `minutosEsperaEstimados` (see `docs/04-queue-and-estimation.md#end-of-queue-estimate-for-hospital-ranking`), tie-broken by arrival time then name; el combinado ordena por suma de rankings (distancia Google + tiempo). Invalid values return `400`. `transporte` valid values: `transporte-publico`, `vehiculo`, `vehiculo-dos-ruedas`, `caminar`, `bicicleta` (see `docs/12-hospital-selection-and-arrival.md`).

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

Returns `204 No Content`. The dynamic estimate is available via `GET /api/atencion/tiempo-estimado` (`TiempoEstimadoAtencionResponse` with `consultaId`, `fechaHoraAtencionEstimada`, `posicionEnCola`, `pacientesAntes`, `minutosPromedioAtencion`, `hayMedicosActivos`, `medicosActivos`, `medicosParaEstimacion`, `sectorId`, `nombreSector` from `ConsultaMedica.sector`, `codigoSala` from `Sala.nombre` — `null` until the doctor calls the patient — and `mensaje` when `hayMedicosActivos=false`).

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

Returns dynamic estimate based on `EntradaCola` (`EN_COLA` only, ordered by `prioridad DESC`, `ordenRelativo ASC`, `fechaHoraIngreso ASC`) and active doctor sessions (`SesionAtencionMedica` `ACTIVA`; if none, estimates with one virtual doctor and `hayMedicosActivos=false`). Response is `TiempoEstimadoAtencionResponse` with `consultaId`, `fechaHoraAtencionEstimada`, `posicionEnCola`, `pacientesAntes`, `minutosPromedioAtencion`, `hayMedicosActivos`, `medicosActivos`, `medicosParaEstimacion`, `sectorId`, `nombreSector` (`ConsultaMedica.sector`), `codigoSala` (`Sala.nombre`, `null` until `LLAMADO`/`EN_ATENCION` when a room is assigned), and `mensaje` when no doctors are active. See also `POST /api/atencion/hospital` which returns the same payload at queue entry.

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

All endpoints in this section are under `PacienteEsperaController` (`EsperaPacienteService`) and operate on the active `EntradaCola` of the authenticated patient. Every response is `EstadoConsultaPacienteDTO` (`consultaId`, `estadoConsulta`, `estadoEntradaCola`, `tipoPausa`, `fechaHoraLimiteRespuesta`, `sectorId`, `nombreSector` from `ConsultaMedica.sector`, `tiempoEstimadoAtencion` which is non-null only when `estadoEntradaCola == EN_COLA`).

```http
GET /api/paciente/consulta/estado
```

Returns the current queue/consultation state for the authenticated patient (no state change). Valid for `EN_COLA`, `LLAMADO`, `EN_ESPERA`, `ATRASADO`, `EN_ATENCION` (see `EsperaPacienteService.obtenerEntradaActivaPaciente`). When the entry is `EN_COLA`, the payload includes the dynamic estimate from `EstimacionAtencionService.calcularPara` (`TiempoEstimadoAtencionResponse`). Example: `docs/04-queue-and-estimation.md`.

### Pausar Cola Manualmente (salida voluntaria)

```http
POST /api/paciente/consulta/cola/pausa-manual
```

Patient voluntarily leaves the queue. Requires `EntradaCola.EN_COLA`, transitions to `EN_ESPERA` with `TipoPausaCola.ESPERA_MANUAL`, sets `ConsultaMedica.EN_ESPERA` and `fechaHoraSalidaTemporal=now`. While `EN_ESPERA` the entry does not count for estimation (`docs/04-queue-and-estimation.md#entries-counted-for-estimation`). Automatically cancelled to `CANCELADA` after 60 minutes without return (`EsperaPacienteService.cancelarEsperasVencidas`, `MINUTOS_MAXIMOS_EN_ESPERA=60`). Return path: `POST /api/paciente/consulta/cola/reincorporar` restores the previous relative position (`docs/02-patient-flow.md#waiting-and-absence-rules`). Replaces the former `POST /api/paciente/consulta/ausentarme`. No body; authenticated by `Jwt.getSubject()`.

### Confirmar Atraso (tras ser marcado ausente)

```http
POST /api/paciente/consulta/cola/atraso/confirmar
```

Patient confirms they are delayed after the doctor marked them absent on call. Requires `EN_ESPERA` with `AUSENTE_AL_LLAMADO`, transitions to `ATRASADO` with `ATRASADO_CONFIRMADO`, sets `ConsultaMedica.ATRASADO`, `fechaHoraUltimaRepregunta=now`, `fechaHoraLimiteRespuesta=now+30m` (`MINUTOS_REPREGUNTA_ATRASADO=30`). While `ATRASADO` the entry does not count for estimation. Automatically cancelled after 30 minutes without renewal (`EsperaPacienteService.cancelarAtrasadosSinRespuesta`). Replaces `POST /api/paciente/consulta/estoy-atrasado`.

### Renovar Confirmación de Atraso (sigo asistiendo)

```http
POST /api/paciente/consulta/cola/atraso/renovar
```

Patient still intends to attend while in `ATRASADO`. Requires `ATRASADO` (`ATRASADO_CONFIRMADO`), extends `fechaHoraUltimaRepregunta=now` and `fechaHoraLimiteRespuesta=now+30m`. Does not change `estado`/`tipoPausa`. If not renewed within 30 minutes the entry is cancelled. Replaces `POST /api/paciente/consulta/sigo-asistiendo`.

### Reincorporarse a la Cola (confirmar llegada)

```http
POST /api/paciente/consulta/cola/reincorporar
```

Patient physically arrives and re-enters `EN_COLA`. Accepts two origins (both documented in `docs/02-patient-flow.md` and `docs/12-hospital-selection-and-arrival.md`):
* From `ATRASADO` → `EN_COLA` at **first place within its priority level** (`ordenRelativo = min(ordenRelativo) - 1` for that `prioridad` via `obtenerOrdenParaPrimerLugarDePrioridad`), clears `tipoPausa`, `fechaHoraLimiteRespuesta`, resets `fechaHoraIngreso=now`, `ConsultaMedica.EN_COLA`.
* From `EN_ESPERA(ESPERA_MANUAL)` → `EN_COLA` restoring the **previous relative position** (keeps `ordenRelativo`).
Other states are rejected with `IllegalStateException`. On success `tiempoEstimadoAtencion` is recalculated dynamically. Replaces `POST /api/paciente/consulta/llegue`.

### Cancel Hospital Selection

```http
POST /api/paciente/consulta/cancelar
```

Patient voluntarily and definitively abandons their **active hospital selection** at any point of the attention flow (`EsperaPacienteService.cancelarSeleccion`). The client requests confirmation (dialog) before calling this endpoint, which is **idempotent** and safe to retry.

Allowed `EntradaCola` states (`ESPERA_PACIENTE`/`ESTADOS_ENTRADA_PACIENTE`):

* `EN_COLA`, `LLAMADO`, `EN_ESPERA`, `ATRASADO` → entry transitions to `CANCELADA` (clears `tipoPausa`, `fechaHoraLimiteRespuesta`, `fechaHoraUltimaRepregunta`); `ConsultaMedica` transitions to `CANCELADA` (clears `medico` and `sala`). The entry stops counting for estimation and availability, the doctor can no longer call or re-see the patient (`listarPacientesDisponibles`/`llamarProximo` only read `EN_COLA`, `obtenerSesionActual` only `LLAMADO`/`EN_ATENCION`), and any open AI triage chat is finalized (`RepoChat.findFirstByPacienteUsuarioAuthIdAndFinalizadoFalse`).
* `CANCELADA` → no-op success (idempotent retry).

Rejected with `409 ConflictoDeEstadoException`:

* `EN_ATENCION` → `"No puede cancelar una consulta con atencion en curso"` (consultation in progress).
* `FINALIZADA` → `"La consulta ya finalizo y no se puede cancelar"`.

Rejected with `400 NoSuchElementException` when the patient has no active hospital selection (e.g. only a `PENDIENTE` consultation or no record). No body; authenticated by `Jwt.getSubject()`. Returns `EstadoConsultaPacienteDTO` (`estadoEntradaCola=CANCELADA`, `estadoConsulta=CANCELADA`, `tiempoEstimadoAtencion=null`).

## Doctor

### List Assignments

```http
GET /api/medico/asignaciones
```

Returns the authenticated doctor's hospital and specialty assignments.

### List Sectors

```http
GET /api/hospitales/{hospitalId}/sectores?codigoEspecialidad={codigoEspecialidad}
```

Returns the active sectors (`Sector.activa=true`) of a hospital for the given specialty, ordered by name ASC.

### List Rooms

```http
GET /api/hospitales/{hospitalId}/sectores/{sectorId}/salas?codigoEspecialidad={codigoEspecialidad}
```

Returns the active rooms of the given sector for the given specialty.

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

Doctor selects hospital/specialty/sector/room (`hospitalId`, `codigoEspecialidad`, `sectorId` and `salaId` in the request body).

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
GET /api/medico/sesiones/{sesionId}/pacientes-disponibles?dni={dni}
```

Returns ordered `EntradaCola.EN_COLA` consultations for the session hospital, specialty and sector,
including the effective priority, patient name and surname, document (`numeroDocumento`/`tipoDocumento`) and `estadoConsulta`. For queued patients the effective priority is the preliminary backend classification. Room fields remain null until the consultation is called.

`dni` is optional. When provided, the backend filters by exact match on `Paciente.numeroDocumento` (trimmed, `String` exact equals) within the same hospital/specialty/sector queue and ordered by `prioridad DESC, ordenRelativo ASC, fechaHoraIngreso ASC`. The filtering is executed at DB level via `RepoEntradasCola.findByGestorDeColaIdAndEstadoAndConsultaMedicaPacienteNumeroDocumento...` for acceptable response times. Blank or missing `dni` returns the full ordered queue. Non-matching `dni` returns `[]` (empty JSON array, `200 OK`) — the frontend must show the “no coincidences” message.

Example: `GET /api/medico/sesiones/42/pacientes-disponibles?dni=30111222`

Response item (`ConsultaLlamadaDTO`):

```json
{
  "consultaId": 5,
  "codigoLlamado": "A-005",
  "pacienteId": 4,
  "nombrePaciente": "Juan",
  "apellidoPaciente": "Perez",
  "numeroDocumento": "30111222",
  "tipoDocumento": "DNI",
  "prioridad": "URGENTE",
  "estadoConsulta": "EN_COLA"
}
```

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

Returns the hospital's enabled specialties, rooms and sectors. Response is `ConfiguracionHospitalResponse` with `especialidades`, `salas` and `sectores` (`SectorHospitalResponse` list, ordered by `nombre` ASC).

Example `sectores` item:

```json
{
  "id": 10,
  "nombre": "Sector Rojo",
  "especialidadId": 1,
  "especialidadCodigo": "CLINICA_MEDICA",
  "especialidadNombre": "Clínica médica"
}
```

### Enable or Disable Specialty

```http
POST   /api/admin/hospitales/{hospitalId}/configuracion/especialidades/{especialidadId}
DELETE /api/admin/hospitales/{hospitalId}/configuracion/sectores/{sectorId}/especialidades/{especialidadId}
```

`DELETE` / deshabilitar is scoped to a sector: it only blocks when the specialty still has active rooms inside that sector (`RepoSalas.existsByHospitalIdAndEspecialidadIdAndSectorIdAndActivaTrue`). Returns `ConfiguracionHospitalResponse`.

### Create Room

```http
POST /api/admin/hospitales/{hospitalId}/configuracion/sectores/{sectorId}/salas
```

Rooms are created inside a sector, so `{sectorId}` is required in the path. The room is assigned to that sector and its specialty must match the sector's specialty (`409 { "error": "La especialidad de la sala debe coincidir con la especialidad del sector" }`). Room name uniqueness is now `hospitalId + sectorId + nombre` case-insensitive (`RepoSalas.existsByHospitalIdAndSectorIdAndNombreIgnoreCase`) -> `409 { "error": "Ya existe una sala con ese nombre en el sector" }`.

Body:

```json
{
  "nombre": "Consultorio 3",
  "especialidadId": 1
}
```

### Update Room

```http
PUT /api/admin/hospitales/{hospitalId}/configuracion/sectores/{sectorId}/salas/{salaId}
```

Uses the same body as room creation. The room must belong to the sector in the URL (`404 { "error": "La sala no pertenece al sector" }`) and its specialty must match the sector's specialty. Name uniqueness is `hospitalId + sectorId + nombre` excluding self (`RepoSalas.existsByHospitalIdAndNombreIgnoreCaseAndSectorIdAndIdNot`) -> `409`.

### Activate or Deactivate Room

```http
PATCH /api/admin/hospitales/{hospitalId}/configuracion/sectores/{sectorId}/salas/{salaId}/estado
```

The room must belong to the sector in the URL; activating re-validates that the room's specialty is still enabled for the hospital.

Body:

```json
{
  "activa": false
}
```

Room responses (`SalaHospitalResponse`) include the sector: `sectorId` and `sectorNombre` in addition to the specialty fields.

### Create Sector

```http
POST /api/admin/hospitales/{hospitalId}/configuracion/sectores
```

Hospital admin only (`ADMIN_HOSPITAL` via `StaffAccessService.exigirAdminHospital`). Creates a sector that groups rooms of a single specialty. The specialty must already be enabled for the hospital; otherwise `409`.

Body (`GuardarSectorRequest`):

```json
{
  "nombre": "Sector Rojo",
  "especialidadId": 1
}
```

Validation: `nombre` `@NotBlank @Size(max=100)` (trimmed before uniqueness check), `especialidadId` `@NotNull` -> `400` with field map on violation. Uniqueness is `hospitalId + nombre` case-insensitive (`RepoSectores.existsByHospitalIdAndNombreIgnoreCase`) -> `409 { "error": "Ya existe un sector con ese nombre en el hospital" }`. `404` if hospital or specialty not found. Audits `SECTOR_CREADO` via `AuditoriaHospital`.

Success `200` (`SectorHospitalResponse`):

```json
{
  "id": 10,
  "nombre": "Sector Rojo",
  "activa": true,
  "especialidadId": 1,
  "especialidadCodigo": "CLINICA_MEDICA",
  "especialidadNombre": "Clínica médica"
}
```

### Update Sector

```http
PUT /api/admin/hospitales/{hospitalId}/configuracion/sectores/{sectorId}
```

Hospital admin only. Updates `nombre`, `especialidad` and `activa`. Specialty must be enabled for the hospital. Name uniqueness is `hospitalId + nombre` case-insensitive excluding self (`RepoSectores.existsByHospitalIdAndNombreIgnoreCaseAndIdNot`) -> `409`. Trimmed before check.

Body (`ActualizarSectorRequest`):

```json
{
  "nombre": "Sector Rojo - Ala Norte",
  "especialidadId": 1,
  "activa": true
}
```

Validation: `nombre` `@NotBlank @Size(max=100)`, `especialidadId` `@NotNull`, `activa` `@NotNull` -> `400`. `404` if hospital/sector/specialty not found or sector does not belong to hospital (`findByIdAndHospitalId`). If any `Sala` of the sector has `ConsultaMedica` with `estadoConsulta NOT IN (FINALIZADA,CANCELADA)` (`RepoConsultasMedicas.existsBySalaIdInAndEstadoConsultaNotIn`) -> `409 { "error": "No se puede modificar/eliminar un sector con salas que aún tienen pacientes asignados" }`. Audits `SECTOR_ACTUALIZADO`.

Success `200` (`SectorHospitalResponse` with updated `activa`).

### Delete Sector

```http
DELETE /api/admin/hospitales/{hospitalId}/configuracion/sectores/{sectorId}
```

Hospital admin only. Returns `204 No Content` on success.

Blocking rules (both checked, `409` if violated):
* Same patient check as `PUT`: if any `Sala` of the sector has patients (`existsBySalaIdInAndEstadoConsultaNotIn`) -> `409` same message.
* If any `Sala` of the sector has `SesionAtencionMedica` with `estado IN (ACTIVA, PAUSADA)` (`RepoSesionesAtencionMedica.existsBySalaIdInAndEstadoIn`) -> `409 { "error": "No se puede eliminar un sector con sesiones de atención activas o pausadas" }`.

If sector has empty salas (no patients/sessions), `DELETE` desvincula salas (`sala.setSector(null)`) and deletes sector. `404` if sector not in hospital. Audits `SECTOR_ELIMINADO`.
