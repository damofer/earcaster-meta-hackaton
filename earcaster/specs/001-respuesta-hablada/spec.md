# 001 · Respuesta hablada con datos del momento

**Etapa:** 1 · Respuestas familiares
**Estado:** Implementada; falta comprobarla en un teléfono

## Por qué

Hoy cada frase responde con un sonido. Para contestar «¿qué día es hoy?» el cuidador tendría que grabar un audio nuevo cada día. Con una respuesta hablada, el cuidador escribe una vez «Hoy es {dia} {fecha}» y la app lo dice en voz alta con el día real, siempre.

## Para quién

- **Cuidador:** escribe la respuesta en vez de grabarla, y usa datos que cambian solos.
- **Persona mayor:** oye una respuesta clara, lenta y siempre correcta.

## Historias

1. Como cuidador, quiero que una frase responda leyendo un texto en voz alta, para no tener que grabar cada respuesta.
2. Como cuidador, quiero poner en el texto la hora, el día o la fecha, para que la respuesta sea correcta cualquier día.
3. Como persona mayor, quiero que la voz hable despacio, para entenderla a la primera.
4. Como cuidador, quiero escuchar cómo sonará antes de dejarla puesta.

## Requisitos

- **R1.** Cada frase responde de una de dos formas: **Sonido** (como hoy) o **Voz** (un texto leído en voz alta). Por defecto, Sonido.
- **R2.** El texto de Voz admite estos datos, que se cambian en el momento de responder:

  | Dato | Ejemplo a las 17:10 del lunes 19 de octubre de 2026 |
  | --- | --- |
  | `{hora}` | las 5 y 10 de la tarde |
  | `{dia}` | lunes |
  | `{fecha}` | 19 de octubre |
  | `{año}` | 2026 |
  | `{saludo}` | Buenas tardes |

  Se aceptan con o sin tilde y en mayúsculas o minúsculas (`{Día}`, `{ano}`). Un dato desconocido se deja tal cual, para que el cuidador lo vea al probar.
- **R3.** La hora se dice como la diría una persona: «la 1 y 5», «las 3 y cuarto», «las 8 y media», «las 12 en punto del mediodía», «las 10 de la noche».
- **R4.** La voz usa el español del teléfono y funciona sin internet si el teléfono tiene instalada una voz en español.
- **R5.** En Ajustes hay un control **Velocidad de la voz**; por defecto un poco más lenta que lo normal (0,85).
- **R6.** La voz respeta el **Volumen** de Ajustes, igual que los sonidos.
- **R7.** Mientras la app habla, deja de escuchar (para no oírse a sí misma) y vuelve a escuchar al terminar, igual que con los sonidos.
- **R8.** El botón **Parar** de la pantalla y de la notificación también corta la voz.
- **R9.** Si el teléfono no tiene voz en español, la frase suena con el sonido de notificación para que la persona reciba algo, y el editor avisa al cuidador cómo instalar la voz.
- **R10.** En el editor: un selector **Sonido / Voz**. Con Voz se ve un campo «Lo que dirá», botones para insertar cada dato y un botón para escucharla. El recorte solo se ve con Sonido.
- **R11.** En la lista, una frase con Voz muestra «Voz: » y el comienzo del texto.
- **R12.** No se puede empezar a escuchar si una frase con Voz no tiene texto; la app dice cuál.
- **R13.** Las frases guardadas antes de esta versión siguen funcionando igual (Sonido).

## Criterios de aceptación

1. **Dado** la frase «qué día es hoy» con Voz «Hoy es {dia} {fecha}», **cuando** alguien la dice un lunes 19 de octubre, **entonces** la app dice «Hoy es lunes 19 de octubre» y vuelve a escuchar al terminar.
2. **Dado** el texto «Son {hora}», **cuando** se prueba a las 13:05, **entonces** dice «Son la 1 y 5 de la tarde».
3. **Dado** el texto «{saludo}, mamá», **cuando** se prueba a las 9:00, **entonces** dice «Buenos días, mamá».
4. **Dado** que la app está hablando, **cuando** se toca Parar en la notificación, **entonces** la voz se corta en menos de un segundo y la app sigue escuchando.
5. **Dado** un teléfono sin voz en español, **cuando** se toca Escuchar en el editor, **entonces** aparece el aviso para instalarla; y al decir la frase escuchando, suena el sonido de notificación.
6. **Dado** una frase con Voz y el texto vacío, **cuando** se toca Empezar a escuchar, **entonces** la app no empieza y dice qué frase no tiene texto.
7. **Dado** una frase con sonido guardada en la versión 1.6, **cuando** se actualiza la app, **entonces** sigue sonando con su sonido y su recorte.

## Fuera de esta spec

- Varias respuestas por frase (spec 002).
- `{nombre}` de la persona y plantillas (spec 003).
- Voz clonada de un familiar: no está previsto; la voz real se graba con Sonido.
