# 005 · Entrada suave

**Etapa:** 1 · Respuestas familiares
**Estado:** Pendiente

## Por qué

Una voz que sale del teléfono de golpe puede asustar a una persona con demencia o hacer que no preste atención a las primeras palabras. Un tono breve y suave antes de hablar avisa de que viene una respuesta.

## Requisitos

- **R1.** En Ajustes, **Tono antes de responder** (activado por defecto en instalaciones nuevas, desactivado al actualizar para no cambiar lo que ya usan).
- **R2.** El tono dura menos de un segundo, es suave y va incluido en la app (no depende de los tonos del teléfono).
- **R3.** Suena antes de cualquier respuesta, de Voz o de Sonido, con el volumen de Ajustes.
- **R4.** Parar durante el tono corta también la respuesta que venía.
- **R5.** El volumen de las grabaciones hechas en la app se normaliza al grabar, para que todas suenen igual de fuerte.

## Criterios de aceptación

1. **Dado** el tono activado, **cuando** se dice una frase, **entonces** suena el tono y, a continuación, la respuesta, sin pausa mayor de medio segundo.
2. **Dado** el tono sonando, **cuando** se toca Parar, **entonces** no suena la respuesta.
3. **Dado** una grabación hecha bajito y otra fuerte, **cuando** se escuchan, **entonces** las dos suenan a un volumen parecido.
4. **Dado** una app actualizada desde la 1.7, **cuando** se abre Ajustes, **entonces** el tono está desactivado.
