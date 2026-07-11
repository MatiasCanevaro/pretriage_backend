# Next.js Staff Frontend Plan: Reception And Doctor

## Recommended Stack

- Next.js App Router with TypeScript.
- Auth0 SDK for Next.js; backend JWT remains the authority.
- React Hook Form + Zod for local validation.
- TanStack Query for server state, retries, and invalidation.
- A small Zustand store or URL state only for the active admission wizard; do not duplicate API server state globally.

## Handoff Bundle And Required Inputs

The frontend agent must receive:

- This complete plan copied as `docs/nextjs-staff-frontend-plan.md`.
- Backend repository URL, tested commit SHA, local startup command and environment-variable names
  without secret values.
- A generated `/v3/api-docs` snapshot from that commit.
- Access to a receptionist and doctor test account plus seeded hospital, specialty and room data.
- The backend doctor prerequisite contracts listed below, or linked blocker tasks.
- Logo source files and brand rules when ready.
- Node-specific Figma links and target viewports when mockups exist.

Unknown visual inputs do not block the engineering foundation. Unknown API contracts do block the
dependent production workflow. Authentication provider configuration, deployment URLs, logo
assets and Figma nodes remain repository/environment-specific placeholders until supplied.

## How To Produce The Handoff Materials

Create a temporary `frontend-handoff/` workspace:

```text
frontend-handoff/
  README.md
  brand/
    logo.svg
    logo-dark.svg
    logo-mark.svg
    favicon.svg
    brand-guidelines.md
  design/
    figma-index.md
    screen-matrix.md
    content-guide.md
  contract/
    pretriage-openapi.json
    backend-contract.md
    example-payloads/
  environment/
    env.example
    local-setup.md
    test-data.md
  prompt/
    frontend-agent-prompt.md
```

Do not place passwords, tokens, Auth0 secrets, credentialed URLs or real patient data here.

### 1. Product and audience brief

Write `README.md` before designing:

- Product purpose: hospital pretriage operations for receptionists and doctors.
- Main users: reception desktop/tablet staff and doctors working from hospital rooms.
- Success measures: admission under three minutes, safe queue transitions, refresh recovery and
  keyboard accessibility.
- In-scope and out-of-scope items from the screen inventory.
- Supported language, time zone, date/number formats, target devices and browsers.
- Links to the backend commit, Figma file and issue tracker.

Acceptance: a new collaborator can explain both workflows and scope without opening source code.

### 2. Prepare the logo and brand package

1. Locate the original vector source. Prefer SVG; do not trace a small raster into a fake vector.
2. Export the approved full logo for light backgrounds, dark-background variant when required,
   mark-only version, square favicon and PNG fallbacks only when needed.
3. Optimize SVGs while preserving paths and viewBox. Remove editor metadata and embedded raster
   content where possible.
4. Write `brand/brand-guidelines.md` with official name/capitalization, variant usage, minimum
   size, clear space, allowed backgrounds, HEX colors, typography/fallbacks and prohibited
   stretching, recoloring, rotation or cropping.
5. Record ownership/permission and the person who approves brand usage.

Acceptance: files open correctly, SVGs have a viewBox, transparent variants remain transparent,
light/dark contrast is readable and the UI requires no unofficial logo variation.

### 3. Define content and terminology

Write `design/content-guide.md`:

- Canonical labels for reception, admission, triage, call code, queue, session, room and specialty.
- Exact button verbs for start, pause, resume, close, call, present, absent, cancel and finalize.
- Confirmation text for terminal/destructive actions.
- Messages for empty, loading, offline, validation, 403, 404 and 409 states.
- Privacy rules: anonymous call code in queues and no clinical content in public areas.
- Tone: concise, calm, operational and non-diagnostic.

Acceptance: Figma and implementation use the same labels and no screen uses placeholder copy.

### 4. Build the Figma design source

Create one Figma design file with these pages:

1. `00 Cover & Links`: product/version, owner, backend commit and links.
2. `01 Foundations`: logo, semantic colors, typography, spacing, radius, elevation, icons,
   breakpoints and grids.
3. `02 Components`: buttons, inputs, select, checkbox, radio, badges, alerts, dialogs, tables,
   cards, navigation, skeletons and toasts with all interactive states.
