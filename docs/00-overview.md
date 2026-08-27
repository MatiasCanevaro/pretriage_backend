# Pretriage Backend Overview

Pretriage backend manages the first medical attention flow before a patient is seen by a doctor. The system combines patient self-service, AI-assisted triage, hospital and specialty selection, hospital queues, doctor sessions, and estimated attention time.

## Scope

The current focus is first attention only. The system does not model referral, diagnosis, treatment follow-up, or post-consultation derivation.

## Main Actors

- Patient: selects specialty and hospital, completes AI triage, enters queue, checks state and estimated attention time.
- Doctor: starts an attention session at an assigned hospital/specialty/room, calls patients, marks absences, pauses or closes the session.
- Hospital admin: manages scoped staff memberships and invitations, and hospital configuration (enabled specialties and rooms). Assignment administration remains incremental.
- AI triage bot: collects symptoms and produces structured triage output used to assign priority.

## Core Modules

- Authentication: Auth0 login/register integration with refresh-token rotation via `POST /api/renovar` (`AuthController.renovar`, `AuthService.renovarTokenUsuario`, `RefreshTokenRequest`/`LoginResponseDTO`, `RefreshTokenInvalidoException` -> `401`; `offline_access` scope; public endpoint in `SpringSecurityConfig`).
- Hospital selection: filters hospitals by selected specialty and distance; can order/filter by estimated attention time and shows only hospitals available for attention (with active doctors), displaying the estimated wait alongside each hospital.
- Medical specialties: represented by `EspecialidadMedica`.
- AI triage chat: creates a chat, stores patient and bot messages, stores structured triage JSON.
- Queue management: uses `EntradaCola` as queue state per hospital/specialty.
- Doctor attention: uses SesionAtencionMedica, rooms, assignments, and historical AtencionMedica records.
- Reception admission: supports DNI-based in-person registration, structured form triage, and entry into the same dynamic queue without using chat.
- Estimated attention time: recalculated dynamically using queue state and active doctor sessions.
- Real-time estimation: authenticated SSE subscriptions support periodic updates, heartbeat, and multiple connections per consultation.
- Medical studies management: patients can upload, list, download, and delete medical study files (PDFs, images) stored in AWS S3. Doctors can access patient studies during attention through clinical history endpoints.
- Health insurance credentials: patients and receptionists can load, list, update, and delete credentials. Load and update validate the health insurance at runtime selecting the validator for the requested obra social (`FabricaValidadoresCredencialesObraSocial`). Only a demo mock (`OSDE`) exists; real integrations with the health insurance companies are out of scope and can be plugged in by implementing `ValidadorCredencialObraSocial`.

## Current Technical Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL through Docker Compose
- Spring Security OAuth2 Resource Server
- Spring AI with Ollama
- Ollama model configured as `llama3.2:3b`
