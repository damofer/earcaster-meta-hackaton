# 017 · Conversación con IA y voz natural

**Etapa:** 4 · Cuidador conectado e IA
**Estado:** En curso

## Por qué

Las frases fijas responden bien a lo que el cuidador previó y con las mismas palabras. Una persona con Alzheimer pregunta de muchas formas y por cosas que nadie anticipó. Una IA con la ficha de la spec 016 puede contestar esas preguntas con calma, y con una voz natural de ElevenLabs suena menos a máquina que la voz del teléfono.

## Historias

1. Como persona cuidada, quiero que me contesten aunque no use las palabras exactas.
2. Como cuidador, quiero que las respuestas sigan lo que escribí en la ficha y nunca inventen una visita o una toma.
3. Como cuidador, quiero ver qué preguntó y qué le respondieron, para corregir la ficha.

## Requisitos

- **R1.** Las frases fijas van primero y no cambian: si lo que se oye coincide con una frase del cuidador, responde esa frase, sin internet. La IA solo atiende lo que no coincide.
- **R2.** **Cuándo responde la IA:** cuando la persona dice el nombre del asistente (lo elige el cuidador, por ejemplo «Clara») seguido de una pregunta o un pedido. Como opción experimental, el cuidador puede hacer que responda a cualquier pregunta que se oiga, sabiendo que puede contestarle a la televisión y que cuesta más.
- **R3.** **Qué sale del teléfono:** el texto de lo que se dijo (lo transcribe Vosk en el teléfono; el audio nunca sale), la ficha (016) y los datos del momento: la hora, la próxima visita (014) y las tomas de hoy (007).
- **R4.** **Cómo responde:** en español, en una o dos frases cortas, con calidez y sin corregir con dureza. Estas reglas no se pueden quitar:
  - nunca dice que es un familiar ni una persona;
  - no da consejos médicos ni diagnósticos;
  - nunca afirma una toma o una visita que no esté en el registro o en la agenda, y nunca dice «tómala» (007);
  - no inventa datos de la familia que no estén en la ficha.
- **R5.** **Acciones:** la IA solo puede pedir las acciones marcadas en la ficha (016 R2). La app las ejecuta con los frenos de siempre: dice antes lo que va a hacer («Llamando a Carlos»), «Parar sonido» lo cancela, y el aviso de ayuda mantiene la pregunta y los 10 segundos para cancelar (009).
- **R6.** Si la persona expresa angustia («me duele el pecho», «no sé dónde estoy») y el aviso de ayuda está permitido, la IA pregunta si quiere que avise a alguien, como en la spec 009.
- **R7.** **Voz:** ElevenLabs, con una voz en español que el cuidador elige de una lista y prueba antes. Si no elige ninguna, suena «Jhenny, suave y tranquila», la voz de Clara en el video de la landing (de la biblioteca de ElevenLabs); si la cuenta de ElevenLabs no puede usarla, suena la primera en español de la cuenta. Por defecto no se clona la voz de ningún familiar. Clonar una voz exige que su dueño la grabe y lo acepte expresamente, y esa voz solo dice las respuestas fijas que ese familiar escribió, nunca lo que genera la IA.
- **R8.** Si no hay internet, la IA falla o tarda más de 6 segundos, la app dice con la voz del teléfono una frase de reserva que elige el cuidador (por defecto «No te entendí bien, ¿me lo dices de otra forma?»).
- **R9.** Si la respuesta tarda más de 2 segundos, suena un tono suave (005) para que la persona sepa que fue oída.
- **R10.** El cuidador ve en su panel las conversaciones de los últimos 7 días (pregunta, respuesta y acción) y puede borrarlas. No se guardan más días.
- **R11.** **Costo:** el cuidador fija un máximo de respuestas al día por paciente (50 por defecto). Al llegar al máximo solo responden las frases fijas y el cuidador recibe un aviso.
- **R12.** Las claves de la IA y de ElevenLabs viven en el servidor (015), nunca dentro de la app.
- **R13.** Se activa por paciente, desde el modo cuidador, después de leer y aceptar qué datos salen del teléfono y hacia qué empresas.

## Criterios de aceptación

1. **Dado** «cuándo viene Ana» como frase fija y una visita de Ana el domingo en la agenda, **cuando** la persona dice «Clara, ¿Ana viene hoy?», **entonces** responde la IA y dice que Ana viene el domingo.
2. **Dado** que Ana no tiene visitas en la agenda, **cuando** pregunta por Ana, **entonces** la IA no da ninguna fecha.
3. **Dado** el teléfono sin internet, **cuando** dice «Clara, ¿quién eres?», **entonces** suena la frase de reserva con la voz del teléfono, y las frases fijas siguen funcionando.
4. **Dado** que llamar a Carlos está permitido, **cuando** dice «Clara, llama a mi hijo», **entonces** la app dice «Llamando a Carlos» y llama; «Parar sonido» antes de la llamada la cancela.
5. **Dado** que se alcanzó el máximo de 50 respuestas, **cuando** pregunta algo que no es una frase fija, **entonces** no se consulta a la IA y el cuidador ve el aviso.
6. **Dado** una conversación de hoy, **cuando** el cuidador abre su panel, **entonces** ve la pregunta y la respuesta como texto, sin audio.

## Decisiones

- **Claude aparte y ElevenLabs solo para la voz** (decidido el 19 de octubre de 2026). Un agente de ElevenLabs con Claude por dentro necesita recibir el audio del micrófono para contestar con voz, y aquí solo sale texto (R3); además, así la función revisa cada frase antes de que suene. Claude con un modelo rápido (Haiku).

## Preguntas abiertas

- **Nombre del asistente o cualquier pregunta:** probarlo con familias; a una persona con Alzheimer le puede costar recordar el nombre.
- **Tiempo de respuesta:** se calculan de 1,5 a 2,5 segundos en total si todo va en cadena; hay que medirlo en un teléfono real.
- **Costo mensual por paciente:** ElevenLabs cobra por carácter y la IA por texto; estimarlo con el uso real de la etapa 2.
