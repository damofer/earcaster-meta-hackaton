# 016 · Contexto del paciente

**Etapa:** 4 · Cuidador conectado e IA
**Estado:** En curso

## Por qué

Para que la IA de la spec 017 responda como lo haría alguien que conoce a la persona, necesita saber quién es: cómo le gusta que la llamen, quiénes son sus hijos, qué la calma y de qué no conviene hablar. Eso lo sabe el cuidador, que es también quien decide qué puede hacer la app por su cuenta.

## Historias

1. Como cuidador, quiero contarle a la app quién es mi madre en pocas líneas, sin formularios eternos.
2. Como cuidador, quiero decidir qué puede hacer la app sin preguntarme: a quién puede llamar y qué tomas puede anotar.
3. Como cuidador, quiero que si mi madre pregunta por mi padre, que falleció, la app responda como yo lo haría.

## Requisitos

- **R1.** Cada paciente tiene una ficha con secciones cortas, todas opcionales y cada una con un ejemplo escrito:
  - cómo se llama y cómo le gusta que la llamen;
  - dónde vive y con quién;
  - familia y personas cercanas: nombre, parentesco y, si se quiere, un contacto para llamar;
  - su rutina del día;
  - lo que le gusta y lo que la calma;
  - temas delicados y cómo responderlos («Si pregunta por Jorge, su esposo, que falleció: dile que Jorge la quería mucho y habla de sus nietos»);
  - un campo libre.
- **R2.** **Acciones permitidas:** casillas para lo que la IA puede hacer sin preguntar al cuidador: llamar (y a cuáles contactos de la ficha), encender o apagar la linterna, anotar tomas (cuáles) y mandar el aviso de ayuda. Todas empiezan apagadas.
- **R3.** **Vista previa:** el cuidador escribe una pregunta («¿dónde está Jorge?») y ve qué respondería la IA con esta ficha. No suena en el teléfono de la persona ni se ejecuta ninguna acción.
- **R4.** La ficha no pide diagnósticos ni medicamentos con dosis, y la pantalla lo dice: solo lo que el cuidador quiera contar.
- **R5.** La ficha se guarda en la cuenta del cuidador (spec 015), se puede editar o borrar entera, y solo sale hacia la IA en el momento de responder (spec 017).
- **R6.** Hasta 4.000 caracteres en total, para que la respuesta sea rápida y barata; la pantalla muestra cuántos quedan.

## Criterios de aceptación

1. **Dado** una ficha que dice «le gusta que la llamen Mari», **cuando** ella pregunta a la IA «¿quién soy?», **entonces** la respuesta la llama Mari.
2. **Dado** que llamar a Carlos no está permitido, **cuando** ella dice «llama a Carlos» y ninguna frase fija lo cubre, **entonces** no se hace ninguna llamada.
3. **Dado** el tema delicado de Jorge, **cuando** ella pregunta «¿dónde está Jorge?», **entonces** la respuesta sigue la indicación del cuidador y no dice que murió.
4. **Dado** la vista previa, **cuando** el cuidador prueba una pregunta, **entonces** nada suena en el teléfono de la persona y no se ejecuta ninguna acción.

## Relación con otras specs

- Las plantillas para el cuidador (003) pueden tomar de aquí el nombre de la persona.
