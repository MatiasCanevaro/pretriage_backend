# Reintentos acotados de Gemini REST

`GeminiJsonClient` puede repetir una petición REST a `generateContent` ante errores transitorios de transporte y algunos estados HTTP. `max-attempts=2` significa dos intentos HTTP en total por solicitud Gemini, contando la llamada original. Los intentos comparten el presupuesto de esa operación configurado con `pretriage.ia.gemini.request-timeout` (por defecto, 45 segundos), reducido cuando el llamador pasa un `maxWait` menor. Cada intento usa el menor valor entre `attempt-timeout` (20 segundos por defecto) y el tiempo que queda.

Por defecto se permiten dos intentos en total (`max-attempts=2`), con una espera inicial de 500 ms y jitter entre la mitad y una vez y media ese valor. Las esperas crecen exponencialmente hasta cinco segundos antes del jitter. No se inicia otro intento si la espera no cabe en el presupuesto. Los valores se pueden configurar con `GEMINI_MAX_ATTEMPTS`, `GEMINI_ATTEMPT_TIMEOUT` y `GEMINI_RETRY_INITIAL_BACKOFF`; el máximo admitido es cinco intentos y el retroceso inicial admite de cero a cinco segundos.

Se reintentan HTTP 408, 500, 502, 503 y 504, además de errores `IOException` de transporte, incluidos los timeouts de `HttpClient`. Errores TLS, de handshake o validación de certificados no se reintentan, incluso si están anidados en la causa de otra `IOException`. Un 503 usa `Retry-After` cuando contiene segundos o una fecha HTTP válida. Un 429 solo se reintenta cuando trae ese encabezado válido; así, una respuesta de cuota agotada sin una indicación temporal no genera otra llamada. Las fechas vencidas permiten un reintento inmediato. El cliente no reintenta errores de autenticación o petición, respuestas de seguridad, JSON inválido ni respuestas semánticas incompletas.

Una interrupción cancela el flujo y conserva el estado de interrupción del hilo. Los logs de reintento contienen el número de intento, estado técnico, tiempo transcurrido y espera; no incluyen la clave, URL, cuerpo de la respuesta ni texto clínico. El cuerpo de la solicitud se serializa una vez y se reutiliza entre los intentos. Una corrección semántica de nivel superior es otra solicitud Gemini: el orquestador debe pasarle el presupuesto restante mediante `maxWait` para que ambas solicitudes compartan el límite de 45 segundos. Esta política se limita a `generateContent`; no reintenta POST de clientes móviles ni solicitudes de otras integraciones.

La política sigue la recomendación de Gemini para errores que permiten reintento: retroceso exponencial con jitter, errores transitorios seleccionados y máximo de intentos. El plazo total reducido y el tratamiento de 429 con `Retry-After` son límites propios del backend para evitar que las llamadas de triage excedan el tiempo disponible. Referencia: [Gemini API troubleshooting: Retry strategy](https://ai.google.dev/gemini-api/docs/troubleshooting#retry-strategy).

## Verificación

Desde el endurecimiento del chat, la clasificación final y las continuaciones de
texto marcadas relevantes o ambiguas hacen una petición de verificación semántica antes de
publicar o guardar la respuesta. Generación, verificación y eventual corrección
comparten el presupuesto restante de 45 segundos. Las respuestas fijas de alcance
no requieren una segunda verificación. El cierre de voz tiene además una comprobación previa de alcance,
antes de aceptar el cierre y drenar las transcripciones; no escribe datos clínicos.
Véase [alcance y validación semántica](20-chat-hardening.md).

Si el verificador rechaza semánticamente una clasificación final y proporciona un motivo
seguro de la lista cerrada, el orquestador puede incluir ese motivo como orientación en
una sola solicitud de corrección. Si el verificador no proporciona motivo, se usa una
orientación genérica. El sistema no copia texto de rechazo ni citas del proveedor al
prompt correctivo o a los logs; la corrección comparte el mismo presupuesto de 45 segundos.
Un segundo rechazo conserva el chat cerrado en `FALLIDA` para procesamiento manual.

En una continuación de texto, un rechazo semántico de la pregunta conserva solo el
código de motivo permitido y lo convierte en una instrucción fija para una única
corrección. Un motivo ausente o una respuesta repetida usa la orientación genérica
correspondiente; no se arrastra un motivo semántico previo. Si la pregunta corregida
vuelve a fallar la verificación, el turno se rechaza y no se persiste una respuesta del
bot ni se crea una entrada de cola. La generación y su verificación, más esa corrección
y su verificación, comparten el presupuesto de 45 segundos.

Una aclaración necesaria puede repetir la última pregunta si el dato sigue
pendiente y conserva intentos disponibles. Esto no permite repetir preguntas
antiguas ni reinicia los presupuestos de
[cierre por datos resueltos](21-cierre-por-datos-resueltos.md).

El 21 de octubre de 2026 pasaron las **435 pruebas Java** completas con
`python scripts/test_backend_isolated.py`, contra una base temporal aleatoria,
y las **12 pruebas Python** de los helpers E2E. Las regresiones controlan 503 y
timeout recuperados, agotamiento de intentos, presupuesto restante, `Retry-After`,
autenticación/cuota dura, TLS, interrupciones y contenido privado excluido de logs.
`ChatAutomaticRetryTest` combina los servicios reales con HTTP simulado y verifica
que un reintento exitoso guarda un único turno y actualiza la cola una sola vez.
Las pruebas transaccionales comprueban que fallar la clasificación conserva un
chat cerrado en `FALLIDA`, sin resultado inventado.

Los reintentos mantienen la petición original y el indicador de espera de mobile.
La política de cierre por datos añade el origen `CIERRE_SIN_DATOS`: el cliente
actualizado sincroniza GET y muestra una entrevista cerrada sin fingir una
clasificación completada. Para cargar las clases nuevas en el proceso local es
necesario reiniciar el backend.
