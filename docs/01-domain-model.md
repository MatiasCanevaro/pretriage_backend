# Domain Model

## Patient And Consultation

`Paciente` represents either an authenticated patient profile or a reception-created patient.
Reception-created patients store their identity fields directly plus `telefono`, optional
`correoElectronico`, and a structured one-to-one `Direccion`.

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

## Hospitals And Specialties

`EspecialidadMedica` stores a code and display name for each specialty.

`Hospital` has many specialties. A patient can select only hospitals that support the chosen specialty.

`Sala` belongs to a hospital and specialty. A room can be used by one doctor session at a time.

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
- `fechaHoraLimiteRespuesta`

Relevant states in `EstadoEntradaCola`:

- `EN_COLA`
- `LLAMADO`
- `EN_ESPERA`
- `ATRASADO`
- `EN_ATENCION`
- `FINALIZADA`
- `CANCELADA`

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
