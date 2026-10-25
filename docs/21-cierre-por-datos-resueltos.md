# Cierre de entrevista según datos resueltos

## Objetivo y plan

La entrevista conserva el relato espontáneo y las preguntas sencillas sobre los
síntomas que el paciente mencionó. El cierre deja de depender de ocho mensajes o
cuatro preguntas. El trabajo se organiza en cuatro partes:

1. Reconstruir el progreso por síntoma y dato desde el historial, con una sola
   política compartida entre texto y voz.
2. Asociar cada respuesta a su pregunta, mantener las aclaraciones en contexto y
   separar un turno ambiguo de un hecho clínico confirmado.
3. Aplicar límites independientes de intentos, estancamiento y recursos totales,
   conservando un motivo distinto cuando el cierre es parcial.
4. Verificar las transiciones y la integración con clasificación, cola y voz en
   pruebas aisladas, además de actualizar las instrucciones de Gemini.

No se agregan entidades JPA ni migraciones. El progreso es una proyección del
historial; no genera síntomas, diagnósticos, prioridades ni preguntas clínicas
locales. Gemini continúa redactando las preguntas y la clasificación final.

## Datos y estados

`ProgresoEntrevista` identifica el síntoma destinatario y la dimensión pendiente:
inicio/duración, intensidad del dolor, impacto de otra molestia o temperatura
cuando hay fiebre. Dos síntomas distintos conservan sus propios datos; una
respuesta sobre uno no completa automáticamente al otro.

| Estado | Significado |
| --- | --- |
| `PENDIENTE` | Falta una respuesta que pueda interpretarse para ese dato. |
| `RESPONDIDO` | Hay información comprensible vinculada a ese síntoma y dato. |
| `DESCONOCIDO` | La persona no sabe, no recuerda o no realizó la medición. |
| `RECHAZADO` | La persona prefiere no responder ese dato. |
| `AGOTADO` | Se consumieron los intentos de aclaración sin obtenerlo. |

Los tres últimos estados no equivalen a una respuesta clínica negativa. Un valor
desconocido o agotado no se transforma en ausencia del síntoma, temperatura normal
ni intensidad cero. Una respuesta inválida no se convierte en un valor conocido.
En particular, `38 kg`, `38 cm` o una respuesta afirmativa a «¿te mediste?» no
establecen una temperatura; «8» después de la escala de dolor sí puede establecer
su intensidad, mientras que una cifra acompañada de una unidad o asociación
incoherente requiere aclaración.

La invitación abierta a agregar información se ofrece una sola vez cuando
corresponde. Si aparecen más síntomas, se completan sus datos pertinentes sin
repetir esa invitación. «No tengo otros síntomas» evita explorar síntomas nuevos,
pero no completa por sí solo los datos pendientes de los síntomas ya mencionados.
«No sé» y el rechazo de un dato permiten continuar sin insistir en él.

## Límites de recursos

Los valores centrales de la política son resguardos técnicos, no criterios de
prioridad médica ni una cantidad mínima de respuestas:

| Límite | Valor inicial | Efecto |
| --- | --- | --- |
| Turnos totales del paciente | 24 | Finalizar con la información disponible al agotarse. Incluye turnos excluidos del contexto clínico. |
| Intentos por dato | 3 | Una pregunta inicial y hasta dos aclaraciones; después marcar únicamente ese dato `AGOTADO` y seguir con los restantes. |
| Respuestas consecutivas sin avance | 4 | Detener una conversación estancada sin presentar los datos pendientes como completos. |

Una repetición no resuelve un dato ni reinicia sus intentos. Los intentos de
transporte y corrección contra Gemini no crean nuevos mensajes ni nuevos objetivos;
mantienen sus límites anteriores y comparten los 45 segundos de cada operación.
El cálculo del progreso no necesita una llamada adicional a un modelo.

## Motivos de cierre

- `DATOS_RESUELTOS`: se completaron los pasos de la entrevista; los datos pueden
  estar respondidos, desconocidos o rechazados explícitamente.
- `ALARMA`: se conserva la vía existente de cierre ante una alarma afirmada.
- `SOLICITUD_PACIENTE`: la persona pidió terminar.
- `LIMITE_RECURSOS`: se alcanzó un presupuesto técnico o quedaron datos agotados.
- `SIN_AVANCE`: se agotó el margen de respuestas sin progreso.
- `EN_CURSO`: todavía corresponde continuar o aclarar un dato.

