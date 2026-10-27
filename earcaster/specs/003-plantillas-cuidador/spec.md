# 003 · Plantillas para el cuidador

**Etapa:** 1 · Respuestas familiares
**Estado:** Pendiente

## Por qué

Un cuidador cansado no sabe por dónde empezar ni qué formas de preguntar tiene que escribir. Las plantillas traen las preguntas más comunes con sus variantes y una respuesta de ejemplo; el cuidador solo ajusta los detalles de su casa.

## Historias

1. Como cuidador, quiero elegir de una lista las preguntas que repite mi familiar, para no escribirlas desde cero.
2. Como cuidador, quiero que las variantes («¿dónde están mis llaves?», «no encuentro las llaves») ya vengan puestas.
3. Como cuidador, quiero escribir una vez el nombre de mi familiar para que las respuestas lo usen.

## Requisitos

- **R1.** «Nueva frase» ofrece dos caminos: **Desde una plantilla** o **En blanco** (como hoy).
- **R2.** Las plantillas se agrupan en categorías:

  | Categoría | Ejemplos de pregunta | Respuesta de ejemplo |
  | --- | --- | --- |
  | Tiempo | ¿qué día es hoy?, ¿qué hora es? | «Hoy es {dia} {fecha}», «Son {hora}» |
  | Objetos | ¿dónde están mis llaves?, ¿dónde están mis gafas? | «{nombre}, tus llaves están en …» |
  | Personas y visitas | ¿cuándo viene …?, ¿dónde está …? | «… viene el domingo a comer» |
  | Dónde estoy | ¿dónde estoy?, quiero irme a casa | «{nombre}, estás en tu casa, todo está bien» |
  | Calma | tengo miedo, estoy sola | «Tranquila, {nombre}. Ana llega pronto y te quiere mucho» |
  | Rutina | ¿ya comí?, ¿qué hago ahora? | «Ya desayunaste a las 8. Ahora es hora de descansar» |

- **R3.** Al elegir una plantilla se crea la frase con todas sus variantes y la respuesta como Voz; los huecos (…) quedan marcados para que el cuidador los complete antes de guardar. Puede cambiarla a una grabación con su voz.
- **R4.** En Ajustes hay un campo **Nombre de la persona**; el dato `{nombre}` lo dice en las respuestas. Si está vacío, `{nombre}` se omite sin dejar una coma suelta.
- **R5.** Cada variante de la plantilla usa solo palabras que el modelo de voz conoce (se comprueba al escribir las plantillas).
- **R6.** Las respuestas de ejemplo siguen las guías de cuidado: no corrigen, no dicen «ya te lo dije», no prometen cosas falsas.

## Criterios de aceptación

1. **Dado** el nombre «María» en Ajustes, **cuando** se crea la plantilla «¿dónde están mis llaves?» y se completa el hueco con «el gancho de la puerta», **entonces** al decir «no encuentro las llaves» la app dice «María, tus llaves están en el gancho de la puerta».
2. **Dado** el nombre vacío, **cuando** se prueba «{nombre}, estás en tu casa», **entonces** dice «Estás en tu casa».
3. **Dado** una plantilla con un hueco sin completar, **cuando** se toca Listo, **entonces** la app pide completarlo.
4. **Dado** todas las plantillas, **cuando** se revisan contra el vocabulario del modelo, **entonces** ninguna variante tiene palabras desconocidas.
