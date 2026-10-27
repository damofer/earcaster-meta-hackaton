# Specs de Earcaster

Earcaster se desarrolla por specs (SDD, desarrollo guiado por especificaciones): cada función nueva se escribe primero como una spec que dice **qué** debe pasar y **cómo se comprueba**, luego un plan técnico y una lista de tareas, y solo entonces el código.

## Cómo se trabaja

1. **Spec** (`spec.md`): para quién es, historias de uso, requisitos numerados (R1, R2…) y criterios de aceptación en forma «Dado / Cuando / Entonces». No habla de clases ni de archivos.
2. **Plan** (`plan.md`): cómo se construye en esta app: clases que cambian, datos que se guardan, riesgos. Se escribe cuando la spec empieza.
3. **Tareas** (`tasks.md`): pasos pequeños que se marcan al terminar. Cada tarea dice qué requisito cubre.
4. **Código**: un commit por spec (o por tarea grande), con el número de spec en el mensaje. La spec se marca como hecha cuando todos sus criterios se comprobaron en un teléfono.

Una spec no cambia en silencio: si al construirla algo resulta distinto, se corrige la spec en el mismo commit.

## Principios (valen para todas las specs)

- **Nada sale del teléfono** salvo lo que el cuidador active a propósito: el aviso de ayuda (spec 009) y, en la etapa 4, la conexión con el cuidador y la conversación con IA (specs 015 y 017). El audio nunca sale.
- **Sin internet para funcionar.** Lo único que se descarga es el modelo de voz, una vez. Lo que usa internet (etapa 4) siempre tiene un respaldo sin internet.
- **La persona no configura nada.** Todo lo prepara el cuidador; la persona solo habla y escucha.
- **Accesible por defecto:** letra grande, contraste alto, botones de 48 dp o más, frases cortas y en español.
- **Dignidad:** las respuestas no corrigen, no infantilizan y no engañan.
- **No es un dispositivo médico ni de emergencia.** Ninguna pantalla ni texto lo promete.
- **Lo que ya funciona no se rompe:** las frases con sonido, la grabación, el recorte y el dictado siguen igual.

## Etapas y specs

| Etapa | Spec | Qué hace | Estado |
| --- | --- | --- | --- |
| 0 · Base | — | Escuchar sin internet, varias frases, grabar y recortar audio, dictar la frase | Hecho |
| 1 · Respuestas familiares | [001](001-respuesta-hablada/spec.md) | Respuesta hablada con la hora, el día o la fecha reales | Por probar en un teléfono |
| 1 · Respuestas familiares | [002](002-varias-respuestas/spec.md) | Varias respuestas por frase, que se turnan | Pendiente |
| 1 · Respuestas familiares | [003](003-plantillas-cuidador/spec.md) | Plantillas para el cuidador y nombre de la persona | Pendiente |
| 1 · Respuestas familiares | [004](004-modo-persona/spec.md) | Modo persona: pantalla simple protegida con PIN | Pendiente |
| 1 · Respuestas familiares | [005](005-entrada-suave/spec.md) | Tono breve antes de responder | Pendiente |
| 2 · Rutina, agenda y avisos | [014](014-agenda-de-visitas/spec.md) | Agenda de visitas: «¿Cuándo viene Ana?» se responde con el calendario | Por probar en un teléfono |
| 2 · Rutina, agenda y avisos | [006](006-recordatorios/spec.md) | Recordatorios a horas fijas | Por probar en un teléfono |
| 2 · Rutina, agenda y avisos | [007](007-registro-de-tomas/spec.md) | Registro de tomas y «¿ya tomé la pastilla?» | Por probar en un teléfono |
| 2 · Rutina, agenda y avisos | [008](008-acciones-por-voz/spec.md) | Llamar a un contacto, encender la linterna y anotar una toma por voz | Por probar en un teléfono |
| 2 · Rutina, agenda y avisos | [009](009-aviso-de-ayuda/spec.md) | Aviso de ayuda por SMS | Por probar en un teléfono |
| 3 · Cuidado compartido | 010 | Estación fija en la sala | Por escribir |
| 3 · Cuidado compartido | 011 | Resumen diario de preguntas | Por escribir |
| 3 · Cuidado compartido | 012 | Exportar e importar la configuración | Por escribir |
| 3 · Cuidado compartido | 013 | Escucha afinada para voces mayores | Por escribir |
| 4 · Cuidador conectado e IA | [015](015-modo-cuidador-y-paciente/spec.md) | Modo cuidador y modo paciente: el cuidador maneja a sus pacientes desde su teléfono | Por probar con dos teléfonos |
| 4 · Cuidador conectado e IA | [016](016-contexto-del-paciente/spec.md) | Contexto del paciente y acciones permitidas | En curso |
| 4 · Cuidador conectado e IA | [017](017-conversacion-con-ia/spec.md) | Conversación con IA y voz natural de ElevenLabs | Desplegada; falta probarla en dos teléfonos |
| 4 · Cuidador conectado e IA | [018](018-choose-ai-model/spec.md) | Choose the AI model: Claude or Meta (Muse Spark) | En curso |
| 4 · Cuidador conectado e IA | [019](019-daily-summary/spec.md) | The caregiver's daily summary (Muse Spark) | En curso |
| 4 · Cuidador conectado e IA | [020](020-family-updates/spec.md) | The family updates the profile by writing | En curso |
| 4 · Cuidador conectado e IA | [021](021-visit-brief/spec.md) | Before the visit: a brief for the visitor | Planned |
| 4 · Cuidador conectado e IA | [022](022-change-alert/spec.md) | Alert when something changes | Planned |
| 4 · Cuidador conectado e IA | [023](023-scam-shield/spec.md) | Scam shield | Draft |

Las specs de etapas futuras quedan como **Borrador** y se revisan al empezar su etapa, con lo aprendido en la anterior. Los números identifican la spec, no el orden: la 014 va primero en la etapa 2 porque responde a la pregunta que más cambia.

### Para pasar de etapa

- **Etapa 1 → 2:** 5 familias la usan 2 semanas; la app responde al menos 8 de cada 10 veces que se hace la pregunta, y el cuidador dice que repite menos.
- **Etapa 2 → 3:** los recordatorios y el aviso de ayuda funcionan un mes sin fallos en teléfonos de 3 marcas distintas.
- **Etapa 4:** puede empezar cuando la 2 funcione en un teléfono; no depende de la 3, aunque la escucha afinada (013) mejora mucho lo que recibe la IA.
- **Etapa 3 → publicar:** revisión del tono de las respuestas con una asociación de familiares de Alzheimer.
