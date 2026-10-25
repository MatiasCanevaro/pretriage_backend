# E2E Chat Debugging

`scripts/e2e_chat.py` exercises real login, backend endpoints, Gemini REST and PostgreSQL.
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

- The Gemini API key can access a model supporting `generateContent`. This metadata precheck does not prove generation quota availability.
- Login, hospital selection and chat endpoints succeed.
- The backend follows the short evidence-led flow: no minimum answer count or
  fixed topic checklist, uses one open invitation for other symptoms or useful
  context, keeps relevant context volunteered earlier, and closes when the patient
  has nothing more to add. At most eight patient answers are accepted.
- The stored result has meaningful motive/symptoms and the expected fields.
- Final `origenClasificacion` is `GEMINI` by default. The per-turn response exposes
  `origenRespuesta` (`GEMINI`).
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

## Custom Cases

```text
--expected-priority 5
--expected-intensity 8
--expected-onset "hace una hora"
--expected-symptom "dolor de pecho"
--min-turns 1
--allow-early-finalization
--skip-gemini-check
```

Use `--allow-early-finalization` for cases intentionally expected to close
immediately, such as an alarm scenario. `--min-turns` sets a test-specific minimum;
it does not change the backend interview policy.
`--expected-symptom` can be repeated. Chat has no local fallback; the E2E requires
Gemini provenance and fails if generation fails or the response is rejected.
`--skip-gemini-check` only skips the availability precheck.

## Alarm Case with Gemini REST

`scripts/chat_case_alarm.txt` contains a single synthetic message reporting
chest pain and difficulty breathing. Run it against the isolated backend:

```powershell
python scripts/e2e_chat.py --backend-url http://localhost:18080 --db-name pretriage_chat_e2e --messages-file scripts/chat_case_alarm.txt --expected-symptom "dolor" --expected-priority 5 --expect-immediate-attention --debug-log target/chat-alarm-e2e.json
```

`--expect-immediate-attention` requires immediate attention, nonempty alarm signs,
urgent priority and `GEMINI` origin in both the final HTTP response and stored
classification. It also checks that the final patient-facing message includes
the stored safety recommendation. It does not accept a local fallback. Repeatable `--expected-alarm TERM` can additionally check
specific terms in the stored alarm signs. This one-message fixture must close
in the first turn without any extra early-finalization allowance.

## Diagnostics and Regression Tests

Mobile distinguishes backend HTTP failures from transport failures when sending a
text message. A 5xx reports that the assistant could not respond, 401/403 indicates
access/session trouble, 404 reports a missing chat, and 429 requests a pause. It
keeps the draft and manual retry; it does not automatically repeat a POST. Closed
interviews still recover authoritative state through GET and offer classification
retry without another patient message. An unfinished failed turn is removed by
`ChatService`, so its absence from message history cannot identify the failure;
correlate the original backend log at the time of the attempt. Gemini's internal
REST retries do not replay a mobile patient's POST.

