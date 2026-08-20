1. Flujo normal: selección, pretriage y encolamiento
```mermaid
flowchart TD
    A["Inicio"] --> B["Seleccionar hospital"]
    B --> C{"¿Realiza pretriage?"}

    C -->|"No"| D["Encolar con prioridad mínima"]
    C -->|"Sí"| E["Realizar pretriage"]
    
    E --> F{"¿El caso tiene mayor gravedad?"}
    F -->|"No"| D
    F -->|"Sí"| G["Encolar con prioridad según gravedad"]

    D --> H["Paciente en espera"]
    G --> H
```
2. Flujo de ausencia y atraso
```mermaid
flowchart TD
    A["Paciente en espera"] --> B{"¿Se ausenta?"}

    B -->|"No"| A
    B -->|"Sí"| C["Desencolar paciente"]
    
    C --> D["Iniciar ventana de espera: 1 hora"]
    D --> E{"¿Marca 'llegué'?"}

    E -->|"Sí"| F["Volver a encolar"]
    E -->|"No"| G["Timeout de 1 hora"]
    
    G --> H["Cancelar turno"]

    F --> I["Paciente nuevamente en espera"]
```
3. Flujo de cancelación voluntaria
```mermaid
flowchart TD
    A["Paciente con turno activo"] --> B{"¿Cancela el turno?"}

    B -->|"No"| C["Continúa con el turno"]
    B -->|"Sí"| D["Desencolar automáticamente"]
    
    D --> E["Turno cancelado"]
```
4. Flujo de llamado médico, ausencia y reencolamiento
```mermaid
flowchart TD
    A["Paciente en espera"] --> B["Médico ejecuta llamarPróxima"]
    B --> C["Estado del paciente: LLAMADO"]
    C --> D{"¿Paciente atiende presencialmente?"}

    D -->|"Sí"| E["Atención médica"]
    D -->|"No"| F["Médico marca AUSENTE"]

    F --> G["Desencolar paciente"]
    G --> H["Iniciar ventana de espera: 1 hora"]
    H --> I{"¿Marca 'estoy atrasado'?"}

    I -->|"No"| J{"¿Marca 'llegué'?"}
    I -->|"Sí"| K["Extender ventana 30 minutos"]

    J -->|"Sí"| L["Volver a encolar con prioridad previa"]
    J -->|"No"| M["Timeout de 1 hora"]
    M --> N["Cancelar turno"]

    K --> O{"¿Marca 'llegué' dentro de los 30 min?"}
    O -->|"Sí"| L
    O -->|"No"| P["Timeout de 30 minutos"]
    P --> Q["Cancelar turno"]

    L --> R["Paciente nuevamente en espera"]
    R --> S["Médico puede ejecutar llamarPróxima nuevamente"]
    S --> C
```