Agotar un único dato no cierra inmediatamente toda la entrevista. El cierre
parcial conserva la distinción entre datos obtenidos y pendientes; si hay hechos
de salud comprensibles, Gemini clasifica únicamente esos hechos con las
validaciones existentes. Si no hay hechos clínicos comprensibles, el cierre no
crea prioridad, resultado clínico ni entrada de cola.

Cerrar la entrevista y completar la clasificación siguen siendo operaciones
distintas. Un fallo del proveedor conserva las respuestas y el estado `FALLIDA`;
el reintento de procesamiento no vuelve a entrevistar ni agrega un mensaje.

## Contexto y persistencia

Los turnos marcados `excluidoDelTriage=true` se usan localmente para medir los
intentos y recursos consumidos, pero nunca como evidencia de síntomas o valores.
Su texto no se reenvía a Gemini. Para continuar, el proveedor recibe el historial
clínico admitido y una instrucción del backend sobre el dato que sigue pendiente,
sin valores ni citas del turno excluido. La clasificación final conserva
exclusivamente la evidencia admitida.

Los resultados nuevos guardan `seguimientoEntrevista` dentro de
`Chat.resultadoTriageJson`, además de `origenClasificacion`. Contiene el motivo de
cierre y los estados/intentos de los datos, sin duplicar valores ni citas. Es
metadata del backend; no se solicita a Gemini ni modifica el DTO clínico. Los
resultados históricos sin esta metadata continúan siendo legibles.

Al reintentar un chat fallido que ya estaba cerrado bajo reglas anteriores, el
procesamiento no vuelve a pedir respuestas. Si la proyección actual no puede
reconstruir un motivo terminal, la metadata usa `CIERRE_PREVIO` y conserva los
datos pendientes. El prompt final siempre pide clasificar la entrevista cerrada;
nunca incorpora instrucciones de continuar preguntando.

Texto y voz usan el mismo progreso. Cambiar de modalidad no reinicia los objetivos
ni sus presupuestos. El cierre de voz conserva la herramienta, validación,
drenaje de transcripciones y procesamiento posteriores existentes. El móvil no
decide el cierre contando mensajes y no requiere un protocolo nuevo.

El reconocimiento local de síntomas y expresiones es acotado. No es una lista de
consultas permitidas: un motivo de salud comprensible fuera de ese léxico conserva
el chequeo semántico y la clasificación de Gemini. Los presupuestos técnicos no
validan médicamente la entrevista ni reemplazan esa comprobación de alcance.

## Verificación

La regresión incluye continuar después de ocho mensajes con datos pendientes;
resolver más de cuatro datos; distinguir síntomas y duraciones; aclarar unidades;
aceptar desconocidos y rechazos; acotar repeticiones; y cerrar sin resultado si no
se obtuvo un motivo comprensible. También conserva las pruebas de alarmas,
negaciones, correcciones de Gemini, procesamiento fallido y transcripciones tardías.

La suite completa se ejecuta con `python -B scripts/test_backend_isolated.py` en
una base temporal, con Docker Compose desactivado. Nunca contra la base de la app.

Verificación del 25 de octubre de 2026:

- Suite backend: 624 pruebas registradas, sin fallos ni errores, siete omitidas.
- Foco de progreso, validador y conversación: 133 pruebas, todas aprobadas.
- Android: `assembleDebug` y `testDebugUnitTest`, 14 pruebas aprobadas; Gradle
  detenido al terminar. El APK de prueba apunta a `http://192.168.100.241:8080`.
- Gemini real, con repositorios y cola simulados y sin Spring ni base de datos:
  un relato sintético supera ocho respuestas, resuelve la medición y contesta la
  invitación en el noveno turno; se guarda `DATOS_RESUELTOS`, la prioridad está en
  rango y la cola se actualiza una sola vez. Se comprueba que los tres valores
  excluidos no aparezcan en las entradas enviadas al proveedor.

La prueba real es opcional y consume solicitudes del proveedor. Con
`GEMINI_API_KEY` en el entorno, puede ejecutarse de forma acotada:

```powershell
./mvnw.cmd "-Dtest=ChatHardeningGeminiSmokeTest#flujoRealCierraTrasMasDeOchoRespuestasYFiltraLosDatosExcluidos" "-Dpretriage.gemini.hardening.live=true" "-Dspring.docker.compose.enabled=false" test
```

Las pruebas de voz usan transporte simulado. Esta validación no incluye una
grabación nueva en un teléfono físico ni una evaluación clínica de prioridades.