4. `03 Reception`: every reception screen and modal/state from the inventory.
5. `04 Doctor`: every doctor screen and modal/state from the inventory.
6. `05 Prototype`: linked happy paths and critical conflict/recovery paths.

Use variables and component variants rather than detached copies. At minimum define default,
hover, focus, disabled, loading, validation-error and selected states. Use semantic names such as
`surface/default`, `text/muted`, `action/primary`, `status/urgent` and `status/danger`;
do not name tokens after literal colors.

Create frames for desktop reception at 1440 x 900, compact desktop/tablet at 1024 x 768, and a
390 x 844 narrow fallback. For each route, design loading, empty, success, validation, permission,
conflict and long-content states where applicable. Include focus indication and annotate order.

Acceptance: no required state exists only in prose; components use variables/variants; layouts
work at all target widths; contrast and focus visibility are reviewable.

### 5. Create the screen and Figma indexes

In `design/screen-matrix.md`, use:

```text
Role | Route | State | Required data | Main actions | API dependencies | Figma node | Status
```

Add one row for every screen/modal/state in the inventory. In `design/figma-index.md`, paste
node-specific URLs, viewport, last review date and approver. A file-level URL is insufficient.

Prototype reception happy path and refresh recovery; doctor happy path, absence and pause/resume;
and one 403, 404 and 409 recovery path for each role.

Acceptance: every frame maps to a route/state and every route/state has a frame or an explicit
decision explaining why a shared pattern covers it.

### 6. Produce the backend contract package

1. Check out the exact backend commit to consume.
2. Start dependencies/backend and run focused backend tests.
3. Export OpenAPI:

```powershell
Invoke-WebRequest http://localhost:8080/v3/api-docs -OutFile frontend-handoff/contract/pretriage-openapi.json
```

4. Write `contract/backend-contract.md` with repository URL, commit SHA, base URL, startup and
   verification commands, authentication flow, role probes, known blockers and compatible date.
5. Add synthetic request/success/error examples for each mutation. Remove tokens and real data.
6. Validate paths, enums, required/optional fields and status codes against implementation/docs.

Acceptance: OpenAPI parses, its SHA is traceable, examples contain no secrets, and missing doctor
contracts are linked blocker tasks rather than invented schemas.

### 7. Prepare environment and test data

- `environment/env.example` lists variable names and safe placeholders only.
- `environment/local-setup.md` covers versions, frontend/backend startup, ports, Auth0 callback
  and logout URLs, contract generation and test commands.
- `environment/test-data.md` defines synthetic hospital, specialties, rooms, receptionist,
  doctor, new/existing patients and queue cases.
- Share credentials through a secret manager or direct secure channel, never Git, Markdown,
  Figma or chat transcripts.
- Provide a repeatable seed/reset script for active/paused sessions, empty/non-empty queues and
  conflict cases without production data.

Acceptance: a new agent can start both repositories and reproduce reception and doctor scenarios
without manually editing the database.

### 8. Package and review the handoff

1. Copy the agent prompt below into `prompt/frontend-agent-prompt.md` and replace placeholders.
2. Link every backend blocker to a decision-complete issue.
3. Run a secret scan and search for real DNI, email, tokens and passwords.
4. Validate Markdown links, JSON parsing, SVGs and Figma node permissions.
5. Review with one product/domain reviewer and one implementation reviewer.
6. Freeze a version containing date, backend SHA and Figma link/version in `README.md`.

Acceptance: checklist items pass, reviewers are recorded, and the agent has no unresolved question
that can be answered by the prepared materials.

## Routes

```text
/recepcion                         session bootstrap and hospital selection
/recepcion/admisiones/nueva        DNI and patient identity
/recepcion/admisiones/[id]/triage  structured clinical form
/recepcion/admisiones/[id]/resultado priority, code, position, estimate
/medico                            session bootstrap and assignment selection
/medico/sesion                    active/paused session dashboard
/medico/atencion/[consultaId]     called patient and attention workflow
/medico/historial                 completed attention history
```

Protect both staff segments. Redirect users without receptionist access after a 403 from
`/api/recepcion/hospitales`, and users without doctor access after a 403 from
`/api/medico/asignaciones`. Never infer authorization only from frontend role metadata.

