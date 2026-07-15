# Backend change summary: reception admission improvements

This document summarizes the implemented changes included in the
`feature/mejoras-admision-recepcion` delivery. It does not claim that the future
membership and invitation architecture is implemented; that proposal is documented
separately in `10-staff-identity-memberships-and-invitations.md`.

## Patient lookup and admission safety

- Patient lookup now reports `atencionEnCurso` and `estadoAtencionEnCurso`.
- Reception can warn before attempting admission when the patient is already in an
  active consultation.
- Admission creation retains the authoritative active-consultation concurrency
  check.

## Complete structured address

- Admission requests now require `ciudad` and `provincia` in addition to street,
  number, optional floor, and postal code.
- `Direccion` persists city and province.
- Lookup responses return the complete stored address.
- Verified address data submitted during admission updates an existing patient's
  address instead of being discarded.

## Reception triage contract

- `motivoConsulta` remains the required short account of the main complaint.
- Additional `sintomas` may be empty and no longer need to duplicate the motive.
- The former single `intensidadDolor` and `localizacionDolor` request fields were
  replaced by repeatable `dolores` entries.
- Each `DolorReportadoRequest` requires a location and an intensity from 0 to 10.
- This is a breaking request-contract change; clients must regenerate from OpenAPI.

## Priority consistency and multiple pains

- The classifier evaluates every reported pain and normalizes the result to the
  highest intensity.
- The deterministic fallback uses that same maximum.
- An explicit low-risk case —all pain at 0..3, improving, no fever, and no alarm
  signs— is normalized to priority level 2.
- Stable or worsening pain is not lowered by that coherence rule.
- Provider output cannot override the normalized maximum pain value.

## AI provider reliability

- Reception classification uses provider-native structured output with schema
  validation.
- Ollama runs reception classification with temperature 0 and a fixed seed for
  reproducibility.
- Spring AI retry attempts are limited so the deterministic fallback is reached
  promptly.
- Connect and read timeouts bound unavailable or stalled Ollama calls.
- The corrected Spring AI model property is `spring.ai.ollama.chat.model`.

## Local persistence behavior

- The default JPA schema mode changed from destructive `create` to configurable
  `${JPA_DDL_AUTO:update}`.
- Local data is therefore preserved across normal backend restarts unless a clean
  schema is requested explicitly.

## Tests added or extended

- Patient lookup exposes the active consultation state.
- Reception-created patients persist city and province.
- A mild improving headache maps to normal priority in fallback.
- Mild stable pain is not incorrectly lowered.
- Multiple pains use the greatest intensity for fallback classification.
- Admission finalization tests use the new repeatable pain contract.

## Verification performed

- Focused reception and triage service tests passed.
- The complete Maven test suite passed with 80 tests and no failures.
- The live OpenAPI document was exported and the frontend transport types were
  regenerated against the new request schema.
