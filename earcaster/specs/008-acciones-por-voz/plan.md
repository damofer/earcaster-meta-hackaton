# 008 · Plan técnico

## Resumen

Una frase gana un tercer tipo de respuesta, «Una acción». Las acciones viven en `Actions`, que usa el mismo `Responder` del servicio para decir lo que hace. El servicio que escucha llama a `Actions` en vez de a `Responder` cuando la frase es una acción.

## Cambios

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `Trigger.java` | `action` (`call`, `light_on`, `light_off`, `dose`; vacío = sonido o voz) y `contacts` (nombre y número). `isReminder()` es falso para las acciones. JSON opcional. | R1, R2, R6 |
| `Actions.java` (nuevo) | `run()`: llamar con `TelecomManager.placeCall` y `EXTRA_START_CALL_WITH_SPEAKERPHONE` después de decir «Llamando a…»; linterna con `CameraManager.setTorchMode` (cámara trasera con flash) y apagado a los 10 minutos; anotar la toma en `DoseLog`. `stop()` cancela una llamada pendiente. `release()` apaga la linterna que encendió. `preview()` para el editor. | R2, R3, R4, R6, R7 |
| `Responder.java` | `say(texto)`: dice un texto fijo, con el tono de notificación si no hay voz. | R2, R4, R6 |
| `SpokenText.java` | `doseNoted()`: «Lo anoto: …, a las 9 y 5 de la mañana». | R6 |
| `ListenerService.java` | Llama a `Actions` para las acciones. Las frases de llamar exigen 3 parciales seguidos y confianza de 0,94 sea cual sea la precisión de Ajustes. «Parar sonido» cancela la acción. | R1, R5 |
| `MainActivity.java` + `sheet_editor.xml` | Botón «Una acción» y lista de acciones; «Elegir contacto» con el selector de la agenda (`ACTION_PICK` sobre teléfonos, sin permiso de contactos); nombre de la toma; «Escuchar cómo suena» dice la frase sin actuar. Al empezar: falta contacto, frase de una palabra o toma sin nombre bloquean; se pide `CALL_PHONE`. Se oculta «A una hora fija». | R1, R2, R5, R6, R7 |
| `AndroidManifest.xml` | `CALL_PHONE`; telefonía no obligatoria. | R2 |

## Llamadas y micrófono

Durante una llamada, Android le da silencio al micrófono de las demás apps. La escucha sigue abierta y, al colgar, vuelve a oír sola (R3); no hace falta vigilar el estado de la llamada.

## Riesgos

- **Marcadores que ignoran el altavoz:** algunos no respetan `EXTRA_START_CALL_WITH_SPEAKERPHONE`. Se comprueba en el teléfono de prueba.
- **Linterna ocupada:** si otra app usa la cámara, no se puede encender; la app lo dice.
