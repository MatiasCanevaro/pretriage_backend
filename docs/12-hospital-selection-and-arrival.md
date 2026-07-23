# Hospital Selection And Arrival

## Flow Overview

The hospital selection flow allows patients to choose a hospital and get arrival time estimates. The backend provides route options but does not store the user's choice or enforce the estimated arrival time for queue entry.

## API Sequence

### 1. Get Nearby Hospitals

```http
GET /api/hospitales/cercanos?latitud={lat}&longitud={lon}&especialidadId={especialidadId}
```

- Filters hospitals by selected specialty and distance from patient's location.
- Returns hospitals sorted by proximity.
- Patient reviews the list and selects one.

### 2. Select Hospital

```http
POST /api/atencion/hospital
```

- Patient confirms their hospital choice.
- Creates or updates the consultation with the selected hospital.
- Does not yet enter the queue.

### 3. Get Arrival Time Estimates

```http
GET /api/hospitales/{idHospital}/tiempo-arribo?latitud={lat}&longitud={lon}&modoTransporte={modoTransporte}
```

- Returns multiple route options to reach the selected hospital.
- `modoTransporte` must be one of the following valid values:
  - `transporte-publico`: Public transit (buses, trains, etc.)
  - `vehiculo`: Driving/car
  - `vehiculo-dos-ruedas`: Two-wheel vehicles (motorcycles)
  - `caminar`: Walking
  - `bicicleta`: Bicycling
- Each route includes estimated duration and details.

Example response:

```json
 [
    {
      "tiempoEstimadoArribo": "01:00:00",
      "idHospital": 1,
      "distanciaMetros": "5000",
      "PolylineCode": "fdsjfksda",
      "combinacionesLineas": [
        {
          "nombreLinea": "Línea 85 A"
        },
        {
          "nombreLinea": "Línea 101 B"
        }
      ],
      "transporte": "transporte-publico"
    },
  {
    "tiempoEstimadoArribo": "01:30:00",
    "idHospital": 1,
    "distanciaMetros": "5000",
    "PolylineCode": "fdsjfksdfdsaa--qQ@",
    "combinacionesLineas": [
      {
        "nombreLinea": "Línea 85 A"
      },
      {
        "nombreLinea": "Línea 7 B"
      }
    ],
    "transporte": "transporte-publico"
  }
  ]

```

## Response Fields

- `tiempoEstimadoArribo`: Estimated travel time in HH:MM:SS format.
- `idHospital`: The destination hospital ID for this route.
- `distanciaMetros`: Total distance of the route in meters.
- `PolylineCode`: Google Maps encoded polyline string representing the route path. This encoding compresses multiple latitude/longitude coordinate points into a single string format that can be decoded to reconstruct the exact route geometry on a map.
- `combinacionesLineas`: For public transport, lists the transit line names used in the route (e.g., bus lines).
- `transporte`: The transport mode used for this route.

## Frontend Responsibilities

The frontend must:

- **Display route options**: Show all available routes from the backend response.
- **User selection**: Allow the patient to choose which route to follow.
- **No persistence**: The selected route is NOT sent to the backend.
- **Arrival confirmation**: When the patient physically arrives, the frontend calls the queue entry endpoint.

## Backend Limitations

The backend:

- **Does not store route choice**: No database field records which route the patient selected.
- **Does not enforce arrival time**: Queue entry is NOT based on the estimated arrival time.
- **Provides options only**: The `/tiempo-arribo` endpoint is informational, not prescriptive.

## Queue Entry

Queue entry happens when the patient marks arrival from the frontend:

```http
POST /api/paciente/consulta/llegue
```

- This is independent of the arrival time estimation.
- The patient can enter the queue regardless of whether they followed the estimated time.
- Queue position and estimated attention time are calculated at this moment based on current queue state.

## Public Transport Considerations

When `modoTransporte` indicates public transport:

- Multiple routes may be returned (different bus lines, combinations, etc.).
- Real-time delays are not accounted for in estimates.
- The patient must decide which route best fits their current situation.
- The backend cannot predict actual arrival time due to traffic, delays, or patient decisions.

## Key Principle

The backend provides information; the frontend and patient make decisions. The system does not track or validate the patient's actual route or arrival time against the estimates.