## Complete Screen Inventory

### Shared screens

1. `/` or `/inicio`: authenticated staff entry. Resolve available role from backend probes and
   redirect directly when only one role is available; show role cards when the account has both.
2. `/acceso-denegado`: explain missing role access without exposing backend details.
3. Global `not-found`, error and offline states: preserve safe navigation and never render raw
   API bodies, tokens, DNI or clinical content.

### Reception screens

1. `/recepcion`: hospital/session bootstrap, active hospital, unfinished-admission work list,
   start/close session and new-admission action.
2. `/recepcion/admisiones/nueva`: DNI lookup, existing-patient result, new-patient basic data,
   contact/address fields and specialty selection.
3. `/recepcion/admisiones/[id]/triage`: structured clinical form, local draft recovery, review,
   cancel and finalize actions.
4. `/recepcion/admisiones/[id]/resultado`: terminal state, anonymous call code, read-only
   priority, queue position and estimate.

Reception modal/states: choose hospital, confirm session close, patient not found, active
consultation conflict, abandon draft, cancel admission, AI/network retry, no open admissions,
loading skeleton and unauthorized/not-found admission.

### Doctor screens

1. `/medico`: assignments grouped by hospital/specialty, room selection and start-session action.
2. `/medico/sesion`: active/paused session header, ordered anonymous queue, call-next,
   pause/resume/close controls and empty queue.
3. `/medico/atencion/[consultaId]`: called-patient confirmation, absent action, read-only
   clinical context during attention and finish-attention action.
4. `/medico/historial`: attention history with state, hospital, specialty, room and timestamps.

Doctor modal/states: occupied room, existing session conflict, no assignments, no available rooms,
empty queue, patient called, confirm absent, confirm finish, blocked pause/close/call, paused
session, stale current-patient state and refresh recovery.

### Explicitly outside this staff frontend

- Patient self-service and digital chat.
- Public waiting-room display.
- Administration of hospitals, assignments, rooms or specialties.
- Diagnosis, prescriptions, treatment and medical notes until persisted backend contracts exist.

## Bootstrap

On layout load, request in parallel:

```http
GET /api/recepcion/hospitales
GET /api/recepcion/sesiones/activa
```

- If there is an active session, show hospital name and `Nueva admision`.
- When a session is active, also request
  `GET /api/recepcion/admisiones?sesionId={id}` and show every unfinished admission in
  oldest-first order with actions to resume or cancel.
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
  "telefono": "+54 11 5555-0101",
  "correoElectronico": "ana.perez@example.com",
  "calle": "Av. Siempre Viva",
  "alturaDomicilio": "742",
  "piso": "2",
  "codigoPostal": "C1000",
  "codigoEspecialidad": "CLINICA_MEDICA"
}
```

For a new patient, require name, surname, birth date, biological gender, phone, street, street
number and postal code. Floor and email are optional; validate email only when supplied. Send to
`POST /api/recepcion/admisiones`. Specialty options come only from the active hospital DTO. On
success, navigate using the returned admission ID.

### Step 3: Structured triage form

On route entry, fetch `GET /api/recepcion/admisiones/{id}`. Continue only when the admission
is `INICIADA` or `FORMULARIO_COMPLETO`; redirect terminal admissions to the result/status
view. Treat `404` as deleted/invalid navigation and `403` as an ownership failure.

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

### Local draft and cancellation

- Store the incomplete form only in `sessionStorage` under
  `recepcion:admision:{admisionId}:triage`; the backend deliberately returns metadata only.
- Restore that draft after the admission detail request confirms the admission is still open.
- Confirm before abandoning an open admission.
- Cancel with `POST /api/recepcion/admisiones/{id}/cancelar`. Disable cancel/finalize while
  either terminal request is pending and clear the draft only after a successful terminal response.
- After cancel or finalize, invalidate active-session, open-admissions and admission-detail queries.

## Result Screen

Show:

- Anonymous `codigoLlamado` in very large text.
- Specialty.
- Queue position and estimated time.
- Read-only priority for staff.
- `Nueva admision` action.

Load this screen from `GET /api/recepcion/admisiones/{id}` so refreshes do not depend on
navigation state. A finalized admission still in `EN_COLA` returns a freshly calculated estimate;
other states may return `estimacion: null`. A cancelled admission shows a terminal cancelled
status and no queue information.

Do not show clinical data on the hospital public screen. A later display client should receive only code, room, and call time.

## Error Handling

- `400`: map validation errors to fields.
- `401/403`: return to authentication or hospital selection.
- `404`: show resource-not-found navigation and remove stale local draft data.
- `409`: active consultation or invalid state transition; show a blocking dialog and refetch state.
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
  sesionId: number;
  pacienteId: number;
  pacienteDni: string | null;
  pacienteNombre: string | null;
  pacienteApellido: string | null;
  hospitalId: number;
  hospitalNombre: string;
  especialidadCodigo: string;
  especialidadNombre: string;
  codigoLlamado: string;
  estado: "INICIADA" | "FORMULARIO_COMPLETO" | "FINALIZADA" | "CANCELADA";
  prioridad: "RIESGO_VITAL_INMEDIATO" | "MUY_URGENTE" | "URGENTE" | "NORMAL" | "NO_URGENTE" | null;
  fechaHoraInicio: string;
  fechaHoraFinalizacion: string | null;
  fechaHoraCancelacion: string | null;
  estimacion: TiempoEstimadoAtencion | null;
};
```

