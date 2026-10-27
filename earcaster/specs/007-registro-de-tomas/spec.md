# 007 · Registro de tomas: «¿Ya tomé la pastilla?»

**Etapa:** 2 · Rutina, agenda y avisos
**Estado:** Implementada; falta comprobarla en un teléfono

## Por qué

Olvidar si ya se tomó la medicación lleva a saltarse una dosis o, peor, a tomarla dos veces. La app puede contestar con lo que se anotó, pero solo si la anotación es fiable.

## Requisitos

- **R1.** Cada medicación tiene nombre y horas («Pastilla de la mañana · 9:00»): es un recordatorio (spec 006) con el botón «Hecho» y el campo «Al tocar «Hecho», anotar la toma de: pastilla de la mañana».
- **R2.** Una toma se anota de tres formas, de más a menos fiable: el cuidador en su pantalla, el botón grande «Ya la tomé» del recordatorio, o la persona diciendo «ya me la tomé». Cada anotación guarda quién la hizo y a qué hora.
- **R3.** Nuevo dato para la respuesta hablada: `{toma:Pastilla de la mañana}`, que dice «Sí, hoy está anotada a las 9 y 5 de la mañana» o «Hoy todavía no está anotada». Se dice «anotada» y no «la tomaste» porque la app solo sabe lo que se anotó. El nombre se compara sin tildes ni mayúsculas, pero completo: «pastilla» no es «pastilla de la noche».
- **R4.** **Seguridad:** la app nunca dice «tómala» ni «no la has tomado». Si no hay anotación dice «todavía no está anotada» y, si el cuidador lo escribe junto al dato (`{toma:Pastilla|Pregúntale a Carlos}`), «Pregúntale a Carlos». La decisión de tomar la medicación no la toma la app.
- **R5.** El registro se guarda solo en el teléfono y se borra a los 30 días.
- **R6.** El cuidador ve en su pantalla las tomas de los últimos 7 días.

## Criterios de aceptación

1. **Dado** una toma anotada con el botón a las 9:05, **cuando** la persona pregunta «¿ya tomé la pastilla?» a las 11:00, **entonces** la app dice «Sí, hoy está anotada a las 9 y 5 de la mañana».
2. **Dado** ninguna toma anotada hoy, **cuando** pregunta, **entonces** dice «Hoy todavía no está anotada» y nunca «tómala».
3. **Dado** una toma anotada ayer, **cuando** pregunta hoy, **entonces** cuenta como no anotada hoy.
