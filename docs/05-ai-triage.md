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
5. Bot asks follow-up questions until enough information is available. A repeated
   question does not finalize the interview; the backend asks for missing data.
6. On finalization, backend stores the structured result and classification origin
   in `Chat.resultadoTriageJson`.
7. Result priority maps to `NivelDeGravedad`.
8. The existing queue entry's priority is updated. Hospital selection already
   entered the consultation into its hospital/specialty/sector queue.

The chat preserves a valid final model classification. It does not replace that
result with a locally generated summary just because the conversation contains
enough information. Missing information triggers follow-up questions; alarm
conditions and the 12-message patient limit allow an earlier or bounded close.

`ChatTurnResponse.origenRespuesta` identifies the source of each response:
`OLLAMA` or `FALLBACK_LOCAL`. The final JSON additionally contains
`origenClasificacion` with the same values. A successful HTTP response alone does
not prove that Ollama supplied the classification. These fields are additive;
`respuesta` and `atencionEstimada` retain their existing meanings.

## Voice Chat (Gemini Live)

`WS /api/chat/{id}/voz` adds an interactive voice channel to the same chat
(model `gemini-3.8-live`, configurable). Gemini Live is only the voice layer:

1. The backend (`SesionVozChat`) proxies client audio to Gemini Live over
   `BidiGenerateContent`, so the API key never reaches the browser.
2. Gemini's system instruction forbids it from asking its own questions. When the
   patient finishes speaking it calls the function `registrar_respuesta_paciente`
   with the literal transcription.
3. The backend passes that text to `ChatService.enviarMensaje`; the existing bot
   (Ollama + validation + local fallback) produces the next question or the final
   classification, persists both messages, and enters the queue exactly as in
   the text chat.
4. The function response returns that text and Gemini reads it aloud verbatim.
   After the closing message of a finalized triage is spoken, the session ends.

The stored patient message is Gemini's transcription of the speech, so
recognition errors reach the triage as text; the patient sees the transcription
and the persisted turn through `transcripcion_paciente` and `turno_bot` events.
Protocol details: `docs/06-api-reference.md#voice-chat-gemini-live`.

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

## Chat Output Validation

`TriageIaClient` supplies the response schema to Ollama and validates the returned
JSON before the chat uses it. Unreported pain intensity may be `null`; priority
may be `null` while the interview remains open. A final result must have a valid
priority from 1 to 5 and meaningful clinical content. Invalid provider output is
handled as a provider failure and uses the explicitly identified local fallback.
When the conversation is ready to close, reaches its limit or contains a local
alarm, the native schema requires `finalizado=true` and a non-null integer
priority. The model receives the patient's reported messages together for that
classification, and is instructed to close rather than ask another question.
The final instruction explicitly derives `motivoConsulta` from reported symptoms
instead of allowing `"no informado"` for a described complaint, preserves the
reported onset, and separates current alarm symptoms from medical history.
Validation failures log fixed `field` and `reason` identifiers without logging
the patient's text or raw model response.

When a valid final result requires immediate attention, the chat response also
includes its `recomendacionSeguridad`, even if the model's `mensaje` omits it.

The chat makes one provider call per turn and validates its response locally;
it does not use the schema advisor's automatic correction loop. Invalid output
or a transport failure enters local fallback without another model call.

## Priority Mapping

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
python scripts\e2e_chat.py --backend-url http://localhost:18080 --db-name pretriage_chat_e2e --messages-file scripts\chat_case_example.txt --debug-log target\debug_case.json
```

The debug log includes:

- Patient messages.
- Bot messages.
- Stored structured triage JSON.
- Assigned severity and queue priority.
- Queue state.

## Verification of the Ollama Chat Fix

The 2026-09-29 verification used `llama3.2:3b` and an isolated PostgreSQL database:

- Full Java suite: 256 tests passed; Python E2E helper suite: 11 tests passed.
- The four-message example completed with final origin `OLLAMA`, preserved
  `inicio="desde ayer"` and `intensidadDolor=5`, and stored priority 2 consistently
  in the result, consultation severity and queue. Intermediate questions can
  still come from the explicitly identified local fallback.
- The initial synthetic chest-pain/breathing-difficulty alarm used local fallback
  because Ollama supplied `motivoConsulta="no informado"` despite reported symptoms.
  After correcting the final instruction, the real API case completed in one
  turn with origin `OLLAMA`, priority 5, alarm signs and immediate attention,
  preserving the reported onset of ten minutes. Its visible chat message also
  includes the urgent safety recommendation. No `--allow-fallback` was used.

These checks verify integration and persistence, not clinical accuracy. The
model may still return invalid or incomplete content, so the fallback remains
necessary and visible. Use explicit case expectations when evaluating clinical
classification; the E2E does not assume the old local priority is the correct
model classification.