## Doctor Frontend

### Backend prerequisites

Do not ship refresh-safe doctor pages until these backend contracts exist:

1. `GET /api/medico/sesiones/activa` returning the doctor's `ACTIVA` or `PAUSADA`
   session, or `204` when none exists.
2. `GET /api/medico/sesiones/{sesionId}/consulta-actual` returning the consultation assigned
   to that session in `LLAMADO` or `EN_ATENCION`, or `204`.
3. A doctor-authorized clinical detail endpoint returning the reception form or digital triage
   summary required for care. The current `ConsultaLlamadaDTO` contains identifiers and room only.
4. Typed doctor conflicts as `409` and missing resources as `404`, matching the reception API.

Until those contracts are implemented, session/current-patient state survives only in memory and
a refresh cannot be recovered reliably. Do not work around this with `localStorage`, because
backend state remains authoritative.

### Bootstrap and session selection

Load in parallel:

```http
GET /api/medico/asignaciones
GET /api/medico/sesiones/activa
```

- If a session exists, redirect to `/medico/sesion`.
- Otherwise group assignments by hospital and specialty.
- After selecting an assignment, load rooms with
  `GET /api/hospitales/{hospitalId}/salas?codigoEspecialidad={codigo}`.
- Start with `POST /api/medico/sesiones`:

```json
{
  "hospitalId": 10,
  "codigoEspecialidad": "CLINICA_MEDICA",
  "salaId": 4
}
```

Never allow arbitrary specialty/room combinations. Options must come from assignments and the
room endpoint.

### Session dashboard

The header always shows hospital, specialty, room and session state. When `ACTIVA`:

- Poll `GET /api/medico/sesiones/{id}/pacientes-disponibles` every 10 seconds and refetch after
  every queue mutation. Preserve backend order; do not sort client-side.
- Show anonymous `codigoLlamado`, queue count and state. Do not show patient identity in the
  waiting list.
- `Llamar siguiente` sends `POST /api/medico/sesiones/{id}/llamar-proximo`.
- Disable call, pause and close while a consultation is `LLAMADO` or `EN_ATENCION`.
- Pause with `POST /api/medico/sesiones/{id}/pausar`; paused sessions reserve doctor and room
  but do not offer call/attention actions.
- Resume with `POST /api/medico/sesiones/{id}/reanudar`.
- Close with `POST /api/medico/sesiones/{id}/cerrar`, after confirmation, then clear session
  query state and return to assignment selection.

Do not optimistically change queue/session states. Disable the action, wait for the server
response, replace cached data with the returned DTO and invalidate related queries.

### Called patient and attention

After `llamar-proximo`, navigate to `/medico/atencion/{consultaId}` and render:

1. `LLAMADO`: show call code and room.
   - `Presente` calls `POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/presente`.
   - `Ausente` calls `POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/ausente`.
2. `EN_ATENCION`: load authorized clinical detail read-only and offer finalization.
3. Terminal: return to the session queue and invalidate current consultation, available patients
   and history.

