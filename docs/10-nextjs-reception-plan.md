# Next.js Reception Frontend Plan

## Recommended Stack

- Next.js App Router with TypeScript.
- Auth0 SDK for Next.js; backend JWT remains the authority.
- React Hook Form + Zod for local validation.
- TanStack Query for server state, retries, and invalidation.
- A small Zustand store or URL state only for the active admission wizard; do not duplicate API server state globally.

## Routes

```text
/recepcion                         session bootstrap and hospital selection
/recepcion/admisiones/nueva        DNI and patient identity
/recepcion/admisiones/[id]/triage  structured clinical form
/recepcion/admisiones/[id]/resultado priority, code, position, estimate
```

Protect the whole `/recepcion` segment. Redirect users without receptionist access after a 403 from `/api/recepcion/hospitales`.

## Bootstrap

On layout load, request in parallel:

```http
GET /api/recepcion/hospitales
GET /api/recepcion/sesiones/activa
```

- If there is an active session, show hospital name and `Nueva admision`.
- Otherwise show assigned hospital cards and start with `POST /api/recepcion/sesiones`.
- Do not store the selected hospital separately from the returned session.

Start-session payload:

```json
{ "hospitalId": 10 }
```

## Admission Wizard

### Step 1: DNI

Use a numeric input with 7-8 digits. On blur or submit:

```http
GET /api/recepcion/pacientes/30111222?sesionId=5
```

- `200`: prefill patient fields and mark them as an existing patient.
- `404`: enable new-patient fields.
- Do not interpret 404 as a UI error.

### Step 2: Patient and specialty

```json
{
  "sesionId": 5,
  "dni": "30111222",
  "nombre": "Ana",
  "apellido": "Perez",
  "fechaNacimiento": "1990-05-10",
  "generoBiologico": "FEMENINO",
  "codigoEspecialidad": "CLINICA_MEDICA"
}
```

Send to `POST /api/recepcion/admisiones`. Specialty options come only from the active hospital DTO. On success, navigate using the returned admission ID.

### Step 3: Structured triage form

Sections:

1. Main complaint and symptoms.
2. Onset and evolution.
3. Pain intensity and location.
4. Fever and alarm signs.
5. Background, medication, allergies, pregnancy possibility.
6. Observations and review.

Use checkboxes for common symptoms/alarm signs plus an `Otro` text field. Keep free text short. Show an always-visible summary panel on desktop and a review step on mobile.

Final payload:

```json
{
  "motivoConsulta": "Dolor abdominal",
  "sintomas": ["dolor", "nauseas"],
  "inicio": "Hace dos horas",
  "evolucion": "EMPEORA",
  "intensidadDolor": 7,
  "localizacionDolor": "Abdomen inferior",
  "fiebre": false,
  "signosAlarma": [],
  "antecedentesRelevantes": [],
  "medicamentos": [],
  "alergias": [],
  "posibilidadEmbarazo": "NO",
  "observaciones": ""
}
```

Send once to:

```http
POST /api/recepcion/admisiones/{id}/finalizar
```

Disable the submit button while pending. Do not implement a priority input. Display the returned priority read-only.

## Result Screen

Show:

- Anonymous `codigoLlamado` in very large text.
- Specialty.
- Queue position and estimated time.
- Read-only priority for staff.
- `Nueva admision` action.

Do not show clinical data on the hospital public screen. A later display client should receive only code, room, and call time.

## Error Handling

- `400`: map validation errors to fields.
- `401/403`: return to authentication or hospital selection.
- `409`: active consultation/admission conflict; show a blocking dialog.
- Network/AI timeout: keep local form data and allow retry. The backend may use fallback classification, so trust a successful response.
- Double submit: disable immediately and treat an already-finalized response as non-retriable until status retrieval is added.

## UX Targets

- Complete common admission in under three minutes.
- Keyboard-first navigation for reception desktops.
- Large touch targets for tablets.
- Autosave locally in `sessionStorage` keyed by admission ID until finalization; clear after success.
- Never store JWTs, DNI, or clinical forms in `localStorage`.
- Confirm before abandoning an incomplete admission.

## Suggested Frontend Types

```ts
type ReceptionSession = {
  id: number;
  hospitalId: number;
  hospitalNombre: string;
  estado: "ACTIVA" | "FINALIZADA";
  fechaHoraInicio: string;
  fechaHoraFin: string | null;
};

type AdmissionResult = {
  id: number;
  consultaId: number;
  pacienteId: number;
  codigoLlamado: string;
  estado: "INICIADA" | "FORMULARIO_COMPLETO" | "FINALIZADA" | "CANCELADA";
  prioridad: "RIESGO_VITAL_INMEDIATO" | "MUY_URGENTE" | "URGENTE" | "NORMAL" | "NO_URGENTE" | null;
  estimacion: TiempoEstimadoAtencion | null;
};
```

## Frontend Test Plan

- Unit: Zod schemas and DNI normalization.
- Component: existing vs new patient states.
- Component: priority is never editable.
- Integration with MSW: bootstrap, start session, create and finalize admission.
- E2E Playwright: receptionist login -> hospital -> DNI -> form -> result.
- E2E conflict: second active session and patient with active consultation.
- Accessibility: keyboard-only form and error announcements.
