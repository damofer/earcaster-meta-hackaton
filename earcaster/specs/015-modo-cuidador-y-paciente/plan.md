# 015 · Plan técnico

## Resumen

Firebase con dos piezas desde el principio: **Authentication** (el cuidador entra con correo y contraseña; el teléfono del paciente entra como anónimo) y **Firestore** (configuración, grabaciones, tomas y avisos). Las notificaciones al cuidador con la app cerrada necesitan **Cloud Functions** y **Cloud Messaging**, que piden el plan Blaze; llegan con la spec 017.

La idea central: el teléfono del paciente **copia la configuración de la nube a sus ajustes locales**. Así `ListenerService`, `Reminders`, `Agenda` y `Responder` siguen igual que hoy y funcionan sin internet con la última copia.

Sin `google-services.json` la app compila y funciona como hoy, en modo «un solo teléfono».

## Datos en Firestore

| Ruta | Qué guarda | Escribe | Lee |
| --- | --- | --- | --- |
| `caregivers/{uid}` | nombre y correo | el cuidador | el cuidador |
| `patients/{pid}` | `caregiverUid`, nombre y `config`: frases, visitas fijas, calendario sí/no, velocidad y volumen, nombre de la persona, aviso de ayuda aceptado | el cuidador | el cuidador y los teléfonos vinculados |
| `patients/{pid}/sounds/{sid}` | una grabación `.m4a` como `Blob` (30 s como máximo, unos 480 KB) | el cuidador | el cuidador y los teléfonos vinculados |
| `patients/{pid}/doses/{id}` | nombre, hora y quién la anotó | los dos | los dos |
| `patients/{pid}/events/{id}` | aviso de ayuda: frase oída, hora, si salió el SMS | el teléfono del paciente | el cuidador |
| `patients/{pid}/devices/{uid}` | modelo, si escucha, última señal | el teléfono del paciente | el cuidador |
| `links/{código}` | `pid`, `caregiverUid`, caducidad | el cuidador | quien conoce el código |

## Vincular sin servidor

1. El cuidador crea `links/{código}`: 8 caracteres al azar de un alfabeto sin letras que se confundan (sin 0, O, 1, I), válido 10 minutos. El QR contiene `earcaster:CÓDIGO`.
2. El teléfono del paciente entra como anónimo, lee `links/{código}` y crea `patients/{pid}/devices/{uid}` con el código. Las reglas solo lo dejan si el código existe, apunta a ese paciente y no ha caducado. Después borra `links/{código}` para que nadie más lo use. Cerrar la ventana del código en el teléfono del cuidador no lo borra: sigue valiendo sus 10 minutos, y los caducados se borran solos (TTL de `expiresAt`).
3. Desde ahí las reglas le dejan leer `patients/{pid}` porque su uid está en `devices`.
4. Desvincular: el cuidador borra `devices/{uid}`. El teléfono del paciente lo nota en su escucha de cambios (documento borrado o permiso denegado) y borra su copia.

Son 8 caracteres y no 6 cifras (la spec se corrige): con 6 cifras, alguien podría adivinar un código probando durante esos 10 minutos; con 8 de 32 símbolos hay un billón de combinaciones.

## Reglas de seguridad

`firestore.rules` vive en el repositorio. David las pega en la consola (Firestore › Reglas). Resumen:
- `caregivers/{uid}`: solo su dueño.
- `patients/{pid}`: lo crea quien se pone como `caregiverUid`; lo lee, cambia o borra ese cuidador; lo leen los uid que estén en `devices`.
- `sounds`: escribe el cuidador, leen los dos. `doses`: escriben y leen los dos. `events` y `devices`: escribe el teléfono vinculado (solo su propio `devices/{uid}`), lee el cuidador.
- `links/{código}`: lo crea el cuidador de ese paciente; `get` solo si no caducó; nadie puede listarlos.

## Cambios en la app

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `build.gradle` (raíz y app) | Plugin `google-services` solo si existe `app/google-services.json`. Firebase BoM con `auth` y `firestore`; `play-services-code-scanner` para leer el QR sin pedir la cámara; `zxing:core` para dibujarlo. | — |
| `Cloud.java` (nuevo) | El único que toca Firebase: si está configurado, cuenta del cuidador, pacientes, vincular, escuchar cambios, subir tomas, avisos y señal. | todos |
| `Prefs.java` | Modo del teléfono: `SOLO` (hoy), `CUIDADOR` o `PACIENTE`; paciente vinculado. | R1 |
| `WelcomeActivity` (nueva) | La pregunta de R1, con letra grande. | R1, R10 |
| `CaregiverActivity` (nueva) | Entrar o crear la cuenta; lista de pacientes; añadir, renombrar, quitar; **Vincular teléfono** con el QR y el código. Al tocar un paciente abre el editor. | R2, R3, R4, R9 |
| `MainActivity` | En modo cuidador lee y guarda la configuración del paciente elegido en la nube; las grabaciones nuevas se suben a `sounds`; sin botones de escuchar. | R2 |
| `PatientActivity` (nueva) | Versión básica de la pantalla de la spec 004: hora, día y fecha en grande, «Te estoy escuchando» o un botón grande «Empezar a escuchar», salida con PIN. Antes de vincular: leer el QR o escribir el código, y pedir los permisos (micrófono, notificaciones, calendario, llamadas, SMS). | R4, R5 |
| `PatientSync.java` (nuevo) | En el teléfono del paciente: escucha `patients/{pid}` y `sounds`, escribe la configuración en `Prefs`, baja las grabaciones a archivos y reprograma `Reminders`. Sube tomas, avisos y una señal cada 15 minutos mientras escucha. | R6, R7, R8 |
| `DoseLog.java` | Cada toma se guarda también en `doses`; las del cuidador llegan al registro local. | R7 |
| `ListenerService.java` | Arranca `PatientSync` en modo paciente; al disparar el aviso de ayuda deja un evento. | R6, R7 |

## Riesgos

- **Sonidos que no viajan:** los tonos del teléfono pueden no existir en otro modelo; si falta, suena el tono de notificación. Un archivo elegido de más de 900 KB no se sube y el editor lo dice.
- **Sin notificaciones con la app cerrada** hasta activar Blaze: el cuidador ve tomas, avisos y la última señal al abrir la app; el SMS de ayuda sigue igual.
- **Borrar los datos de la app del paciente** pierde el uid anónimo: hay que vincular de nuevo.
- **Costo:** la cuota gratuita de Firestore (50.000 lecturas y 20.000 escrituras al día) cubre de sobra a una familia; la señal cada 15 minutos son 96 escrituras al día por teléfono.