Finalize with
`POST /api/medico/sesiones/{sesionId}/consultas/{consultaId}/finalizar`. The current backend
does not accept notes, diagnosis, treatment or prescriptions. Do not create local clinical
records; add those fields only with a future persisted backend contract.

### History

`GET /api/medico/atenciones` feeds `/medico/historial`. Show the order returned by the backend,
with state, hospital, specialty, room and timestamps. Clinical detail links remain unavailable
until an authorized detail contract exists.

### Doctor types

```ts
type DoctorAssignment = {
  hospitalId: number;
  nombreHospital: string;
  codigoEspecialidad: string;
  nombreEspecialidad: string;
};

type MedicalSession = {
  id: number;
  hospitalId: number;
  codigoEspecialidad: string;
  salaId: number;
  estado: "ACTIVA" | "PAUSADA" | "FINALIZADA";
};

type CalledConsultation = {
  consultaId: number;
  codigoLlamado: string;
  pacienteId: number;
  salaId: number;
  nombreSala: string;
  estadoConsulta: "EN_COLA" | "LLAMADO" | "EN_ATENCION" | "EN_ESPERA" | "FINALIZADA";
};

type MedicalAttention = {
  id: number;
  consultaId: number;
  sesionId: number;
  pacienteId: number;
  hospitalId: number;
  codigoEspecialidad: string;
  salaId: number;
  estado: "EN_CURSO" | "FINALIZADA";
  fechaHoraInicio: string;
  fechaHoraFin: string | null;
};
```

## Shared Client Architecture

- Use one authenticated server-side API client that forwards the access token and normalizes
  `400/401/403/404/409` without logging bodies containing DNI or clinical data.
- Keep server state in TanStack Query. Use component state for dialogs and forms; use
  `sessionStorage` only for the unfinished reception triage draft.
- Suggested query keys: `receptionHospitals`, `receptionSession`, `openAdmissions(sessionId)`,
  `admission(id)`, `doctorAssignments`, `doctorSession`, `rooms(hospitalId, specialty)`,
  `availablePatients(sessionId)`, `currentConsultation(sessionId)`, and `doctorHistory`.
- Global handling: `401` re-authenticates, `403` routes to role selection, `404` removes stale
  route state, and `409` refetches authoritative state before explaining the conflict.
- Never persist JWTs, DNI, clinical forms, patient identity or medical summaries in
  `localStorage`, analytics, error breadcrumbs or console logs.

## Backend-Frontend Contract Synchronization

Treat backend implementation plus its generated OpenAPI document as the API source of truth.
Treat this file as product/workflow guidance, not as a substitute for the live schema.

### Initial frontend repository setup

1. Store the backend repository URL and tested commit SHA in `docs/backend-contract.md`.
2. Export `GET /v3/api-docs` from a tested backend into `contracts/pretriage-openapi.json`.
3. Generate TypeScript transport types/client from that snapshot with a committed script such as
   `npm run contract:sync -- --backend=http://localhost:8080`.
4. Keep domain-friendly adapters and Zod validation outside generated files. Never hand-edit
   generated output.
5. Add `npm run contract:check` to CI. It must regenerate into a temporary directory and fail on
   an unexplained diff.

### When backend changes first

1. Backend change updates code, tests, OpenAPI annotations/schema and relevant `docs/` in the same
   commit.
2. Classify the contract change as additive, behavior-only, deprecated or breaking.
3. Prefer additive compatibility: add fields/endpoints first, keep old fields during migration,
   and remove only after the frontend has shipped the replacement.
4. Open/link a frontend task containing backend commit SHA, affected endpoints, example payloads,
   state transitions and required UI behavior.
5. Frontend updates the OpenAPI snapshot, generated client, adapters, MSW handlers, fixtures,
   screens and Playwright coverage in one PR.
6. Record the new tested backend SHA after cross-repository E2E passes.

### When frontend discovers a backend need

1. Do not mock an invented permanent API. Write a contract proposal with method/path, request,
   response, authorization, status codes, state transitions and retry/idempotency behavior.
2. Implement and verify the backend contract first, including documentation and focused tests.
3. Export the updated OpenAPI snapshot and only then implement the production frontend adapter.
4. Temporary MSW-only prototypes must be labeled `blocked-by-backend` and cannot be merged as a
   completed production flow.

