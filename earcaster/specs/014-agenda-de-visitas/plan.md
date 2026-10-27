# 014 · Plan técnico

## Resumen

El texto de la respuesta hablada gana un dato con nombre, `{visita:Ana|reserva}`. `SpokenText` sigue siendo Java puro: recibe la agenda como una interfaz (`Sources`) y solo decide cómo se dice la visita. La clase `Agenda` implementa esa interfaz leyendo el calendario del teléfono y las visitas fijas.

## Cambios

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `SpokenText.java` | Datos con nombre y reserva (`{clave:nombre\|reserva}`). Interfaz `Sources` y clase `Visit`. `visit()` arma la frase: hoy, mañana, día de la semana, fecha, en curso, todo el día. `mentions()` compara el nombre como palabras completas, sin tildes. Si la agenda lanza un error, se usa la reserva. | R1, R5, R7, R9 |
| `Weekly.java` (nuevo) | Próxima vez que toca algo semanal (días como bits, minuto del día, duración). Lo usarán también los recordatorios (006). `describe()`: «los domingos», «de lunes a viernes». | R2 |
| `Agenda.java` (nuevo) | `nextVisit()`: visitas fijas + `CalendarContract.Instances` de los próximos 30 días (más un día antes por los eventos de todo el día en UTC). Filtra por título con `mentions()`, solo calendarios visibles o los elegidos. Pasa los eventos de todo el día a la medianoche local. Devuelve la más próxima. Sin permiso o con error: `null`. | R2, R3, R8, R9 |
| `Prefs.java` | `calendarOn`, `calendarIds` (vacío = todos), `fixedVisits` (JSON). | R2, R4 |
| `Trigger.java` | `thenSound`: después de la voz suena el sonido de la frase. JSON `then_sound`, opcional. | R6 |
| `Responder.java` | Pasa la `Agenda` a `SpokenText.fill`. Con `thenSound`, encadena voz y sonido; si no hay voz, suena el sonido elegido. Un contador de turnos evita que una voz parada dé paso al sonido. | R6, R8 |
| `MainActivity.java` | Botón «+ visita» (diálogo con nombre y reserva). Interruptor «Después, que suene una grabación» que muestra el grupo de sonido. Ajustes › «Agenda de visitas»: calendario (con explicación antes del permiso), elegir calendarios, visitas fijas con días y hora. | R4, R5, R6 |
| `sheet_visits.xml`, `dialog_fixed_visit.xml`, `dialog_named_field.xml`, `view_days.xml`, `item_row.xml` (nuevos) | Pantallas de la agenda. `view_days` y `item_row` se reutilizan en los recordatorios. | R2, R5 |
| `sheet_editor.xml`, `sheet_settings.xml` | Interruptor de grabación después de la voz; botón de la agenda. | R6 |
| `AndroidManifest.xml` | `READ_CALENDAR`. | R3, R4 |

## Datos guardados

```json
{"phrase": "cuándo viene ana", "speak": true, "speech": "{visita:Ana|Ana te llama pronto}",
 "then_sound": true, "sound_uri": "file:///…/grabacion-1.m4a", "start_ms": 0, "end_ms": 0}
```

Visitas fijas en `fixed_visits`: `[{"name": "Ana", "days": 1, "minute": 780}]` (bit 0 = domingo; 780 = 13:00).

## Riesgos

- **El calendario familiar no está en el teléfono de la persona:** hace falta añadir la cuenta de Google y que el calendario esté marcado para sincronizar. La pantalla lo explica si no encuentra calendarios.
- **Nombres repetidos** («Ana» la hija y «Ana» la vecina): se elige qué calendarios leer.
- **Consulta en el hilo principal:** es una consulta pequeña (30 días, unas columnas). Si en un teléfono lento se nota, se pasa a segundo plano.
