# E2E Chat Debugging

The script `scripts/e2e_chat.py` tests the real chat flow without mocking AI.

## Basic Run

```powershell
python scripts\e2e_chat.py
```

## Run With Messages File

```powershell
python scripts\e2e_chat.py --messages-file scripts\chat_case_example.txt
```

Messages file format:

- One patient message per line.
- Empty lines are ignored.
- Lines starting with `#` are ignored.

Example:

```text
# One patient message per line
Tengo fiebre y dolor de garganta desde ayer.
La fiebre llego a 38.5 y el dolor es 5 de 10.
No tengo dificultad para respirar ni dolor de pecho.
No tengo enfermedades previas, no tomo medicacion y no tengo alergias.
```

## Debug Log

Set output path:

```powershell
python scripts\e2e_chat.py --messages-file scripts\chat_case_example.txt --debug-log scripts\debug_case.json
```

Debug log includes:

- `chatId`
- masked user
- hospital place id
- specialty
- scripted patient messages
- bot answers
- final estimated attention response
- stored `resultado_triage_json`
- queue priority and state
- persisted chat messages from DB

## What The Script Validates

- Ollama is reachable and has the configured model.
- Login works with `.env` credentials.
- DB seed creates minimal patient, hospital, and specialty data.
- Hospital selection works.
- Chat starts and receives real bot answers.
- Triage finalizes.
- Consultation enters `EN_COLA`.
- `EntradaCola` also becomes `EN_COLA`.

## Useful Flags

```powershell
--backend-url http://localhost:8080
--env-file .env
--messages-file scripts\my_case.txt
--debug-log scripts\debug_my_case.json
--skip-ollama-check
--message "Tengo fiebre"
--message "No tengo dolor de pecho"
```

`--message` can be repeated and takes precedence over `--messages-file`.
