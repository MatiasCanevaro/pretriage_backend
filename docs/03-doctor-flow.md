# Doctor Flow

## Main Flow

```mermaid
flowchart TD
    A[Doctor logs in] --> B[Sees assigned hospitals and specialties]
    B --> C[Chooses hospital and room]
    C --> D[Starts active session]
    D --> E[Calls next patient from queue]
    E --> F{Patient responds?}
    F -->|Yes| G[Patient enters attention]
    F -->|No| H[Doctor marks absent]
    G --> I[Doctor finishes attention]
    H --> J[Patient waiting/delayed flow]
```

## Session Rules

- A doctor is already assigned to hospitals and specialties by the admin domain.
- A doctor starts a session by choosing hospital, specialty, and room.
- A doctor cannot change specialty inside a session.
- To change specialty, close the current session and start another.
- A room can be used by one doctor at a time.
- A doctor can have one active or paused session at a time.
- A paused session continues reserving the doctor and room, although it does not count as estimation capacity.
- A doctor cannot pause or close a session while a consultation is LLAMADO or EN_ATENCION.
- A doctor cannot call another patient while a previous patient is still LLAMADO or EN_ATENCION.

## Session States

- `ACTIVA`: doctor is attending and counts for estimation.
- `PAUSADA`: doctor is temporarily absent and does not count for estimation.
- `FINALIZADA`: session closed.

## Calling Patients

When doctor calls next patient:

- Backend selects next `EntradaCola.EN_COLA` for the same hospital/specialty.
- Ordering is by priority descending and relative order ascending.
- Entry becomes `LLAMADO`.
- Consultation also becomes `LLAMADO`.

If patient appears:

- Entry becomes `EN_ATENCION`.
- Consultation becomes `EN_ATENCION`.
- Doctor, room, and session context are assigned.
- An `AtencionMedica.EN_CURSO` historical record is created and linked to the consultation and session.

If patient does not respond:

- Doctor marks absent manually.
- Entry becomes `EN_ESPERA`.
- `tipoPausa = AUSENTE_AL_LLAMADO`.
- Patient decides whether they are delayed or returning.

## Completing Attention

When attention finishes:

- `EntradaCola` and `ConsultaMedica` become `FINALIZADA`.
- The linked `AtencionMedica` becomes `FINALIZADA` and stores its end time.

## Queue Visibility And History

- `GET /api/medico/sesiones/{sesionId}/pacientes-disponibles` lists ordered `EN_COLA` patients for the session hospital and specialty.
- `GET /api/medico/atenciones` returns the authenticated doctor's historical attention records.

## Clinical History Access

When attending patients, doctors can access their medical records:

- `GET /api/medico/pacientes/{pacienteId}/historial-clinico` retrieves all medical records (PDFs, images) for a specific patient.
- `GET /api/medico/pacientes/{pacienteId}/historial-clinico/{historialId}/archivo` downloads or previews a specific medical record file.
- `GET /api/medico/pacientes/{pacienteId}/historial-clinico/{historialId}/reporte` retrieves structured information about a specific medical record.
- `GET /api/medico/pacientes/{pacienteId}/ultimos-reportes` gets the most recent medical records (typically last 5-10).