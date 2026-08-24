# Queue And Estimation

## Source Of Truth

`EntradaCola` is the source of truth for queue state.

Do not use `GestorDeCola.consultasEnEspera` for new estimation logic. It is legacy compatibility state.

## Queue Scope

Each queue is per:

```text
hospital + especialidad
```

This is represented by `GestorDeCola`.

## Queue Ordering

Estimable queue entries are ordered by:

```text
prioridad DESC,
ordenRelativo ASC,
fechaHoraIngreso ASC
```

Priority values:

```text
5 = RIESGO_VITAL_INMEDIATO
4 = MUY_URGENTE
3 = URGENTE
2 = NORMAL
1 = NO_URGENTE
```

## Entries Counted For Estimation

Only this state counts:

- `EntradaCola.EN_COLA`

These states do not count as waiting queue entries:

- `LLAMADO`
- `EN_ESPERA`
- `ATRASADO`
- `EN_ATENCION`
- `FINALIZADA`
- `CANCELADA`

## Dynamic Estimation

The estimated attention time is calculated by `EstimacionAtencionService` every time it is requested.

It is not persisted as final truth.

Formula:

```text
pacientesAntes = index of patient in ordered EN_COLA list
medicosActivos = count of SesionAtencionMedica.ACTIVA for same hospital/specialty
medicosParaEstimacion = max(medicosActivos, 1)
bloquesEspera = pacientesAntes / medicosParaEstimacion
fechaHoraAtencionEstimada = now + bloquesEspera * minutosPromedioAtencion
```

The average duration is configurable:

```properties
pretriage.estimacion.minutos-promedio-atencion=10
```

## No Active Doctors

If there are no active doctors:

- Estimation still uses one virtual doctor.
- Response exposes `hayMedicosActivos=false`.
- Response includes a message explaining the estimate is tentative.

Example response:

```json
{
  "fechaHoraAtencionEstimada": "2026-07-09T23:17:12",
  "hayMedicosActivos": false,
  "medicosActivos": 0,
  "medicosParaEstimacion": 1,
  "posicionEnCola": 1,
  "pacientesAntes": 0,
  "minutosPromedioAtencion": 10,
  "mensaje": "No hay medicos atendiendo esta especialidad en este momento. La hora es una estimacion tentativa."
}
```

## End-Of-Queue Estimate For Hospital Ranking

For the nearby-hospital ranking (`GET /api/hospitales/cercanos?ordenarPor=tiempo-atencion`) a prospective wait for a **new** patient that would join at the end of the queue is computed via `EstimacionAtencionService.calcularEsperaParaNuevaConsulta`:

```text
pacientesEnCola        = count(EntradaCola.EN_COLA for hospital+especialidad)
medicosActivos         = count(SesionAtencionMedica.ACTIVA for hospital+especialidad)
medicosParaEstimacion  = max(medicosActivos, 1)
bloquesEspera          = pacientesEnCola / medicosParaEstimacion   // floor division, same as per-patient formula
minutosEsperaEstimados = bloquesEspera * minutosPromedioAtencion
fechaHoraAtencionEstimada = now + minutosEsperaEstimados
```

Only `EntradaCola.EN_COLA` entries are counted, never `GestorDeCola.consultasEnEspera`. A hospital is considered available for ranking only when `medicosActivos > 0` (`disponible=true`); otherwise it is excluded and an empty ranking means the frontend must display "no hay hospitales disponibles". Valid `ordenarPor` values live in `AtencionHospitalService.ORDENES_VALIDOS` (`distancia`, `tiempo-atencion` y combinados `distancia&tiempo-atencion`/`tiempo-atencion&distancia`), el orden es indistinto y extensible con `&`; el combinado usa suma de rankings (distancia según Google + tiempo según minutosEspera).

## When Estimation Changes

The estimate can change whenever:

- A doctor starts session.
- A doctor pauses session.
- A doctor closes session.
- A patient with higher priority enters queue.
- A patient leaves queue manually.
- A delayed patient arrives.
- A patient is called or enters attention.
- A consultation is finalized or cancelled.

## Real-Time Updates

Patients can subscribe through authenticated SSE:

```http
GET /api/atencion/tiempos/suscribirse/{consultaId}
Accept: text/event-stream
```

- The authenticated patient must own the consultation.
- An initial `tiempo-estimado` event is sent when the connection opens.
- Estimates are recalculated from `EntradaCola` and `EstimacionAtencionService` every 15 seconds while connected.
- A `heartbeat` event is sent every 30 seconds.
- Multiple simultaneous emitters are supported for the same consultation.
- Connections are completed when the consultation no longer has an active `EN_COLA` estimate.