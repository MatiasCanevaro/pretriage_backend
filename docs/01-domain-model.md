# Domain Model

## Patient And Consultation

`Paciente` represents either an authenticated patient profile or a reception-created patient.
`OrigenRegistroPaciente` (`APLICACION` | `RECEPCION`) records the registration source.
Reception-created patients store their identity fields directly plus `telefono`, optional
`correoElectronico`, and a structured one-to-one `Direccion`. `UsuarioAuth` is optional for
reception-created patients.

`ConsultaMedica` represents the current first-attention workflow. Important fields:

- `paciente`
- `hospital`
- `especialidad`
- `estadoConsulta`
- `nivelDeGravedadBot`
- `medico`
- `sala`

Relevant states in `EstadoConsulta`:

- `PENDIENTE`
- `HOSPITAL_SELECCIONADO`
- `PRETRIAGE_EN_PROCESO`
- `PRETRIAGE_FINALIZADO`
- `EN_COLA`
- `LLAMADO`
- `EN_ESPERA`
- `ATRASADO`
- `EN_ATENCION`
- `FINALIZADA`
- `CANCELADA`

## AI Triage Chat

`Chat` represents one AI pre-triage conversation. Important fields:

- `paciente`
- `mensajes`
- `fechaHoraCreacion`
- `finalizado`
- `resultadoTriageJson`

The structured triage result is stored as JSON in `resultadoTriageJson` and maps
to `ConsultaMedica.nivelDeGravedadBot` when the triage finishes.

`Mensaje` is one chat message. Important fields:

- `contenido`
- `autor`
- `pacienteAutor`
- `fechaHoraEnvio`

`autor` values in `AutorMensaje`:

- `BOT`
- `PACIENTE`

`ConsultaMedica.sintomasBot` is a many-to-many reference to `Sintoma`, the
catalog of symptom names identified during triage.

## Hospitals And Specialties

`EspecialidadMedica` stores a code and display name for each specialty.

`Hospital` has many specialties. A patient can select only hospitals that support the chosen specialty.

`Sala` belongs to a hospital and specialty. A room can be used by one doctor session at a time.

## Health Insurance Credentials

`ObraSocial` is the health insurance catalog. Important fields:

- `nombre`
- `virgente`

`virgente = false` is the logical delete state used by the admin CRUD.

`Credencial` stores one patient's health insurance credential. Important fields:

- `numeroAfiliado`
- `plan`
- `fechaVencimiento`
- `obraSocial`
- `paciente`

Load and update validate the credential at runtime through
`FabricaValidadoresCredencialesObraSocial`, which resolves the validator for the
requested obra social (`ValidadorCredencialObraSocial`). Only the demo mock
(`OSDE`) exists; real integrations are out of scope.

## Addresses And Coordinates

`Direccion` stores a structured address. Important fields:

- `calle`
- `altura`
- `piso`
- `codigoPostal`
- `ciudad`
- `provincia`
- `coordenada`

`Coordenada` stores a latitude/longitude pair. `Hospital` and `Paciente` own a
one-to-one `Direccion`; `Paciente` also keeps a `coordenadaActual`.

## Queue

`GestorDeCola` is the queue container per hospital and specialty.

`EntradaCola` is the source of truth for queue state. Important fields:

- `gestorDeCola`
- `consultaMedica`
- `estado`
- `tipoPausa`
- `prioridad`
- `ordenRelativo`
- `fechaHoraIngreso`
- `fechaHoraLlamado`
- `fechaHoraSalidaTemporal`
- `fechaHoraUltimaRepregunta`
- `fechaHoraLimiteRespuesta`

Relevant states in `EstadoEntradaCola`:

- `EN_COLA`
- `LLAMADO`
- `EN_ESPERA`
- `ATRASADO`
- `EN_ATENCION`
- `FINALIZADA`
- `CANCELADA`

`tipoPausa` values in `TipoPausaCola`:

- `AUSENTE_AL_LLAMADO`
- `ESPERA_MANUAL`
- `ATRASADO_CONFIRMADO`

## Doctor Attention

`Medico` is authenticated through `UsuarioAuth`.

`AsignacionMedicoHospital` associates a doctor with hospital and specialty.

`SesionAtencionMedica` represents the current doctor work session. Important fields:

- `medico`
- `hospital`
- `especialidad`
- `sala`
- `estado`
- `fechaHoraInicio`
- `fechaHoraPausa`
- `fechaHoraFin`

Session states:

- `ACTIVA`: counts as active doctor capacity.
- `PAUSADA`: does not count as active capacity.
- `FINALIZADA`: closed session.

## Historical Attention

`AtencionMedica` is the historical record of one effective patient attention. It is created when a called patient confirms presence and enters `EN_ATENCION`, and finalized together with the consultation. Important fields:

- `consultaMedica`
- `sesionAtencionMedica`
- `estado`
- `fechaHoraInicio`
- `fechaHoraFin`

One `SesionAtencionMedica` can contain many attention records. One `ConsultaMedica` has at most one `AtencionMedica` in the current first-attention flow. Doctor, hospital, specialty, and room historical context is obtained from the linked session.

Attention states:

- `EN_CURSO`
- `FINALIZADA`

## Priority Review

`RevisionPrioridadConsulta` is the append-only record of a priority review made
by a doctor during attention. Important fields:

- `consultaMedica`
- `medico`
- `decision`
- `prioridadAnterior`
- `prioridadNueva`
- `motivo`
- `fechaHora`

