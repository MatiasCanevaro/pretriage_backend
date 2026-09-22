# Local Development

## Requirements

- Java 21
- Docker Desktop
- PostgreSQL through `compose.yaml`
- Ollama running locally
- Model `llama3.2:3b`
- `.env` with Auth0 and test credentials

## Important Properties

```properties
spring.ai.ollama.chat.model=llama3.2:3b
spring.ai.ollama.chat.temperature=0
spring.ai.ollama.chat.seed=42
pretriage.estimacion.minutos-promedio-atencion=10
pretriage.cambio-contrasenia.expiracion-minutos=${PRETRIAGE_CAMBIO_CONTRASENIA_EXPIRACION:15}
pretriage.cambio-contrasenia.max-solicitudes-por-hora=${PRETRIAGE_CAMBIO_MAX_POR_HORA:3}
pretriage.cambio-contrasenia.ventana-horas=${PRETRIAGE_CAMBIO_VENTANA_HORAS:1}
pretriage.cambio-contrasenia.email.mode=${PRETRIAGE_CAMBIO_EMAIL_MODE:${PRETRIAGE_INVITATIONS_EMAIL_MODE:local}}
pretriage.cambio-contrasenia.email.from=${PRETRIAGE_CAMBIO_EMAIL_FROM:${PRETRIAGE_INVITATIONS_EMAIL_FROM:no-reply@pretriage.local}}
spring.config.import=optional:file:.env[.properties]
spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:create-drop}
```

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

```powershell
python scripts\e2e_chat.py --messages-file scripts\chat_case_example.txt
```

The script reads credentials from `.env`:

- `AUTH0_TEST_USERNAME`
- `AUTH0_TEST_PASSWORD`

It seeds minimal DB data through Docker, performs real login, uses real backend endpoints, calls Ollama, and validates queue state.

## Common Local Issues

### Docker Permission Denied

If tests fail with access to `npipe:////./pipe/dockerDesktopLinuxEngine`, run from a shell/session that has Docker Desktop access.

### Ollama Model Missing

Check available models:

```powershell
ollama list
```

Pull model:

```powershell
ollama pull llama3.2:3b
```

### Backend Not Running

The E2E script expects:

```text
http://localhost:8080
```

Override with:

```powershell
python scripts\e2e_chat.py --backend-url http://localhost:8081
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
