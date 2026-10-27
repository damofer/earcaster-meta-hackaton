# 009 · Plan técnico

## Resumen

El aviso de ayuda es una acción más de la spec 008 (`help`), pero lo maneja el servicio que escucha, porque necesita el reconocimiento de voz durante los 10 segundos para oír «estoy bien». El envío del SMS y su resultado están en `HelpAlert`.

## Cambios

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `Trigger.java` | Acción `help`; usa los mismos `contacts` (uno o dos). | R1 |
| `SpokenText.java` | `helpQuestion()`, `helpMessage()` (SMS con nombre, frase y hora), `names()` («Carlos y Lucía») y `cancelsHelp()`: «estoy bien», «cancela», «falsa alarma» o «no pasa nada» en cualquier momento; «no» solo como frase entera y resultado final. | R2, R3, R6 |
| `HelpAlert.java` (nuevo) | `SmsManager.divideMessage` + `sendMultipartTextMessage` a cada contacto, con un `PendingIntent` por parte. Un receptor propio (no exportado) cuenta los resultados; basta con que salga uno. Sin respuesta en 20 s, falla. | R3 |
| `ListenerService.java` | `askHelp()`: dice la pregunta, reanuda la escucha y espera 10 s. Mientras tanto, los resultados solo se miran para cancelar. `sendHelp()`: SMS; si falla, dice que llama y llama al primero. «Parar sonido» cancela en cualquier momento antes de enviar. | R2, R3 |
| `Prefs.java` | Nombre de la persona y aceptación del aviso. | R3, R5 |
| `MainActivity.java` + `sheet_editor.xml` | Acción «Avisar que necesita ayuda»: aviso de R5 que hay que aceptar (si no, no se elige), uno o dos contactos, nombre de la persona y explicación. Al empezar: sin contactos o sin aceptar, no arranca; pide `SEND_SMS` y `CALL_PHONE`. | R1, R5 |
| `AndroidManifest.xml` | `SEND_SMS` con la nota de publicación. | R7 |

## Riesgos

- **Falsas alarmas por la televisión:** la ventana de 10 segundos y «estoy bien» las frenan; si molestan, la frase de ayuda puede pedir la precisión alta como la de llamar.
- **Un «no» que no era para cancelar:** solo cuenta si es toda la frase.
- **SMS en doble SIM:** se usa la SIM predeterminada para mensajes.