The generic `ProveedorIaException` HTTP warning is not the underlying diagnosis.
For incomplete Gemini REST responses, the log now includes allowlisted
`finishReason`, `outputTokens`, `thoughtTokens` and `maxOutputTokens`. Token counts
of `-1` mean unavailable. According to the [Gemini FinishReason reference](https://ai.google.dev/api/generate-content#FinishReason),
`MAX_TOKENS` indicates the request's output limit was reached; `SAFETY` is a
different rejection and must not be bypassed. Provider messages and clinical
response content are never included in these diagnostics. The old generic
`Gemini no completo la respuesta` alone cannot distinguish these conditions.

On 2026-10-13 chat 17 produced that old generic error. A synthetic equivalent
completed with `STOP` at both 2048 and 8192 output limits, so its historical
finish reason could not be established. The default limit was increased to 8192
as headroom, with `GEMINI_MAX_OUTPUT_TOKENS` configuration and safer diagnostics.
97 focused tests passed with
`./mvnw.cmd "-Dtest=GeminiChatPropertiesTest,GeminiJsonClientTest,TriageIaClientTest,ChatServiceTest,SesionVozChatTest,SesionVozIntegracionTest,AdmisionRecepcionServiceTest" test`.
No saved classification was manufactured or historical chat rewritten.

Read the preceding diagnostics: `Gemini rejected reason=...` identifies safe
technical transport/configuration errors (including HTTP status). Eligible
transient REST errors can be attempted twice per Gemini request, counting the
original request; 429 needs a valid `Retry-After`, and TLS/certificate, safety,
authentication, JSON and semantic failures are not HTTP retries. Each request
uses the remaining part of the 45-second operation budget. A classification
correction is a separate Gemini request within the same budget. See
[Gemini REST retries](19-gemini-retries.md) for details.
`Triage IA rejected category=... field=... reason=...` identifies schema/content
validation errors. `Chat IA rejected chatId=... reason=...` identifies service
rejection: `invalid_response`, `invalid_classification`,
`premature_or_unsafe_closure`, `required_closure_missing`, `repeated_question` or
`invalid_voice_classification` or `correction_deadline_exceeded`.
An intermediate text response uses a JSON Schema containing only `mensaje`;
Gemini cannot set closure or priority there. The backend decides whether to keep
interviewing or start final classification. The prompt asks for one datum per turn,
while `LenguajeClinicoChat.REGLAS_PREGUNTA_TEXTO` keeps closure instructions out of
the question-writing task. Incompatible intermediate responses or repeated questions can receive at most
one remote correction per patient turn. Punctuation is not part of format validation:
zero, two or Unicode question marks do not cause an error or format correction, and
Gemini's returned text is preserved. No local question or clinical replacement is
generated. If a semantic correction fails, the text turn rolls back. These messages do not contain credentials,
patient text or provider response bodies. Before interview closure, a failed text
turn is rolled back and its content is not recoverable from history. Once final
classification is requested, the last answer is preserved and `finalizado=true`
with `estadoClasificacion=FALLIDA` is committed on provider failure. A final result
with semantic inconsistencies can request one bounded correction. The Gemini REST
client may retry eligible transient transport/status failures up to two attempts per
request; quota 429 requires a valid `Retry-After`, and safety, authentication,
invalid JSON and semantic failures are not HTTP retries. Processing retry uses
`POST /api/chat/{id}/clasificacion`, not another patient message.
If the final-result verifier supplies a safe rejection category, one correction request
can use that category as feedback; the original rejection text and any provider citations
are never copied into the correction prompt or logs. No queue entry is created when that
correction fails.

When transport errors coexist with `invalid_response`, `repeated_question` or
`premature_or_unsafe_closure`, do not attribute all delays to the provider. A
single message correction or final-result correction adds another Gemini request
within the shared budget.
In the historical chat 8 trace, `invalid_question_format` appeared repeatedly, but
the chat reached `COMPLETADA` after a manual retry. Treat that as a record of the
older punctuation validation behavior: punctuation no longer causes this rejection,
and a format-only issue does not trigger a correction or truncate Gemini's message.
The fix passed 458 Java tests on an isolated database. A synthetic check using real
Gemini and the real chat/provider services, with repositories simulated, accepted
an imperative invitation containing zero question marks in about 3.4 seconds.
The probe forced that style to reproduce the rejected case; it did not test the
HTTP endpoint or use patient data, and its timing is not a latency guarantee.
An earlier October 21, 2026 diagnosis also found two local contract/coverage mismatches:
blank `recomendacionSeguridad` rejected during an unfinished non-urgent turn, and
a denied `debilidad repentina` question not recognized by the completeness check.
The recommendation contract was fixed, and the later brief-interview change
removed the checklist requirement entirely. Final safety requirements, priority
rules, and provider transport errors remain distinct. Rejected raw responses are not stored, so a
generic closure rejection alone does not identify its exact underlying reason.

Verified on 2026-10-21: the new regressions reproduced both mismatches before
the fix. The 47 focused tests and then all 406 Java tests passed using
`python scripts/test_backend_isolated.py`, with an independently created
disposable database and Compose disabled. The application database was read
only for diagnosis; no chat or classification was rewritten. Restart the running
backend to load the changes; no mobile rebuild is needed for these server fixes.

The additional `field=observaciones reason=invalid_text` report prompted a review
of the descriptive-string contract. Additional notes may now be blank in any
phase, and pending clinical descriptive strings may be blank only during the
interview. Required keys/types and final clinical/safety checks are preserved;
an invalid-text log alone cannot distinguish a blank value from a null or a
wrong type because rejected provider content is deliberately not logged.
The additional regressions reproduced the failures before the descriptive-field
fix. Afterward, 105 focused tests and all 410 Java tests passed on disposable
databases with Compose disabled. No application data or model configuration
was changed during this follow-up.

Final verification on 2026-10-13: 397 Java tests passed with
`python scripts/test_backend_isolated.py`; the runner created and removed its own
database with Compose disabled. A synthetic real-provider correction also completed
without adding a patient message. See [the database verification incident](15-test-isolation-incident.md)
for the earlier shared-database loss and the isolation safeguard introduced afterwards.

The final classification input includes the interviewer questions strictly as
context, so short patient replies can be associated with the questions they
answer; interviewer text is not patient evidence. The symptom validator also
recognizes traumatic complaints such as moratón, hematoma and hormigueo.

The shared onset validator recognizes `anoche`, `esta mañana`, `esta tarde`
and `esta noche`, in addition to existing dates/durations. A complete interview
using these expressions must not be rejected as lacking onset. Explicitly
unknown dates such as `no recuerdo si fue anoche` do not supply onset evidence.

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
