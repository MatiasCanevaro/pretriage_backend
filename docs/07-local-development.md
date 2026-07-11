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
spring.ai.ollama.chat.options.model=llama3.2:3b
pretriage.estimacion.minutos-promedio-atencion=10
spring.config.import=optional:file:.env[.properties]
```

## Compile

```powershell
.\mvnw.cmd test -DskipTests
```

## Focused Tests

```powershell
.\mvnw.cmd "-Dtest=AtencionHospitalServiceTest,EstimacionAtencionServiceTest" test
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
