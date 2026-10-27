# 006 · Plan técnico

## Resumen

Un recordatorio es una frase del editor con «A una hora fija»: reutiliza la respuesta (sonido, voz, grabación después de la voz) y suma hora, días y opciones. Hay **una sola alarma** de Android, la del próximo recordatorio; al sonar se programa la siguiente. El sonido lo hace un servicio en primer plano corto, que pausa la escucha mientras suena.

## Cambios

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `Trigger.java` | `atMinute` (-1 = no es recordatorio), `days` (bits de `Weekly`), `confirm`, `doseName` (spec 007), `evenSilent`. JSON opcional, compatible con la 1.8. | R1, R4, R6 |
| `Reminders.java` (nuevo) | `schedule()`: calcula el próximo con `Weekly.next` y pone una alarma exacta (`setExactAndAllowWhileIdle`); sin permiso, una inexacta. `dueAt()`: los que tocan a esa hora. `showConfirm()`: notificación con «Hecho», a pantalla completa, que caduca a los 30 minutos. | R2, R4 |
| `ReminderReceiver.java` (nuevo) | Al sonar la alarma: programa la siguiente y arranca `ReminderService`. Si Android no deja arrancarlo, muestra al menos la notificación. También recibe «Hecho» desde la notificación. | R2, R4 |
| `BootReceiver.java` (nuevo) | Reprograma al reiniciar, al actualizar la app, al cambiar la hora o la zona horaria y al cambiar el permiso de alarmas. | R5 |
| `ReminderService.java` (nuevo) | Servicio en primer plano de tipo `mediaPlayback`. Muestra «Hecho» de los que lo piden, salta los que no deben sonar en silencio, y hace sonar el resto uno tras otro con `Responder`. Mantiene la CPU despierta con un wake lock de 3 minutos. | R2, R3, R4, R6 |
| `ReminderActivity.java` + `activity_reminder.xml` (nuevos) | Pantalla con el texto grande y un botón «Hecho» de 120 dp; se ve con el teléfono bloqueado y se cierra sola a los 30 minutos. | R4 |
| `ListenerService.java` | `hold(true/false)`: pausa y reanuda la escucha mientras suena un recordatorio, sin perder la pausa de su propia respuesta. | R3 |
| `MainActivity.java` + `sheet_editor.xml` | Sección «A una hora fija»: hora (selector de Material), días, «Hecho», nombre de la toma, sonar en silencio. Pide notificaciones, avisa si faltan las alarmas exactas o la pantalla completa (Android 14). La lista muestra «Recordatorio · 9:00, todos los días». Guardar reprograma la alarma. | R1, R4 |
| `AndroidManifest.xml` | `SCHEDULE_EXACT_ALARM` (hasta Android 12L), `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`; actividad, servicio y receptores. | R2, R4, R5 |

## Datos guardados

```json
{"phrase": "", "speak": true, "speech": "{saludo}, mamá. Son {hora}, hora de la pastilla",
 "at_minute": 540, "days": 127, "confirm": true, "dose_name": "pastilla de la mañana"}
```

## Riesgos

- **Fabricantes que matan las alarmas** (Xiaomi, Huawei): el botón «Evitar que el teléfono la cierre» de Ajustes también ayuda aquí.
- **Pantalla completa en Android 14:** el permiso puede venir negado; la app lo explica y abre el ajuste. Sin él, «Hecho» queda en la notificación.
- **Dos recordatorios a la misma hora:** suenan uno tras otro.