### Compatibility and release rules

- Frontend tolerates unknown additive response fields and nullable fields documented as optional.
- Backend does not silently rename enums, routes or JSON properties.
- Mutations are disabled while pending and are never automatically retried unless explicitly
  documented as idempotent.
- Every cross-repository PR links its counterpart and states the minimum compatible backend/front
  version or commit.
- Run contract checks, backend focused tests, frontend unit/MSW tests and the relevant role E2E
  before declaring a synchronized release.
- If repositories deploy independently, release additive backend changes first, then frontend,
  then remove deprecated backend behavior in a later release.

### Documentation ownership

- Backend owns endpoint paths, DTOs, authorization, status codes, state transitions and domain
  rules under its `docs/`.
- Frontend owns routes, components, design tokens, accessibility, query invalidation and visual
  acceptance criteria.
- This staff plan is copied to the frontend at project bootstrap. Afterwards, the frontend copy is
  authoritative for UI implementation, while backend links remain pinned by commit SHA.

## Brand, Logo And Figma Handoff

### Logo intake

- Request the original SVG when available, plus PNG fallback, light/dark variants, minimum size,
  clear-space guidance and confirmation that the asset is approved for product use.
- Store originals under `public/brand/`; do not redraw, trace, recolor or crop the logo without
  explicit approval.
- Build a small `BrandMark` component with accessible product text, controlled size variants and
  theme-aware source selection. Decorative duplicates use empty alt text.
- If no final logo is available, use a text wordmark placeholder isolated behind `BrandMark` so
  replacement does not affect page layouts.

### Figma intake

- Ask for node-specific Figma URLs for every supplied screen or component, not only a file-level URL.
- Record viewport, component states, responsive behavior, design variables and prototype
  interactions. Map every mockup to one route/state in the screen inventory.
- Treat Figma as visual/interaction intent and the backend/OpenAPI contract as behavioral truth.
  Raise mismatches explicitly; never alter domain behavior merely to imitate a static mockup.
- Reuse Figma variables as semantic tokens for color, typography, spacing, radius, elevation and
  breakpoints. Avoid one-off hardcoded values when a token exists.
- Implement desktop reception/doctor workflows keyboard-first and define tablet/mobile adaptations
  even when only desktop mockups are supplied.

### Visual acceptance

- Maintain a `docs/design-coverage.md` table with route, state, Figma node URL, implementation
  status and visual-regression test.
- Add Storybook or an equivalent isolated component harness for shared controls and terminal/error
  states.
- Capture Playwright screenshots at agreed desktop and tablet viewports. Review layout, focus,
  contrast, truncation, loading, empty, error and long-content cases.
- Logo or Figma delivery can refine visuals, but must not block contract adapters, state machines,
  accessibility foundations or test infrastructure.

## Portable implementation order

1. Implement shared authentication, role routing, API errors, privacy-safe logging and DTO schemas.
2. Complete backend doctor prerequisites: active session, current consultation, clinical detail and
   typed `404/409` responses.
3. Implement reception bootstrap, patient registration, open admissions, triage draft,
   finalization, cancellation and result recovery.
4. Implement doctor assignments, room selection, session bootstrap and pause/resume/close.
5. Implement doctor queue polling, call/present/absent/finalize state machine and history.
6. Add MSW contract tests for both roles, then Playwright role-specific E2E and accessibility.

## Copyable Agent Prompt

Use the following prompt after copying this plan into the frontend repository. Replace values in
angle brackets and attach the logo/Figma links when available.

