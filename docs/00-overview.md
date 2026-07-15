# Pretriage Backend Overview

Pretriage backend manages the first medical attention flow before a patient is seen by a doctor. The system combines patient self-service, AI-assisted triage, hospital and specialty selection, hospital queues, doctor sessions, and estimated attention time.

## Scope

The current focus is first attention only. The system does not model referral, diagnosis, treatment follow-up, or post-consultation derivation.

## Main Actors

- Patient: selects specialty and hospital, completes AI triage, enters queue, checks state and estimated attention time.
- Doctor: starts an attention session at an assigned hospital/specialty/room, calls patients, marks absences, pauses or closes the session.
- Hospital admin: manages scoped staff memberships and invitations. Room, specialty and assignment administration remains incremental.
- AI triage bot: collects symptoms and produces structured triage output used to assign priority.

## Core Modules

- Authentication: Auth0 login/register integration.
- Hospital selection: filters hospitals by selected specialty and distance.
- Medical specialties: represented by `EspecialidadMedica`.
- AI triage chat: creates a chat, stores patient and bot messages, stores structured triage JSON.
- Queue management: uses `EntradaCola` as queue state per hospital/specialty.
- Doctor attention: uses SesionAtencionMedica, rooms, assignments, and historical AtencionMedica records.
- Reception admission: supports DNI-based in-person registration, structured form triage, and entry into the same dynamic queue without using chat.
- Estimated attention time: recalculated dynamically using queue state and active doctor sessions.
- Real-time estimation: authenticated SSE subscriptions support periodic updates, heartbeat, and multiple connections per consultation.
- Clinical-file storage: downloads study files from Amazon S3 only when explicitly enabled.

## Optional Clinical-File Storage

Amazon S3 is disabled by default so local reception, triage, queue and medical-session
flows do not require AWS credentials. To enable downloads of clinical-study files,
configure:

```properties
PRETRIAGE_STORAGE_S3_ENABLED=true
AWS_S3_REGION=us-east-1
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
```

When disabled, the application starts without these variables. An attempted study
download returns an explicit storage-disabled error.

## Current Technical Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL through Docker Compose
- Spring Security OAuth2 Resource Server
- Spring AI with Ollama
- Ollama model configured as `llama3.2:3b`
