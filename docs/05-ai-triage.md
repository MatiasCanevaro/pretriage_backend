# AI Triage

## Purpose

AI triage collects initial symptoms and produces a structured result. It does not replace medical evaluation.

## Model

Spring AI Ollama is configured with:

```properties
spring.ai.ollama.chat.model=llama3.2:3b
spring.ai.ollama.chat.temperature=0
spring.ai.ollama.chat.seed=42
spring.ai.retry.max-attempts=1
spring.http.clients.connect-timeout=2s
spring.http.clients.read-timeout=45s
```

The HTTP limits apply to the auto-configured Ollama client. Reception-assisted
triage catches provider failures and continues with its deterministic fallback;
it must not remain pending indefinitely when Ollama is unavailable. Only one
provider attempt is made; the default exponential retry sequence is intentionally
disabled.

Reception form classification sends the generated `TriageResultDTO` JSON Schema
to Ollama as a provider-native structured-output constraint and validates the
response against that same schema. This prevents semantically valid answers with
renamed or nested fields from being discarded and replaced by the conservative
fallback.

Classification uses temperature `0` and a fixed seed so identical forms are
reproducible. A deterministic coherence guard also caps an explicitly low-risk
form at level `2` when pain is `0..3`, evolution is improving, fever is absent,
and no alarm signs were recorded. Reception forms may report multiple pains; the
classifier evaluates all of them and uses the highest intensity for the structured
result and deterministic rules. The same guard applies to the provider fallback.

## Chat Flow

1. Patient starts a chat.
2. Backend creates `Chat` associated with patient.
3. Patient sends messages.
4. Backend sends conversation context to AI.
5. Bot asks follow-up questions until enough information is available.
6. On finalization, backend stores structured result in `Chat.resultadoTriageJson`.
7. Result priority maps to `NivelDeGravedad`.
8. Consultation enters queue.

## Structured Result

The result contains fields such as:

- `motivoConsulta`
- `sintomas`
- `inicio`
- `evolucion`
- `intensidadDolor`
- `signosAlarma`
- `antecedentesRelevantes`
- `medicamentos`
- `alergias`
- `posibilidadEmbarazo`
- `observaciones`
- `nivelPrioridad`
- `requiereAtencionInmediata`
- `recomendacionSeguridad`

## Priority Normalization

If AI returns invalid or missing priority:

- Immediate attention maps to priority 5.
- Otherwise default priority is 3.

Priority then maps to medical severity:

```text
5 -> RIESGO_VITAL_INMEDIATO
4 -> MUY_URGENTE
3 -> URGENTE
2 -> NORMAL
1 -> NO_URGENTE
```

## Debugging

Use the real E2E script instead of mocked AI tests when tuning behavior:

```powershell
python scripts\e2e_chat.py --messages-file scripts\chat_case_example.txt --debug-log scripts\debug_case.json
```

The debug log includes:

- Patient messages.
- Bot messages.
- Stored structured triage JSON.
- Assigned severity and queue priority.
- Queue state.
