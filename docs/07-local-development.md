# Local Development

## Requirements

- Java 21
- Docker Desktop
- PostgreSQL through `compose.yaml`
- Internet access and a Gemini API key (`GEMINI_API_KEY`)
- `.env` with Auth0 and test credentials

## Important Properties

```properties
pretriage.ia.gemini.model=${GEMINI_CHAT_MODEL:gemini-3.5-flash-lite}
pretriage.ia.gemini.api-key=${GEMINI_API_KEY:}
pretriage.ia.gemini.request-timeout=${GEMINI_REQUEST_TIMEOUT:45s}
pretriage.estimacion.minutos-promedio-atencion=10
pretriage.cambio-contrasenia.expiracion-minutos=${PRETRIAGE_CAMBIO_CONTRASENIA_EXPIRACION:15}
pretriage.cambio-contrasenia.max-solicitudes-por-hora=${PRETRIAGE_CAMBIO_MAX_POR_HORA:3}
pretriage.cambio-contrasenia.ventana-horas=${PRETRIAGE_CAMBIO_VENTANA_HORAS:1}
pretriage.cambio-contrasenia.email.mode=${PRETRIAGE_CAMBIO_EMAIL_MODE:${PRETRIAGE_INVITATIONS_EMAIL_MODE:local}}
pretriage.cambio-contrasenia.email.from=${PRETRIAGE_CAMBIO_EMAIL_FROM:${PRETRIAGE_INVITATIONS_EMAIL_FROM:no-reply@pretriage.local}}
spring.config.import=optional:file:.env[.properties]
spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:create-drop}
pretriage.voz.gemini.api-key=${GEMINI_API_KEY:}
pretriage.voz.gemini.modelo=${GEMINI_LIVE_MODEL:models/gemini-3.8-live}
pretriage.voz.gemini.voz=${GEMINI_LIVE_VOICE:Kore}
pretriage.voz.gemini.idioma=${GEMINI_LIVE_LANGUAGE:es-US}
pretriage.voz.origenes-permitidos=${PRETRIAGE_VOZ_ORIGENES:*}
```

The voice chat (`/api/chat/{id}/voz`) is optional: without `GEMINI_API_KEY` its
handshake returns `503`; text and reception classification use their local fallback. Set the key in `.env`
to enable it, and restrict `PRETRIAGE_VOZ_ORIGENES` (comma-separated origin
patterns) outside local development.

Set `JPA_DDL_AUTO=update` in the ignored local `.env` before starting a development
instance whose data must persist. The current fallback is `create-drop`: it
drops the schema at shutdown and recreates it at startup.

Spring DevTools watches `target/classes`. Maven compilation (including `test`
and `package`) can restart a running backend and erase its data when that
instance uses `create-drop`. Before compiling, verify the running instance uses
a persistent schema mode, or stop it after backing up its data. Editing `.env`
alone does not protect the shutdown of a process that already loaded
`create-drop`. Tests that need a datasource must use an isolated database.

## Invitation email through Brevo

The SMTP adapter is already included in the backend. Configure the ignored `.env`
in the backend working directory, then restart the process to load it:

```properties
PRETRIAGE_INVITATIONS_EMAIL_MODE=smtp
PRETRIAGE_INVITATIONS_EMAIL_FROM=no-reply@pretriage.com.ar
PRETRIAGE_FRONTEND_BASE_URL=http://localhost:3000
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USERNAME=<SMTP login from Brevo>
SMTP_PASSWORD=<SMTP key from Brevo, not an API key>
SMTP_AUTH=true
SMTP_STARTTLS=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=10000
spring.mail.properties.mail.smtp.timeout=10000
spring.mail.properties.mail.smtp.writetimeout=10000
PRETRIAGE_CAMBIO_EMAIL_MODE=local
```

Use the frontend's public HTTPS URL when recipients open links outside the local
development machine. Sender/domain verification is managed in Brevo. Authentication
alone does not verify sender authorization or inbox delivery. SMTP mode does not
return an invitation secret to the administrative frontend; failed sends remain
pending and can be resent from the invitation list.

