# Hospital Selection And Arrival

## Flow Overview

The hospital selection flow allows patients to choose a hospital and get arrival time estimates. The backend provides route options but does not store the user's choice or enforce the estimated arrival time for queue entry.

## API Sequence

### 1. Get Nearby Hospitals

```http
GET /api/hospitales/cercanos?latitud={lat}&longitud={lon}&codigoEspecialidad={codigoEspecialidad}&transporte={transporte}
```

- Filters hospitals by selected specialty and distance from patient's location.
- Returns hospitals sorted by proximity.
- `transporte` is optional and defaults to `transporte-publico`. Valid values:
  - `transporte-publico`: Public transit (buses, trains, etc.)
  - `vehiculo`: Driving/car
  - `vehiculo-dos-ruedas`: Two-wheel vehicles (motorcycles)
  - `caminar`: Walking
  - `bicicleta`: Bicycling
- Each hospital includes `tiempoEstimadoArriboMejorRuta`, the estimated arrival time of the best route (the route labeled `DEFAULT_ROUTE` by Google) for the requested transport mode. It is `null` when Google cannot compute a route for that hospital.
- Patient reviews the list and selects one.

### 2. Select Hospital

```http
POST /api/atencion/hospital
```

- Patient confirms their hospital choice.
- Creates or updates the consultation with the selected hospital.
- Enters the consultation into the queue (`EN_COLA` + `EntradaCola`) immediately with default priority (`NORMAL`).
- The AI triage chat is optional: if the patient completes it later, the existing `EntradaCola` priority is updated with the pretriage result.

### 3. Get Arrival Time Estimates

```http
GET /api/hospitales/{idHospital}/tiempo-arribo?latitud={lat}&longitud={lon}&transporte={transporte}
```

- Returns multiple route options to reach the selected hospital.
- `transporte` is required and must be one of the following valid values:
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
    "tiempoEstimadoArribo": "00:35:45",
    "idHospital": 1,
    "distanciaMetros": 9331,
    "PolylineCode": "vbtrEvkccJsDcFaAwAs@h@a@{@...",
    "combinacionesLineas": [
      {
        "tipoTransporte": "caminar",
        "indicaciones": "Dirígete al nordeste por Independencia hacia Cnel. Dorrego",
        "nombreLinea": null
      },
      {
        "tipoTransporte": "transporte-publico",
        "indicaciones": "Autobús en dirección a 116 (Rojo): Once",
        "nombreLinea": "Plaza Once (98 - 3n, 3v) - Calle 153, 2457"
      },
      {
        "tipoTransporte": "caminar",
        "indicaciones": "Dirígete al norte por Av. Hipólito Yrigoyen/RN205 hacia Riobamba",
        "nombreLinea": null
      }
    ],
    "transporte": "transporte-publico"
  },
  {
    "tiempoEstimadoArribo": "00:38:41",
    "idHospital": 1,
    "distanciaMetros": 9987,
    "PolylineCode": "vbtrHAwAs@h@a@{@...",
    "combinacionesLineas": [
      {
        "tipoTransporte": "caminar",
        "indicaciones": "Dirígete al suroeste por Independencia hacia Tte. Coronel Luis María Campos",
        "nombreLinea": null
      },
      {
        "tipoTransporte": "transporte-publico",
        "indicaciones": "Autobús en dirección a R1 - Wilde - Villa Del Parque (X Ctro. Avellaneda)",
        "nombreLinea": "Nazarre Y Cuenca - San Carlos 2070"
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
- `combinacionesLineas`: One item per step of the route (`legs[].steps[]`), regardless of transport mode. Each item has:
  - `tipoTransporte`: the system transport key for the step (`caminar`, `transporte-publico`, `vehiculo`, `bicicleta`, `vehiculo-dos-ruedas`).
  - `indicaciones`: the navigation instruction text for the step.
  - `nombreLinea`: transit line name (`transitDetails.transitLine.name`), present only for `transporte-publico` steps; `null` otherwise.
- `transporte`: The transport mode used for this route (as requested in the query).

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

The consultation enters the queue at hospital selection (`POST /api/atencion/hospital`):

- The consultation becomes `EN_COLA` and an `EntradaCola` is created with default priority.
- Queue position and estimated attention time are calculated based on current queue state.
- The AI triage chat is optional; when it finishes, the `EntradaCola` priority is updated with the pretriage result.

Patients who temporarily left the queue return through:

```http
POST /api/paciente/consulta/llegue
```

- This is independent of the arrival time estimation.
- The patient can return to the queue regardless of whether they followed the estimated time.
- Queue position and estimated attention time are recalculated at this moment based on current queue state.

## Public Transport Considerations

When `transporte` indicates public transport:

- Multiple routes may be returned (different bus lines, combinations, etc.).
- Real-time delays are not accounted for in estimates.
- The patient must decide which route best fits their current situation.
- The backend cannot predict actual arrival time due to traffic, delays, or patient decisions.

## Key Principle

The backend provides information; the frontend and patient make decisions. The system does not track or validate the patient's actual route or arrival time against the estimates.
