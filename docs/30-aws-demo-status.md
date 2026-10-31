# Estado de la demo AWS

Estado verificado el 31/10/2026. La infraestructura del backend está desplegada
en `us-east-1`; la publicación del frontend en Amplify y su DNS todavía están
pendientes.

Al cerrar esta verificación, EC2 quedó **detenida** para evitar cargos de
cómputo mientras se autoriza Amplify. Se comprobó que la Elastic IP permanecía
asociada y que el volumen seguía adjunto. La reserva IPv4 y el almacenamiento
continúan facturándose. El arranque y la verificación HTTPS posterior al cambio
final de DNS quedan para la continuación.

## Componentes desplegados

| Componente | Estado |
| --- | --- |
| CloudFormation `pretriage-demo` | `UPDATE_COMPLETE`, incluida la Elastic IP elegida por el usuario |
| EC2 | `i-02cb7756f580fa419`, `t3a.medium` (2 vCPU, 4 GiB), volumen raíz cifrado gp3 de 24 GiB con retención al eliminar el stack |
| IPv4 pública | Elastic IP administrada por el stack; conserva la dirección al detener e iniciar EC2 |
| Contenedores | PostgreSQL 18 y Spring Boot `healthy`; Caddy en ejecución con HTTPS verificado; servicio systemd `pretriage` habilitado |
| Acceso operativo | SSM verificado Online durante las pruebas; sin acceso SSH configurado |
| S3 | `pretriage-demo-clinicalfilesbucket-45qsffs15mc2`, privado, cifrado SSE-S3 y versionado; la instancia accede mediante su rol |
| ECR | `827641190526.dkr.ecr.us-east-1.amazonaws.com/pretriage-demo-backendimagerepository-92vmgwg0evhc:demo-e153b1f`; digest `sha256:38f3d3209a6f4f04a12dfc65bd3c19586b4071b8bdf34537b59604927d09921b` |
| API | `https://api.pretriage.com.ar/actuator/health` respondió `UP` por HTTPS a través de Cloudflare; SSL/TLS Full (strict) y proxy DNS habilitados |

El usuario eligió una Elastic IP para conservar la dirección pública al detener
y volver a iniciar EC2. Se debe configurar el registro A de Cloudflare una vez
con el valor de `InstancePublicIp` del stack; las paradas normales no requieren
cambiar DNS. La reserva de IPv4 continúa facturándose mientras EC2 está apagada.
El helper mantiene opcionalmente la actualización explícita de Cloudflare; ver
[despliegue del backend](28-aws-demo-deployment.md).

La actualización agregó la dirección y su asociación; el plan validado marcó
`Replacement: False` para EC2 y conservó la AMI y el volumen existentes.
La comprobación pública posterior a esa asociación todavía devolvió `522`;
queda pendiente confirmar el cambio final del registro A a la Elastic IP
indicada en las salidas del stack y repetir la prueba con EC2 encendida.

La imagen del backend fue publicada en el repositorio ECR. La autenticación
temporal de ECR se limpió después de la publicación. El paquete temporal de
configuración y las versiones temporales del certificado subidos a S3 se
eliminaron; no se dejaron secretos de aplicación en S3. La copia local del
runtime está bajo `target/aws-deploy/runtime`, ignorada por Git y restringida al
usuario local. En EC2, el archivo de entorno y la clave privada del certificado
tienen permisos `600`; el certificado público tiene permisos `644`.

## Repositorios

Los cambios de backend y frontend fueron publicados en la rama `desarrollo` de
sus respectivos repositorios. El código del backend desplegado corresponde al commit
`e153b1f4369ac6f60d922a0a6864e9b0c76a49d1`; el frontend, en el commit
`2732268ed9734e02654e2eb8b5e1d9790113dae4`. La imagen ECR desplegada corresponde al commit de backend
`e153b1f`.

Los checks de GitHub Actions del código publicado pasaron en
[backend](https://github.com/MatiasCanevaro/pretriage_backend/actions/runs/41233853414)
y [frontend](https://github.com/MatiasCanevaro/pretriage_frontend_web/actions/runs/41233812125).

## Frontend pendiente

Amplify Hosting todavía no está creado. La conexión con GitHub no pudo autorizarse:
la integración devolvió `403 ResourceNotAccessible` al intentar habilitar los
webhooks. El propietario debe autorizar o instalar la aplicación GitHub de AWS
Amplify para el repositorio original `MatiasCanevaro/pretriage_frontend_web` en
`us-east-1`. Se eligió ese repositorio original, no un fork. No se contactó a
terceras personas.

El registro DNS `app.pretriage.com.ar` no se creó porque todavía no hay un
destino de Amplify. La API quedó configurada y verificada, pero no existe aún una
URL pública de la aplicación frontend.

## Datos y validaciones

No se cargaron usuarios ni datos clínicos. La base contiene únicamente el
catálogo inicial de especialidades generado por el inicio habitual de la
aplicación. La salud del contenedor confirma que el proceso responde; no implica
que los flujos autenticados completos hayan sido validados con usuarios reales.

La plantilla pasó `cfn-lint` y las comprobaciones seleccionadas de `cfn-guard`;
dos patrones estructurales produjeron falsos positivos documentados. CloudTrail
dispone del historial de eventos de 90 días, pero no se configuró un trail pago.
Las comprobaciones públicas por HTTPS pasaron: readiness `200`, Actuator
restringido `401`, ruta de voz con cabeceras Upgrade sin token `401`, ruta SSE
sin token `401`, preflight del origen autorizado `200` y origen desconocido
`403`. Esto verifica alcance y control de acceso; la sesión real de voz con
Gemini y la entrega de SSE autenticada quedan pendientes de la carga de usuarios.

La prueba de IMDSv2 desde el contenedor del backend devolvió el rol esperado,
sin exponer tokens ni credenciales. Una comprobación desde la instancia, sin
credenciales estáticas, confirmó `PutObject`, `GetObject` y `DeleteObject` con
cifrado AES256. Se retiraron el objeto de prueba, sus versiones y marcadores
de borrado; no se tocaron archivos de la aplicación.

## Operación y costos

Consultar el estado y operar la instancia con el script documentado en
[despliegue del backend](28-aws-demo-deployment.md):

```powershell
./scripts/aws-demo.ps1 -Action status -Profile <perfil>
./scripts/aws-demo.ps1 -Action stop -Profile <perfil>
./scripts/aws-demo.ps1 -Action start -Profile <perfil>
```

Al detener EC2 dejan de cobrarse sus horas de cómputo. La Elastic IP, el volumen
EBS retenido, el bucket/versiones S3 y las imágenes ECR permanecen y pueden
generar cargos. Las referencias de esta región son USD 0,0376 por hora de EC2
activa, USD 0,005 por hora de IP reservada (unos USD 3,65 al mes), y USD 1,92
al mes por el volumen de 24 GiB; S3, ECR, transferencia, servicios de IA y Amplify no
están incluidos. Ver el detalle y las fuentes en
[despliegue del backend](28-aws-demo-deployment.md#costos-para-planificar).

El stack conserva el volumen raíz y el bucket está versionado. El helper ofrece
acciones de consulta, inicio y parada; no elimina recursos. Cualquier limpieza
debe revisar expresamente los datos retenidos y las versiones del bucket antes
de borrar recursos.

La configuración de DNS, frontend y Caddy se describe en
[frontend y DNS](29-aws-frontend-and-dns.md).
