# 014 · Agenda de visitas: «¿Cuándo viene Ana?» siempre al día

**Etapa:** 2 · Rutina, agenda y avisos
**Estado:** Implementada; falta comprobarla en un teléfono

## Por qué

Hoy la respuesta a «¿Cuándo viene Ana?» es una grabación fija («Ana viene el domingo»). En cuanto cambia el plan, la grabación miente y alguien tiene que volver a grabarla en el teléfono de la persona. La respuesta tiene que salir de una agenda que la familia actualiza desde sus propios teléfonos, y la app solo la lee en el momento de responder.

## Cómo suena

| Lo que hay en la agenda | La app dice |
| --- | --- |
| Visita de Ana hoy a las 13:00, son las 10:00 | «Ana viene hoy a la 1 en punto de la tarde» |
| Visita de Ana hoy, ya empezó | «Ana está de visita hoy» |
| Visita de Ana mañana a las 17:30 | «Ana viene mañana a las 5 y media de la tarde» |
| Visita de Ana el domingo (dentro de 6 días o menos) | «Ana viene el domingo a la 1 en punto de la tarde» |
| Visita de Ana el 3 de noviembre (más de 6 días) | «Ana viene el 3 de noviembre» |
| Ninguna visita de Ana en los próximos 30 días | La frase de reserva que escribió el cuidador: «Ana te llama pronto, te quiere mucho» |

Después de la parte hablada puede sonar una grabación de Ana («Te quiero mucho, mamá»), que no cambia nunca y por eso no hay que regrabarla.

## Historias

1. Como familiar, quiero apuntar mi próxima visita desde mi propio teléfono, sin tocar el de mi madre, para que ella oiga la fecha correcta.
2. Como cuidador, quiero que la respuesta diga «hoy», «mañana» o el día de la semana, como lo diría yo.
3. Como cuidador, quiero una frase de reserva para cuando no hay visita apuntada, que no deje a mi madre sin respuesta ni le prometa una fecha falsa.
4. Como familiar, quiero que mi voz grabada acompañe a la fecha, para que suene a mí.

## Requisitos

- **R1.** Nuevo dato para la respuesta hablada (spec 001): `{visita:Ana}`. Se cambia por la frase de la tabla de arriba para la próxima visita de Ana.
- **R2.** Fuentes de la agenda, en este orden:
  1. **Calendario del teléfono:** los eventos de los próximos 30 días de los calendarios que el cuidador elija, cuyo título contenga el nombre («Visita de Ana», «Ana viene a comer»). Si la familia comparte un calendario de Google y está sincronizado en el teléfono, cualquiera puede apuntar visitas desde su propio teléfono.
  2. **Visitas fijas escritas en la app:** nombre, días de la semana y hora («Ana · los domingos · 13:00»), para familias sin calendario. Una visita fija cuenta como «en curso» durante dos horas.

  Se miran las dos fuentes y vale la visita más próxima.
- **R3.** La app solo **lee** el calendario del teléfono; no escribe ni envía nada. La sincronización con Google, si existe, es la del propio teléfono.
- **R4.** Permiso de calendario: se pide solo cuando el cuidador activa la fuente «Calendario del teléfono», con una explicación previa. Sin permiso, funcionan las visitas fijas.
- **R5.** El cuidador escribe una **frase de reserva** junto al dato: `{visita:Ana|Ana te llama pronto}`. Si no la escribe, se usa «Ana vendrá pronto». El botón «+ visita» del editor pide el nombre y la reserva y escribe el dato.
- **R6.** Una respuesta puede ser **voz y luego grabación**: primero el texto con `{visita:Ana}`, después un sonido grabado (con su recorte), con el interruptor «Después, que suene una grabación». Si el teléfono no tiene voz, suena igual la grabación.
- **R7.** El nombre se compara sin tildes ni mayúsculas: «Ana», «ana» y «Ána» son la misma persona. Si dos visitas coinciden, cuenta la más próxima.
- **R8.** La agenda se consulta en el momento de responder (no se guarda copia), para que un cambio de última hora valga en seguida.
- **R9.** Nunca inventa una fecha: si la agenda no se puede leer (sin permiso, calendario borrado), usa la frase de reserva.

## Criterios de aceptación

1. **Dado** un evento «Visita de Ana» el domingo 25 de octubre a las 13:00 y hoy lunes 19, **cuando** se dice «¿cuándo viene Ana?», **entonces** la app dice «Ana viene el domingo a la 1 en punto de la tarde».
2. **Dado** que Ana cambia el evento al sábado desde su teléfono y el calendario ya se sincronizó, **cuando** se vuelve a preguntar, **entonces** dice «el sábado», sin tocar Earcaster.
3. **Dado** ningún evento de Ana en 30 días y la reserva «Ana te llama pronto», **cuando** se pregunta, **entonces** dice «Ana te llama pronto».
4. **Dado** el permiso de calendario negado y una visita fija «Ana · domingos · 13:00», **cuando** se pregunta un lunes, **entonces** dice «Ana viene el domingo a la 1 en punto de la tarde».
5. **Dado** la respuesta «{visita:Ana}» seguida de la grabación «Te quiero mucho, mamá», **cuando** se pregunta, **entonces** se oye la fecha y a continuación la voz de Ana.
6. **Dado** la visita de Ana en curso (empezó a las 13:00 y son las 14:00), **cuando** se pregunta, **entonces** dice «Ana está de visita hoy».

## Fuera de esta spec

- Escribir en el calendario desde Earcaster.
- Avisar a la persona cuando la visita está por llegar (eso es un recordatorio, spec 006).
