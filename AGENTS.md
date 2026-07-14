# Agent Context

## Project
Pretriage backend is a Spring Boot system for medical pre-triage, hospital selection, specialty queues, doctor sessions, and AI-assisted triage.

## Code Discovery
Prefer codebase-memory-mcp for code discovery:
1. search_graph
2. trace_path
3. get_code_snippet
4. query_graph
5. get_architecture

Use grep/rg only for configs, scripts, literals, or when graph results are insufficient.

## Source Of Truth
- Queue state: `EntradaCola`.
- Estimated attention time: `EstimacionAtencionService`.
- AI triage structured result: `Chat.resultadoTriageJson`.
- Doctor active capacity: `SesionAtencionMedica` with `EstadoSesionMedica.ACTIVA`.
- Hospital specialty availability: `Hospital.especialidades`.
- Reception admission: `AdmisionRecepcion` + `SesionRecepcion`; it does not use `Chat`.
- Shared queue entry for digital and reception flows: `IngresoColaService`.

## Domain Rules
- Patients choose a medical specialty before choosing a hospital.
- Hospitals are filtered by distance and selected specialty.
- Queue is per hospital and specialty through `GestorDeCola`.
- Every active queued consultation should have an `EntradaCola`.
- New estimation logic must not use `GestorDeCola.consultasEnEspera`.
- Only `EntradaCola.EN_COLA` counts for estimated attention time.
- `EN_ESPERA`, `ATRASADO`, `CANCELADA`, and `FINALIZADA` do not count as waiting queue entries.
- Queue order is priority DESC, ordenRelativo ASC, fechaHoraIngreso ASC.
- Priority is numeric: 5 is highest, 1 is lowest.
- If no doctors are active, estimate with one virtual doctor and expose `hayMedicosActivos=false`.
- A doctor can use only one room at a time; a room can be used by only one doctor at a time.
- A doctor does not change specialty inside an active session; they must close the current session and start another.
- If a doctor pauses, that session is not active capacity for estimation.
- If a patient marked `ATRASADO` returns, they go to first place within their priority level.
- If a patient manually goes to `EN_ESPERA`, they return to their previous relative position.

## Verification
- Compile: `./mvnw.cmd test -DskipTests`
- Focused estimation tests: `./mvnw.cmd "-Dtest=AtencionHospitalServiceTest,EstimacionAtencionServiceTest" test`
- Full suite needs Docker Desktop access: `./mvnw.cmd test`
- Real chat E2E: `python scripts/e2e_chat.py --messages-file scripts/chat_case_example.txt`

## Documentation Maintenance
- Documentation is part of the definition of done.
- Any change to domain entities, relationships, states, business rules, flows, endpoints, DTO contracts, configuration, or verification commands must update the corresponding files under `docs/` in the same change.
- Regenerate `docs/generated/domain-model.md` and `docs/generated/domain-model.mmd` after changing JPA entities by running `python scripts/generate_domain_diagram.py`.
- Before finishing, verify documented endpoint paths and state transitions against the implementation.
- Do not leave documentation updates for a later task.
## Useful Docs
- Human overview: `docs/00-overview.md`
- Reception-assisted admission: `docs/09-reception-admission.md`
- Agent deep context: `docs/ai-agent-context.md`
- Queue and estimation: `docs/04-queue-and-estimation.md`
- E2E chat debugging: `docs/08-e2e-chat-debugging.md`
