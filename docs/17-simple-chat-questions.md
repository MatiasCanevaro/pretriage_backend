# Entrevista breve y cuantificación de síntomas

La entrevista de texto y voz empieza por el relato espontáneo del paciente. El bot
puede aclarar un motivo que no se entiende y, cuando ya conoce los síntomas que el
paciente enumeró, puede preguntar por su inicio o duración y por cuánto lo afecta.
La cuantificación se limita a esos síntomas: no se pregunta por síntomas nuevos ni
se traslada a uno la duración, intensidad o impacto informado para otro.

El alcance de salud incluye el malestar emocional y la salud mental. Un relato de
angustia, tristeza, ansiedad, una pérdida o una relación puede ser un motivo pertinente
sin que la persona mencione un síntoma físico. En ese caso el bot puede hacer una sola
invitación abierta para entender qué siente o cómo le afecta; no le exige intensidad
del dolor, temperatura ni un cuestionario de síntomas físicos. Una metáfora como
«vacío en el pecho» no se interpreta como dolor torácico o alarma sin afirmación literal.
Si también afirma un síntoma físico, se mantiene la cuantificación pertinente para ese
síntoma. Una emoción negada, por sí sola, no activa la entrevista emocional.

Se permite un máximo de cuatro preguntas de cuantificación en toda la entrevista,
más una única invitación abierta para que el paciente agregue información útil. El
tope general sigue siendo de ocho respuestas del paciente. Cada turno trata un solo
concepto o dato por turno, según las reglas que se indican al modelo. El bot aprovecha
la información espontánea y no vuelve a preguntar lo que
ya quedó respondido. Para dolor puede preguntar intensidad de 0 a 10 o el impacto en
las actividades cotidianas; para fiebre puede pedir la temperatura solo si el
paciente la midió. Si no sabe la cifra o no tiene una medición, ese dato queda como
desconocido y no se insiste. Como el resultado estructurado no tiene campos
específicos para temperatura e impacto, una temperatura medida se conserva con su
unidad en los síntomas u observaciones y el impacto en observaciones.

Una respuesta como «no tengo otros síntomas» detiene la búsqueda de síntomas nuevos,
pero permite completar la cuantificación de los síntomas que el paciente ya nombró,
dentro de los límites anteriores. «No quiero seguir» detiene la entrevista de
inmediato. La invitación abierta se hace como máximo una vez y nunca se repite. Un
relato que ya incluye espontáneamente la cuantificación puede cerrarse sin esa
invitación. Después de completar preguntas de cuantificación, se ofrece una vez si
todavía no se hizo ni el paciente dijo que no tenía más síntomas. Un
signo de alarma claro cierra la entrevista de inmediato para la clasificación urgente
y la validación de prioridad existentes. El tope de respuestas es un límite de
seguridad, no una cantidad mínima.

Una negativa se interpreta según la pregunta anterior. Un «no» a una pregunta de
medición no equivale a una cifra ni a ausencia del síntoma; un «no» a la invitación
indica que no desea agregar información. Ninguna negativa permite inferir que se
descartaron alarmas que no se mencionaron. Los datos no informados permanecen
desconocidos en la clasificación.

## Implementación

- `LenguajeClinicoChat` agrupa las instrucciones de entrevista y comunicación;
  `ValidadorCierreEntrevistaVoz` comparte el criterio de cierre entre texto y voz.
- `LenguajeClinicoChat.REGLAS_PREGUNTA_TEXTO` redacta el próximo turno sin instrucciones
  contradictorias para cerrar la entrevista. El formato no exige exactamente un signo `?` ASCII:
  la puntuación y la presencia o ausencia de signos de interrogación no invalidan ni
  alteran el mensaje que devuelve Gemini.
- Las respuestas intermedias de texto contienen `mensaje` y `estadoEntrada`; Gemini genera
  cada pregunta y el backend decide si continúa o solicita la clasificación final.
- Las entradas ambiguas o ajenas al triage reciben una aclaración o redirección breve
  del backend, quedan excluidas del contexto clínico y no consumen el límite de ocho
  respuestas clínicas. Se conserva su texto original en el historial. Véase
  [alcance y validación semántica](20-chat-hardening.md).
