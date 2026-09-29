# E2E Chat Debugging

`scripts/e2e_chat.py` exercises real login, backend endpoints, Ollama and PostgreSQL.
Use synthetic messages and a disposable database: the seed cancels the test
patient's existing consultations and deletes their previous chats and messages.

## Isolated Setup

Create a test database in the local PostgreSQL container:

```powershell
docker exec postgre psql -U myuser -d postgres -c "CREATE DATABASE pretriage_chat_e2e"
```

Start a separate backend against that database. Pass these program arguments to
the application, retaining the local `.env` for credentials:

```text
--server.port=18080
--spring.datasource.url=jdbc:postgresql://localhost:5432/pretriage_chat_e2e
--spring.jpa.hibernate.ddl-auto=update
--spring.docker.compose.enabled=false
```

The backend must point to the same database given to the script. Do not run
`create-drop` against data that must survive startup, shutdown or DevTools
restarts. The script requires `--db-name` (or `DB_NAME`); the usual `pretriage_db`
also requires `--allow-destructive-seed`.

## Run

```powershell
python scripts/e2e_chat.py --backend-url http://localhost:18080 --db-name pretriage_chat_e2e --messages-file scripts/chat_case_example.txt --expected-intensity 5 --expected-onset ayer --debug-log target/chat-e2e.json
```

Credentials are `AUTH0_TEST_USERNAME` and `AUTH0_TEST_PASSWORD` in `.env` or the
environment. The seed creates the patient, hospital, specialty, active sector
and active room required for hospital selection.

Message files are UTF-8 with or without BOM, one message per line. Empty lines
and lines beginning with `#` are ignored. Repeated `--message` arguments override
the file. The example contains four messages:

```text
Tengo fiebre y dolor de garganta desde ayer.
La fiebre llego a 38.5 y el dolor es 5 de 10.
No tengo dificultad para respirar ni dolor de pecho.
No tengo enfermedades previas, no tomo medicacion y no tengo alergias.
```

## Success Criteria

- Ollama is reachable and the requested model is installed.
- Login, hospital selection and chat endpoints succeed.
- The interview consumes the scripted messages and finishes; early closure is
  rejected by default.
- The stored result has meaningful motive/symptoms and the expected fields.
- Final `origenClasificacion` is `OLLAMA` by default. The per-turn response exposes
  `origenRespuesta` (`OLLAMA` or `FALLBACK_LOCAL`).
- Chat and consultation belong to the authenticated patient; consultation and
  queue entry both have `EN_COLA`, and the assigned sector is consistent.
- Result priority, consultation severity and queue priority agree.
- Expected symptoms match the case (default: `fiebre`). If supplied,
  `--expected-priority`, `--expected-intensity` and `--expected-onset` must match
  too. Without a specified expected priority, the test verifies range and
  consistency with severity and queue priority; it does not prescribe a clinical
  classification for the model. These are test expectations, not a clinical benchmark.

The script reads structured SQL JSON. The patient is already queued before the
optional chat, so queue membership alone never proves AI classification.

## Custom Cases and Fallback

```text
--expected-priority 5
--expected-intensity 8
--expected-onset "hace una hora"
--expected-symptom "dolor de pecho"
--min-turns 1
--allow-early-finalization
--allow-fallback
--skip-ollama-check
```

Use `--allow-early-finalization` for cases intentionally expected to close
immediately, such as an alarm scenario. `--min-turns` sets a custom minimum.
`--expected-symptom` can be repeated. `--allow-fallback` explicitly accepts local
classification; such a run does not demonstrate Ollama classification.
`--skip-ollama-check` only skips the availability precheck.

## Alarm Case with Ollama

`scripts/chat_case_alarm.txt` contains a single synthetic message reporting
chest pain and difficulty breathing. Run it against the isolated backend:

```powershell
python scripts/e2e_chat.py --backend-url http://localhost:18080 --db-name pretriage_chat_e2e --messages-file scripts/chat_case_alarm.txt --expected-symptom "dolor" --expected-priority 5 --expect-immediate-attention --debug-log target/chat-alarm-e2e.json
```

`--expect-immediate-attention` requires immediate attention, nonempty alarm signs,
urgent priority and `OLLAMA` origin in both the final HTTP response and stored
classification. It also checks that the final patient-facing message includes
the stored safety recommendation. It does not accept a local fallback, even with
`--allow-fallback`. Repeatable `--expected-alarm TERM` can additionally check
specific terms in the stored alarm signs. This one-message fixture must close
in the first turn without any extra early-finalization allowance.

## Diagnostics and Regression Tests

`--debug-log` writes JSON on success or failure with the stage, masked user,
scripted turns, response origin and persisted validation details when available.
Do not commit logs containing real patient information. Store diagnostic files
under ignored `target/`; Maven clean removes them.

```powershell
python -m unittest discover -s scripts -p test_e2e_chat.py
./mvnw.cmd "-Dtest=ChatServiceTest,TriageIaClientTest,TriageResultDTOTest,AtencionHospitalServiceTest" test
```

After stopping the isolated backend, remove the disposable database:

```powershell
docker exec postgre psql -U myuser -d postgres -c "DROP DATABASE pretriage_chat_e2e"
```
