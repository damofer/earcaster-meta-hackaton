# 002 · Varias respuestas por frase

**Etapa:** 1 · Respuestas familiares
**Estado:** Pendiente

## Por qué

Una persona con Alzheimer puede hacer la misma pregunta veinte veces al día. Oír exactamente la misma grabación cada vez suena a máquina. Si la hija graba dos o tres versiones («Mamá, las llaves están en el gancho», «Están colgadas junto a la puerta, mamá»), la app las turna y suena más natural.

## Historias

1. Como cuidador, quiero añadir varias respuestas a una misma frase, grabadas o escritas, para que no suene siempre igual.
2. Como cuidador, quiero escuchar cada respuesta por separado y borrar la que no me guste.
3. Como familiar, quiero grabar mi propia versión en el teléfono de mamá sin borrar la de mi hermano.

## Requisitos

- **R1.** Una frase tiene de 1 a 5 respuestas. Cada respuesta es un Sonido (con su recorte) o una Voz (texto con datos, spec 001).
- **R2.** Al oír la frase, la app usa la siguiente respuesta de la lista y vuelve a la primera al llegar al final (turno fijo, no al azar, para que el cuidador sepa qué sonará).
- **R3.** El turno se recuerda aunque se pare y se vuelva a empezar a escuchar, o se reinicie el teléfono.
- **R4.** En el editor, las respuestas se ven como una lista: cada una con ▶ para escucharla, su nombre o el comienzo del texto, y un botón para borrarla. «Añadir otra respuesta» abre las mismas opciones de hoy (Grabar, Tonos, Archivo, Voz).
- **R5.** Cada respuesta grabada puede llevar quién la grabó («Ana», «Carlos»), opcional.
- **R6.** Las frases con una sola respuesta se ven y funcionan exactamente como en la spec 001.
- **R7.** Borrar una respuesta grabada en la app borra también su archivo.

## Criterios de aceptación

1. **Dado** una frase con tres respuestas A, B y C, **cuando** se dice cuatro veces, **entonces** suenan A, B, C y A.
2. **Dado** que la última que sonó fue B, **cuando** se para la escucha y se vuelve a empezar, **entonces** la próxima es C.
3. **Dado** una frase con una sola respuesta de la versión 1.7, **cuando** se actualiza, **entonces** sigue funcionando igual y en el editor aparece como una lista de una.
4. **Dado** cinco respuestas, **cuando** el cuidador quiere añadir otra, **entonces** la app le dice que el máximo es cinco.
5. **Dado** una respuesta grabada, **cuando** se borra, **entonces** su archivo desaparece de la carpeta de grabaciones.

## Fuera de esta spec

- Elegir respuesta según la hora del día (podría ir en una spec posterior).
