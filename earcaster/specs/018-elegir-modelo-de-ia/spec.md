# 018 · Elegir el modelo de IA: Claude o Meta

**Etapa:** 4 · Cuidador conectado e IA
**Estado:** En curso

## Por qué

La conversación con IA (017) solo usaba Claude. Para la hackatón de Meta, y para no depender de una sola empresa, el cuidador puede elegir quién escribe las respuestas: Claude (Anthropic) o Muse Spark (Meta). Todo lo demás de la 017 sigue igual.

## Historias

1. Como cuidador, quiero elegir el modelo de IA de cada paciente sin que cambie nada más.
2. Como cuidador, quiero saber a qué empresa van los datos antes de cambiar de modelo.

## Requisitos

- **R1.** En **Asistente**, el cuidador elige el modelo de IA de cada paciente: **Claude** o **Meta**. Viaja con los ajustes del paciente. Si no eligió nada, sigue Claude, como hasta ahora.
- **R2.** Con los dos modelos valen las mismas reglas de la 017: la ficha, las acciones permitidas, la revisión de cada frase antes de que suene (R4), la voz de ElevenLabs, el máximo diario y la frase de reserva.
- **R3.** Con Meta se usa Muse Spark 1.3, nunca el plan Contributor: en ese plan Meta entrena con lo que recibe, y aquí van datos de salud.
- **R4.** Al pasar a Meta, la app explica que los datos irán a Meta en lugar de a Anthropic; si el cuidador cancela, se queda Claude. El aviso de qué sale del teléfono (017 R13) nombra a las dos empresas.
- **R5.** La clave de Meta vive en el servidor, como las demás (017 R12).

## Criterios de aceptación

1. **Dado** un paciente sin modelo elegido, **cuando** pregunta «Clara, ¿viene Ana hoy?», **entonces** responde Claude, igual que antes.
2. **Dado** Meta elegido y guardado, **cuando** pregunta «Clara, ¿viene Ana hoy?» con una visita de Ana el domingo, **entonces** responde Muse Spark y dice que Ana viene el domingo, con la misma voz.
3. **Dado** Meta elegido y llamar a Carlos permitido, **cuando** dice «Clara, llama a mi hijo», **entonces** la app dice «Llamando a Carlos» y llama.
4. **Dado** Claude guardado, **cuando** el cuidador toca **Meta**, **entonces** ve el aviso; con **Cancelar** vuelve a Claude.
5. **Dado** Meta elegido, **cuando** el cuidador usa la vista previa de la ficha, **entonces** la respuesta la escribe Muse Spark.

## Plan

- **Servidor:** con Meta, la función no llama a Meta directamente: llama a `earcaster-muse`, el motor construido durante la hackatón en el repositorio público [damofer/earcaster-meta-hackaton](https://github.com/damofer/earcaster-meta-hackaton) y desplegado como una función aparte en este mismo proyecto de Firebase. El motor recibe solo texto y nombres, nunca números de teléfono; la acción que devuelve vuelve a pasar por `resolve()`, que toma el número de la ficha. Las frases que llegan se revisan otra vez con `checkSentence`.
- **Datos:** `ai_model` (`"claude"` o `"meta"`) en el JSON de ajustes del paciente; cualquier otro valor cuenta como Claude.
- **Secretos:** `ENGINE_TOKEN` en Secret Manager, compartido con el motor; `META_API_KEY` solo lo usa el motor. El despliegue revisa que `ENGINE_TOKEN` exista.
- **App:** dos botones en `AiActivity` y `Prefs.AI_MODEL` en los ajustes que viajan.

## Tareas

- [x] T1. `ai_model` en los ajustes de la función, con pruebas (R1).
- [x] T2. Llamada al motor `earcaster-muse` con las acciones permitidas sin números, y revisión de frases también aquí (R2, R3, R5).
- [x] T3. `ENGINE_TOKEN` en la revisión del despliegue y dos preguntas con Meta en la prueba de la función desplegada.
- [x] T4. Selector en **Asistente**, aviso al pasar a Meta y aviso de datos con las dos empresas (R1, R4).
- [ ] T5. Comprobar los criterios 1 a 5 con la clave de Meta y en un teléfono.

## Preguntas abiertas

- **Tiempo de respuesta:** Muse Spark siempre razona antes de contestar; hay que medir si llega a la primera frase tan rápido como Claude.