`decision` values in `DecisionRevisionPrioridad`:

- `CONFIRMAR`
- `CORREGIR`

The review state (`PENDIENTE`, `CONFIRMADA`, `CORREGIDA`) is derived from the
history, not persisted. The current effective value is stored in
`ConsultaMedica.nivelDeGravedadMedico`.

## Reception Admission

`SesionRecepcion` is the receptionist's working session at one assigned
hospital. Important fields:

- `recepcionista`
- `hospital`
- `estado`
- `fechaHoraInicio`
- `fechaHoraFin`

Session states in `EstadoSesionRecepcion`:

- `ACTIVA`
- `FINALIZADA`

`AdmisionRecepcion` stores one reception-assisted admission. It does not create
or use `Chat`. Important fields:

- `consultaMedica`
- `sesionRecepcion`
- `estado`
- `formularioJson`
- `resultadoTriageJson`
- `fechaHoraInicio`
- `fechaHoraFinalizacion`
- `fechaHoraCancelacion`

Admission states in `EstadoAdmisionRecepcion`:

- `INICIADA`
- `FORMULARIO_COMPLETO`
- `FINALIZADA`
- `CANCELADA`

## Staff Identity, Memberships And Invitations

`UsuarioAuth` is the local global identity linked to the Auth0 subject. It
stores name, document, email, and a platform-level `RolSistema` (`ADMIN` or
`USER`). `Medico`, `Recepcionista`, and `Paciente` profiles own a one-to-one
`UsuarioAuth`; patient profiles make it optional.

`CambioContraseniaToken` stores a password-reset token. Important fields:

- `usuario` (`UsuarioAuth`)
- `token` (opaque, `unique`, plain for email warning)
- `fechaHoraCreacion`
- `fechaHoraExpiracion`
- `estado`

States in `EstadoCambioContrasenia`:

- `PENDIENTE`
- `CAMBIADO`
- `EXPIRO`
- `INVALIDADO` (superseded by a newer `PENDIENTE` for the same user; not counted for validation)

`expiro()` checks `LocalDateTime.now().isAfter(fechaHoraExpiracion)`. Token lifetime and rate limit (`pretriage.cambio-contrasenia.expiracion-minutos`, `max-solicitudes-por-hora`, `ventana-horas`) are configurable via `application.properties`. `UsuarioAuth.cambiosDeContrasenia` is `@OneToMany(cascade=ALL)`.

`MembresiaHospital` links one global identity to one hospital. Important fields:

- `usuario`
- `hospital`
- `estado`
- `roles`
- `fechaCreacion`
- `fechaAceptacion`
- `fechaSuspension`
- `creadaPor`

The pair `(usuario, hospital)` is unique. States in `EstadoMembresiaHospital`:

- `INVITADA`
- `ACTIVA`
- `SUSPENDIDA`
- `REVOCADA`

Roles in `RolMembresiaHospital`:

- `ADMIN_HOSPITAL`
- `MEDICO`
- `RECEPCIONISTA`

`InvitacionHospital` is a single-use invitation to join a hospital. Important
fields:

- `hospital`
- `emailNormalizado`
- `estado`
- `rolesSolicitados`
- `especialidadIds`
- `tokenHash`
- `matricula`, `tipoMatricula`, `jurisdiccionMatricula`
- `venceEn`
- `fechaCreacion`
- `fechaAceptacion`
- `emailEnviado`, `ultimoIntentoEnvio`, `cantidadIntentosEnvio`
- `invitadaPor`
- `aceptadaPor`

Only the SHA-256 hash of the invitation secret is stored. States in
`EstadoInvitacionHospital`:

- `PENDIENTE`
- `ACEPTADA`
- `EXPIRADA`
- `REVOCADA`

`CredencialProfesional` stores a doctor's professional registration. Important
fields:

- `medico`
- `numero`
- `tipo`
- `jurisdiccion`
- `estado`

The tuple `(numero, tipo, jurisdiccion)` is unique. `tipo` values in
`TipoMatriculaProfesional`: `NACIONAL` | `PROVINCIAL`. `estado` values in
`EstadoCredencialProfesional`: `PENDIENTE_VERIFICACION` | `VERIFICADA` |
`SUSPENDIDA` | `VENCIDA`. National credentials are normalized to the `NACION`
jurisdiction.

`AsignacionMedicoHospital` associates a doctor with hospital and specialty. The
tuple `(medico, hospital, especialidad)` is unique.

`AuditoriaHospital` records privileged hospital changes. Important fields:

- `hospital`
- `actor`
- `fecha`
- `accion`
- `objetivo`
- `resultado`

## Medical Studies And File Management

`EstudioClinico` represents a medical study file (PDF, image, etc.) uploaded by a patient. Important fields:

- `paciente`
- `nombreArchivo`
- `tipoArchivo`
- `extensionArchivo`
- `descripcion`
- `fechaSubida`
- `tamanoArchivo`
- `rutaArchivo`
- `activo`

Files are stored in AWS S3 through `GestionDeArchivosService`. The entity uses soft delete (`activo = false`) to preserve history while removing from active views. Patients can upload, list, download, and delete their own studies. Doctors can view patient studies during attention.

## Generated Diagram

A Mermaid ER diagram can be generated from the JPA entity classes:

```powershell
python scripts\generate_domain_diagram.py
```

Generated files:

- `docs/generated/domain-model.md`
- `docs/generated/domain-model.mmd`