Password recovery otherwise inherits the invitation email mode. The explicit
`PRETRIAGE_CAMBIO_EMAIL_MODE=local` keeps its existing behavior until separately
enabled. Never commit the SMTP key or enable mail protocol debugging with real
credentials. A connection check may authenticate with STARTTLS and quit without
issuing `MAIL`, `RCPT` or `DATA`; sending a real test invitation is a separate step.

Provider reference: [Brevo transactional SMTP](https://help.brevo.com/hc/en-us/articles/7924908994450-Send-transactional-emails-using-Brevo-SMTP).

## Compile

```powershell
.\mvnw.cmd test -DskipTests
```

## Focused Tests

```powershell
.\mvnw.cmd "-Dtest=AtencionHospitalServiceTest,EstimacionAtencionServiceTest" test
.\mvnw.cmd "-Dtest=HospitalConfigurationServiceTest,HospitalConfigurationControllerTest" test
```

## Full Tests

```powershell
.\mvnw.cmd test
```

Full tests need Docker Desktop access. In restricted environments, this can fail with Docker pipe permission errors.

## Real Chat E2E

Create a disposable database and start the backend against it before running
the script; see [isolated setup](08-e2e-chat-debugging.md#isolated-setup).
The backend and script must use the same database. The seed removes the test
patient's previous chats and cancels their active consultations.

```powershell
python scripts\e2e_chat.py --backend-url http://localhost:18080 --db-name pretriage_chat_e2e --messages-file scripts\chat_case_example.txt
```

The script reads credentials from `.env`:

- `AUTH0_TEST_USERNAME`
- `AUTH0_TEST_PASSWORD`

It seeds patient/hospital/specialty/sector/room data through Docker, performs
real login, calls backend endpoints and Gemini, and validates the persisted
classification, its origin, priority and both queue states. It rejects early
closure and final local fallback by default.

## Voice Chat Regression Tests

Run the voice session, shared close validator, service fallback and authentication regressions:

```powershell
.\mvnw.cmd "-Dtest=SesionVozChatTest,SesionVozIntegracionTest,ValidadorCierreEntrevistaVozTest,ChatVozHandshakeInterceptorTest,ChatServiceTest,TriageIaClientTest" test
```

These tests simulate Gemini Live and Gemini REST. They do not verify microphone capture, audio playback
or provider availability. See [the voice integration flow](14-chat-voz-gemini-live.md).

Full application context tests use `SpringBootTest.WebEnvironment.RANDOM_PORT`:
the voice configuration requires a real servlet WebSocket `ServerContainer`, which
the default mock servlet context does not provide. `BackendApplicationTests`
also checks the configured binary/text buffer limits and idle timeout.

## Chat History Schema Migration

`Chat.paciente` now maps many chats to one patient, preserving previous interview
history after a new consultation starts. On an existing PostgreSQL database,
remove the unique constraint left by the old `OneToOne` mapping:

```powershell
Get-Content -Raw scripts/migrations/20261011_chat_patient_history.sql | docker exec -i postgre psql -X -U myuser -d pretriage_db -v ON_ERROR_STOP=1 -f -
```

Apply this migration together with the new backend mapping. Hibernate schema
`update` does not automatically remove existing unique constraints. The script
only removes single-column uniqueness constraints on `chat.paciente_id`, retains
the foreign key and all chats/messages, and creates a non-unique lookup index.
It is transactional, uses a five-second lock timeout, and can be repeated.
Fresh schemas generated from the new mapping do not have that unique constraint.

The real PostgreSQL regression `ChatHistoryPersistenceTest` persists a closed
chat and a new chat for the same patient and verifies that both histories remain.
Use an isolated database when running it; see the datasource guidance above.

Verified on October 11, 2026: `ChatHistoryPersistenceTest`, `ChatServiceTest`
and `EsperaPacienteServiceTest` passed (46 tests) with an isolated PostgreSQL
database. The migration also removed a recreated legacy constraint in that
database and succeeded when repeated. It was then applied to the local
`pretriage_db` with authorization; its existing chat and all nine messages
remained intact and the patient foreign key was preserved.

## Local Hospital Development Data

After the backend creates its schema, populate repeatable synthetic hospital data:

```powershell
python -B scripts/seed_dev_hospital.py --with-active-doctors
```

The script defaults to Docker container `postgre`, user `myuser`, database
`pretriage_db`, and hospital name `Hospital de Prueba - PreTriage`. Override them
with `--db-container`, `--db-user`, `--db-name`, and `--hospital-name`.
It reuses a hospital with that exact name and creates missing specialty links,
one development sector and room per specialty, and their queue managers.
With `--with-active-doctors` it also creates five synthetic local doctor
identities, specialty assignments, and active sessions. These identities are
not Auth0 accounts and cannot sign in. This option is for local development only;
the sessions represent simulated capacity and remain active until closed.
Omit the option to create only hospital infrastructure.

The seed is transactional, does not delete patient/chats/consultations, and
can be repeated without duplicating its data. It does not reactivate paused
doctors or inactive rooms/sectors, or displace another doctor's occupied room.
Existing paused sessions reserve their room and prevent a second seeded session.
Use `--dry-run` to inspect SQL without accessing the database.

`Hospital` has no `activo` field. Operational availability requires specialty
links, an active sector and room, and active doctor sessions. The nearby patient
endpoint also requires that Google Places returns the hospital's actual
`place_id` near the patient's coordinates. A synthetic hospital without that ID
will not appear in mobile's Google-based nearby list. Set a real ID:

```powershell
python -B scripts/seed_dev_hospital.py --with-active-doctors --place-id "REAL_GOOGLE_PLACE_ID"
```

For the local Hospital de Clinicas Jose de San Martin (UBA) demo, Google Places
verified `ChIJwwfedJTKvJURtfBmDu6LFm0` at Av. Cordoba 2351, CABA on October 11,
2026. Restore this local setup after a schema reset with:

```powershell
python -B scripts/seed_dev_hospital.py --with-active-doctors --place-id ChIJwwfedJTKvJURtfBmDu6LFm0
```

This attaches the real place to `Hospital de Prueba - PreTriage`; its seeded
doctor capacity remains synthetic. The location is latitude `-34.5984007`,
longitude `-58.3998358`. For mobile testing, set the emulator location near CABA;
Google's nearby search will not return this hospital from another city.

The script refuses to replace a different existing Place ID or attach an ID
already assigned to another hospital. It does not call Google, infer an ID from
the hospital name, or create memberships/admin accounts.

To retain these data between backend runs, use `JPA_DDL_AUTO=update` in the backend
root `.env` and start the app with that directory as its working directory.
An environment variable or IntelliJ VM option overriding the setting can still
select `create`/`create-drop`; check those if the schema keeps being recreated.
The current `.env` is read at startup. If a running instance loaded `create-drop`,
changing the file alone cannot prevent that instance from dropping its schema
on shutdown; back up needed data before stopping it. Re-run the seed after a
deliberate schema reset.

Verified on October 11, 2026: the local database initially had one hospital but
no sectors, rooms or doctor sessions. Running the seed created one active sector,
room and doctor session for each of the five specialties; running it again
inserted no duplicate seed rows.

## Common Local Issues

### Docker Permission Denied

If tests fail with access to `npipe:////./pipe/dockerDesktopLinuxEngine`, run from a shell/session that has Docker Desktop access.

### Gemini unavailable

Set `GEMINI_API_KEY` in the ignored `.env` and optionally `GEMINI_CHAT_MODEL`.
The REST default is `gemini-3.5-flash-lite`; the audio default is `models/gemini-3.8-live`.
Restart the backend after configuration changes. Keys are never needed in mobile.
Missing keys, HTTP errors, quota exhaustion or invalid output use `FALLBACK_LOCAL`
for text/reception/final voice classification. A Live connection itself requires
an available Live model and key. Availability and quotas depend on the account;
the E2E precheck reads model metadata and the full flow then verifies generation.

### Backend Not Running

The E2E script expects:

```text
http://localhost:8080
```

Override with:

```powershell
python scripts\e2e_chat.py --db-name pretriage_chat_e2e --backend-url http://localhost:8081
```

## Reception HTTP E2E

With the backend running, provide a valid Auth0 access token for a receptionist:

```powershell
$env:E2E_RECEPCION_TOKEN="<token>"
python scripts/e2e_recepcion.py --mode finalize
python scripts/e2e_recepcion.py --mode cancel
```

Use `--hospital-id` when the receptionist is assigned to several hospitals. The script reuses an
active reception session or starts one, creates a unique patient/admission, verifies open-admission
recovery, performs the requested terminal action, and reads the final detail back.
