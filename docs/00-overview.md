# Pretriage Backend Overview

Pretriage backend manages the first medical attention flow before a patient is seen by a doctor. The system combines patient self-service, AI-assisted triage, hospital and specialty selection, hospital queues, doctor sessions, and estimated attention time.

## Scope

The current focus is first attention only. The system does not model referral, diagnosis, treatment follow-up, or post-consultation derivation.

## Main Actors

- Patient: selects specialty and hospital, completes AI triage, enters queue, checks state and estimated attention time.
- Doctor: starts an attention session at an assigned hospital/specialty/room, calls patients, marks absences, pauses or closes the session.
- Admin: future module. Admin creates doctors, hospitals, rooms, specialties, and assignments. Do not assume admin UI exists yet.
- AI triage bot: collects symptoms and produces structured triage output used to assign priority.

## Core Modules

- Authentication: Auth0 login/register integration.
- Hospital selection: filters hospitals by selected specialty and distance.
- Medical specialties: represented by `EspecialidadMedica`.
- AI triage chat: creates a chat, stores patient and bot messages, stores structured triage JSON.
- Queue management: uses `EntradaCola` as queue state per hospital/specialty.
- Doctor attention: uses `SesionAtencionMedica`, rooms, assignments, and historical `AtencionMedica` records.
- Estimated attention time: recalculated dynamically using queue state and active doctor sessions.
- Real-time estimation: authenticated SSE subscriptions support periodic updates, heartbeat, and multiple connections per consultation.

## Current Technical Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL through Docker Compose
- Spring Security OAuth2 Resource Server
- Spring AI with Ollama
- Ollama model configured as `llama3.2:3b`