- Una respuesta intermedia incompatible con el contrato o una pregunta repetida admite como máximo
  una corrección remota por turno dentro del presupuesto total de 45 segundos. La
  puntuación por sí sola no provoca error 500 ni una corrección. Se conserva el texto
  de Gemini; no se generan preguntas localmente ni se inventa contenido clínico.
- La política de cierre de voz usa el historial guardado y las transcripciones
  pendientes. Un resumen de voz no puede inventar datos ni ampliar el conjunto de
  síntomas aportados.
- El validador permite cerrar relatos emocionales tras una invitación abierta y su
  respuesta, sin forzar cuantificación física; el preflight semántico conserva la
  validación de alcance antes de aceptar el cierre.
- Las alarmas cierran inmediatamente; el resto de los cierres respeta la invitación
  única, el máximo de cuatro preguntas de cuantificación y el tope de ocho respuestas.
- La clasificación final conserva los datos desconocidos como no informados y aplica
  las validaciones compartidas de estructura y alarmas.

## Verificación

Verificación del 21 de octubre de 2026: **458 pruebas Java** completas pasaron con
`python scripts/test_backend_isolated.py`, contra una base PostgreSQL temporal
aleatoria y con Docker Compose desactivado. La verificación previa de los helpers
E2E fue de **12 pruebas Python** aprobadas; esos scripts no cambiaron en el ajuste
de puntuación. No se ejecutaron pruebas de integración contra la base de la app.

Las regresiones cubren el «no tengo otros síntomas» anterior a la cuantificación,
duración e intensidad/impacto, fiebre negada, temperatura medida o desconocida,
inicios diferentes de dolor y fiebre, rechazo a continuar, límites de preguntas,
alarmas y la conservación de la transcripción y del resultado final. Una pregunta
sobre «desde cuándo te afecta» no cuenta como una respuesta de intensidad. El
resultado de una pregunta solo se considera obtenido si hay un dato válido o una
respuesta desconocida/declinada; preguntar por sí solo no completa la dimensión.

`ChatQuestionFormatTest` reproduce respuestas de Gemini sin `?`, con `??`, con
`？` y con varios interrogantes. Se conserva el mensaje y se registra un solo turno,
sin una llamada correctiva por puntuación. También verifica corrección acotada de
respuestas vacías o incompatibles y preguntas repetidas, sin fallback local ni
duplicar el historial. La continuación usa un prompt propio sin instrucciones de
cierre; la clasificación final conserva sus validaciones.

La comprobación sintética anterior con `ChatService`, `TriageIaClient` y Gemini REST reales,
usando repositorios y cola simulados, obtuvo una pregunta de duración y luego una
escala de intensidad del dolor. La llamada siguiente agotó el tiempo de espera,
por lo que esa ejecución no verificó el cierre completo contra el proveedor. La
suite con proveedores simulados sí cubre el flujo completo y los reintentos. No
se usaron datos de pacientes ni se escribió en la base de la app. Tampoco fue una
prueba del micrófono o de los WebSockets de voz.

Para verificar el ajuste de puntuación, otra comprobación usó esos tres servicios
reales y pidió a Gemini una invitación imperativa sin interrogantes. El backend
aceptó el texto con origen `GEMINI`, mantuvo la entrevista abierta y registró un
solo turno, en aproximadamente 3,4 segundos. El estilo fue forzado únicamente por
la sonda; la respuesta procedió de Gemini, sin reemplazo local. Los repositorios
y la cola estaban simulados y no se escribió en la base de la app. Es una prueba
de integración de componentes, no del endpoint HTTP completo ni de la latencia
habitual del proveedor.

Como ejemplo sintético de la política vigente: si una persona menciona dolor de
cabeza y fiebre y luego dice que no tiene otros síntomas, el bot deja de buscar
síntomas nuevos, pero todavía puede preguntar —sin repetir la invitación abierta—
por duración o impacto de esos síntomas. Si la persona no conoce su temperatura y
no la midió, se conserva como desconocida y no se vuelve a pedir la cifra. No se
incluyen datos de conversaciones reales en esta documentación.
