# AI Triage

## Purpose

AI triage collects initial symptoms and produces a structured result. It does not replace medical evaluation.

## Model

Spring AI Ollama is configured with:

```properties
spring.ai.ollama.chat.options.model=llama3.2:3b
```

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
