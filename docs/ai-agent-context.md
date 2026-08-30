# AI Agent Context

This file gives future AI agents enough context to work on the project without rediscovering the domain from scratch.

## Core Intent

The system manages the first medical attention workflow:

1. Patient chooses medical specialty.
2. Patient chooses hospital that supports that specialty and enters the hospital/specialty queue with default priority.
3. Optional AI triage collects symptoms and assigns priority, updating the queue priority.
4. Doctors start sessions in rooms and call patients.
5. Estimated attention time is recalculated dynamically.

## High Risk Areas

- Queue state and estimation.
- Doctor session concurrency.
- Patient absence and delayed return rules.
- AI triage priority mapping.
- Auth0 token handling in local E2E scripts.
- Auth refresh with rotation (`AuthService.renovarTokenUsuario`, `RefreshTokenRequest`, `RefreshTokenInvalidoException` -> `401`).

## Auth

- `AuthController` (`/api/register`, `/api/login`, `/api/renovar`): `renovar` is public (`SpringSecurityConfig:34`), validates `@NotBlank refreshToken`, delegates to `AuthService.renovarTokenUsuario` (`grant_type=refresh_token`).
- `AuthService`: `obtenerTokenParaLogearUsuario` requests `offline_access` to obtain rotating `refresh_token`; `renovarTokenUsuario` proxies to Auth0 `/oauth/token` and maps `AuthIdTokenResponse` -> `LoginResponseDTO` (`token`, `refreshToken`, `renovarTokenEn`). Rotation invalidates previous refresh; `invalid_grant`/`invalid_request` maps to `RefreshTokenInvalidoException` (`GlobalExceptionHandler` `401`). Stateless, no DB persistence for refresh.
- Invariants: frontend must replace stored `refreshToken` on each renovar; on `401` force re-login. Keep `LoginResponseDTO.renovarTokenEn` from `expires_in`.
- Password reset: `CambioContraseniaController` (`POST /api/auth/cambio-contrasenia/solicitar-token` `SolicitarTokenCambioContraseniaRequest`, `GET /api/auth/cambio-contrasenia/validar?token=`, `POST /api/auth/cambio-contrasenia` `CambiarContraseniaRequest`) all public (`SpringSecurityConfig`). `CambioContraseniaService` owns `obtenerTokenCambioContraseña` (always `200` generic for privacy, `existsByCorreoElectronicoIgnoreCase` + rate limit `countByUsuarioIdAndFechaHoraCreacionAfter` vs `pretriage.cambio-contrasenia.max-solicitudes-por-hora/ventana-horas`, invalidate prior `PENDIENTE` -> `INVALIDADO`, `TokenService.generarToken` SecureRandom 32B base64url, `PasswordResetEmailPort` smtp/local), `validarTokenCambioContrasenia` (`expiro()` -> `EXPIRO`), `cambiarContraseña` (`PATCH /api/v2/users/{auth0Id}` with M2M `client_credentials`. Entities: `CambioContraseniaToken` (`@ManyToOne UsuarioAuth`, `token unique plain`, `fechaHoraCreacion/Expiracion`, `EstadoCambioContrasenia PENDIENTE/CAMBIADO/EXPIRO/INVALIDADO`) in `UsuarioAuth.cambiosDeContrasenia`. Exceptions: `TokenCambioContraseniaInvalidoException` `400`, `LimiteSolicitudesCambioContraseniaException` `429`, `NoSePudoCambiarContraseniaException` `400`.

## Optional Integrations

- Amazon S3 is disabled by default through
  `pretriage.storage.s3.enabled=false`.
- Local startup must not require AWS credentials.
- Set `PRETRIAGE_STORAGE_S3_ENABLED=true` together with `AWS_S3_REGION`,
  `AWS_ACCESS_KEY_ID`, and `AWS_SECRET_ACCESS_KEY` only when clinical-study file
  downloads are required.

## Source Files By Concern

### AI Chat

- `ChatService`
- `ChatBotController`
- `Chat`
- `Mensaje`
- `TriageResultDTO`

### Hospital And Specialty

- `AtencionHospitalService` (incl. `buscarHospitalesCercanos` with `ordenarPor` and availability filter)
- `HospitalController`
- `Hospital`
- `EspecialidadMedica`
- `RepoHospitales`
- `RepoEspecialidadesMedicas`

### Queue And Estimation

- `EstimacionAtencionService` (`calcularPara` per-patient + `calcularEsperaParaNuevaConsulta` for hospital ranking)
- `EntradaCola`
- `EstadoEntradaCola`
- `GestorDeCola`
- `RepoEntradasCola` (`countByGestorDeColaHospitalIdAndGestorDeColaEspecialidadIdAndEstado`)
- `TiempoEstimadoAtencionResponse` + `EsperaNuevaConsultaCalculo`
 - `HospitalCercanoDTO` enriched with `pacientesEnCola`, `minutosEsperaEstimados`, `fechaHoraAtencionEstimada`, `disponible`

### Doctor Attention

- `AtencionMedicoService`
- `MedicoController`
- `SesionAtencionMedica`
- `EstadoSesionMedica`
- `Sala`
- `AsignacionMedicoHospital`
- `RepoSesionesAtencionMedica`
- `AtencionMedica`
- `EstadoAtencionMedica`
- `RepoAtencionesMedicas`
- `TiempoEstimadoNotifier`

### Patient Waiting State

- `EsperaPacienteService`
- `PacienteEsperaController`
- `EstadoConsultaPacienteDTO`
- `TipoPausaCola`

## Invariants

- `EntradaCola` is the queue source of truth.
- A queue is scoped by hospital and specialty.
- Hospital selection enters the consultation into the queue directly; the AI triage is optional and only updates the queue priority.
- Estimated attention time is dynamic and should be recalculated on every request.
- Only `EntradaCola.EN_COLA` counts for waiting estimation (both per-patient and per-hospital ranking; never `GestorDeCola.consultasEnEspera`).
- Doctor sessions count for capacity only when `EstadoSesionMedica.ACTIVA`.
- Nearby hospitals ranking shows only hospitals with `medicosActivos > 0` (`disponible=true`); an empty result means "no hay hospitales disponibles". Ranking wait uses end-of-queue formula `pacientesEnCola / max(medicosActivos,1) * minutosPromedioAtencion`. `ordenarPor` valid values live in `ORDENES_VALIDOS` (`distancia`, `tiempo-atencion`, combinados con `&`), orden indistinto; el combinado usa suma de rankings.
- Paused sessions do not count as active capacity.
- Zero active doctors still yields an estimate using one virtual doctor, but response must indicate no active doctors.
- A room cannot have two active or paused sessions at the same time.
- A doctor cannot have two active or paused sessions at the same time.
- A patient delayed after absence is not reinserted into queue until marking arrival.
- A paused session reserves doctor and room but does not count as active capacity.
- A session cannot be paused or closed while its doctor has a called or in-attention consultation.
- A doctor cannot call another patient while one is called or in attention.
- `AtencionMedica` is created on presence confirmation and finalized with the consultation.
- `EN_ESPERA` entries are cancelled after one hour measured from `fechaHoraSalidaTemporal`.
- SSE subscriptions validate that the authenticated patient owns the consultation.

## Estimation Contract

`TiempoEstimadoAtencionResponse` keeps the original field:

- `fechaHoraAtencionEstimada`

It also exposes operational metadata:

- `hayMedicosActivos`
- `medicosActivos`
- `medicosParaEstimacion`
- `posicionEnCola`
- `pacientesAntes`
- `minutosPromedioAtencion`
- `mensaje`

Do not remove `fechaHoraAtencionEstimada` because existing clients may depend on it.

## Verification Strategy

For queue or estimation changes:

```powershell
.\mvnw.cmd "-Dtest=AtencionHospitalServiceTest,EstimacionAtencionServiceTest" test
```

For chat behavior changes, run real E2E:

```powershell
python scripts\e2e_chat.py --messages-file scripts\chat_case_example.txt --debug-log scripts\debug_case.json
```

For full confidence:

```powershell
.\mvnw.cmd test
```

Full tests need Docker Desktop access.

## Common Mistakes To Avoid

- Do not calculate estimated time from `GestorDeCola.consultasEnEspera`.
- Do not persist estimated attention time as final truth.
- Do not count paused doctors as active capacity.
- Do not count delayed or manually waiting patients as in queue.
- Do not add admin module assumptions yet; admin is planned but not in scope.
- Do not expose raw `.env` values in logs or docs.

## Documentation Maintenance

Documentation updates are required in the same change as code updates. When changing entities, states, flows, endpoints, DTOs, configuration, or verification commands:

1. Update the relevant narrative files under `docs/`.
2. Regenerate the domain diagram with `python scripts/generate_domain_diagram.py` after JPA entity changes.
3. Verify API paths and state transitions against controllers and services.
4. Do not consider the task complete while documentation is stale.
## Reception-Assisted Admission

- Reception admission uses `AdmisionRecepcion`, `SesionRecepcion`, `TriageFormularioService`, and `IngresoColaService`.
- It does not create or use `Chat`.
- DNI is required; patients without Auth0 are valid domain patients.
- Receptionists may be assigned to multiple hospitals but can have only one active reception session.
- Reception cannot set or edit priority.
- Both digital and reception flows must enter the queue through `IngresoColaService`.
- Keep `docs/09-reception-admission.md` synchronized with backend contracts.
