# 007 · Plan técnico

## Resumen

Un registro local de tomas (`DoseLog`) y un dato nuevo para la respuesta hablada, `{toma:Nombre|extra}`. La frase que se dice sale de `SpokenText` (Java puro, probado con JUnit); el registro se consulta en el momento de responder.

## Cambios

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `DoseLog.java` (nuevo) | Anotaciones `{nombre, hora, quién}` en `SharedPreferences` propias; al leer se descarta lo de más de 30 días. `lastToday()` compara el nombre completo sin tildes. `knownNames()` junta los nombres de los recordatorios y del registro. | R2, R5 |
| `SpokenText.java` | `{toma:Nombre\|extra}`. `dose()`: «Sí, hoy está anotada a las 9 y 5 de la mañana» o «Hoy todavía no está anotada» (+ extra). Si el registro falla, «no está anotada». `Sources.doseToday()` con implementación por defecto. | R3, R4 |
| `Responder.java` | `sources()` une agenda de visitas y registro de tomas. | R3 |
| `ReminderReceiver`, `ReminderActivity` | «Hecho» anota la toma del recordatorio, con quién = botón. | R1, R2 |
| `MainActivity.java` | Botón «+ toma» en la respuesta hablada. Ajustes › «Registro de tomas»: últimos 7 días y «Anotar una toma» para el cuidador. | R2, R3, R6 |

## Anotación por voz

«Ya me la tomé» es una acción de frase de la spec 008 (`Actions`, acción `dose`), que anota con quién = voz.