```text
You are implementing the Pretriage staff frontend in <FRONTEND_REPOSITORY>.

Read completely before changing code:
1. The repository AGENTS.md and local contribution instructions.
2. docs/nextjs-staff-frontend-plan.md (copied from the backend staff plan).
3. docs/backend-contract.md and contracts/pretriage-openapi.json.
4. Existing design tokens, components, tests and authentication setup.

Backend reference:
- Repository: <BACKEND_REPOSITORY_URL>
- Tested commit: <BACKEND_COMMIT_SHA>
- Local base URL: <BACKEND_BASE_URL>
- OpenAPI URL: <BACKEND_BASE_URL>/v3/api-docs

Goal:
Build the production Next.js App Router frontend for receptionist and doctor workflows described
in the staff plan. Implement shared authenticated infrastructure, role routing, responsive and
accessible screens, contract adapters, TanStack Query state, React Hook Form + Zod forms, MSW
tests and Playwright E2E.

Non-negotiable behavior:
- Backend state and generated OpenAPI are authoritative. Never invent a production endpoint,
  enum, field, status transition or authorization rule.
- Reception priority is backend-generated and read-only.
- Queue ordering is backend-owned and must not be re-sorted.
- Never place tokens, DNI, patient identity or clinical data in localStorage, analytics, logs,
  URLs beyond required opaque IDs, or error breadcrumbs.
- The only clinical draft persisted in the browser is reception triage in sessionStorage,
  namespaced by admission ID and cleared after a successful terminal action.
- Disable mutations while pending and refetch authoritative state after mutations/conflicts.
- Do not implement local-only diagnosis, prescription, treatment or medical-note records.

Backend blockers:
Before marking doctor refresh/recovery complete, verify these contracts exist in the pinned
backend/OpenAPI: GET active medical session, GET current called/in-attention consultation, and an
authorized clinical detail endpoint. If missing, create a decision-complete contract proposal and
mark dependent UI as blocked-by-backend. Do not hide the gap with localStorage or a permanent mock.

Design inputs:
- Logo assets: <LOGO_PATHS_OR_PENDING>
- Figma node URLs: <FIGMA_NODE_URLS_OR_PENDING>
- Use supplied Figma nodes as visual intent and map them to the screen inventory.
- Use semantic tokens and reusable components. Keep a text BrandMark placeholder if assets are
  pending. Do not redraw or alter the supplied logo.

Execution order:
1. Audit the existing repo and report mismatches with this plan/OpenAPI.
2. Set up contract snapshot/generation/checking and shared authenticated API errors.
3. Implement shared shell and role routing.
4. Implement reception end-to-end with tests.
5. Implement backend-ready doctor flows and clearly isolate blockers.
6. Implement remaining doctor flows after contracts land.
7. Apply logo/Figma design, responsive behavior and visual regression coverage.
8. Run typecheck, lint, unit/component, MSW integration and Playwright tests.

For every phase:
- Keep a short plan with acceptance criteria.
- Update docs/design-coverage.md and docs/backend-contract.md.
- Preserve unrelated user changes.
- Update generated clients only through the sync command.
- If frontend needs a backend change, stop that dependent path, write the full contract proposal,
  link the counterpart task/PR and continue independent work.

Definition of done:
- Every in-scope screen and modal/state in the inventory is implemented or explicitly blocked by
  a named backend contract.
- Refresh recovery works for open reception admissions and active/paused doctor sessions.
- Role authorization, loading, empty, offline, 400/401/403/404/409 and conflict states are covered.
- Keyboard navigation, focus management, labels, live announcements and contrast pass review.
- Contract check, typecheck, lint, tests and relevant E2E pass.
- Final handoff lists implemented routes, backend SHA, contract blockers, test evidence, Figma
  coverage and any intentional deviations.
```

## Frontend Test Plan

- Unit: Zod schemas and DNI normalization.
- Unit: phone/email/address validation, query-key factories and medical state reducers.
- Component: existing vs new patient states.
- Component: priority is never editable.
- Component: doctor session controls for `ACTIVA`, `PAUSADA` and blocked current-patient states.
- Integration with MSW: bootstrap, start session, create and finalize admission.
- Integration with MSW: doctor assignment/room selection, session recovery, call, present,
  absent, finalize and history invalidation.
- E2E Playwright: receptionist login -> hospital -> DNI -> form -> result.
- E2E Playwright: doctor login -> assignment -> room -> session -> call -> present -> finalize.
- E2E refresh: recover an open reception admission and an active/paused doctor session.
- E2E conflict: second active session and patient with active consultation.
- E2E conflict: occupied room, second doctor session, call while a patient is active, and
  pause/close while `LLAMADO` or `EN_ATENCION`.
- Accessibility: keyboard-only form and error announcements.
- Accessibility: keyboard-operable doctor queue/actions, focus after mutations and live call-state
  announcements without exposing patient identity.
