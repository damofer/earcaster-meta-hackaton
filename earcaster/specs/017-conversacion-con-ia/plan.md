# 017 · Plan técnico

## Resumen

Claude responde y ElevenLabs pone la voz, los dos llamados desde una **función de Firebase** (Cloud Functions). Las claves viven ahí, en Secret Manager, nunca en la app (R12). Se descartó usar un agente de ElevenLabs con Claude por dentro: para devolver voz necesita recibir el audio del micrófono, y aquí solo sale texto (R3).

Para que la espera sea corta, todo va **en cadena**: en cuanto Claude termina la primera frase, la función la revisa, la manda a ElevenLabs y el audio empieza a llegar al teléfono mientras se genera el resto. Estimación, desde que la persona termina de hablar: 1,5 a 2,5 segundos. Hay que medirlo en un teléfono real.

## Cómo viaja una pregunta

1. Vosk ya transcribe todo el español en el teléfono (sin gramática cerrada). Si el texto coincide con una frase fija, responde esa, como hoy (R1).
2. Si no coincide y empieza con el nombre del asistente («clara, ana viene hoy»), o la opción experimental está activa y suena a pregunta (R2), el teléfono llama a la función `responder` con:
   - el texto sin el nombre;
   - los datos del momento, calculados en el teléfono: la hora, las próximas visitas (014) y las tomas de hoy (007).
3. Si a los 2 segundos no hay audio, suena un tono suave (R9).
4. La función comprueba que el teléfono está vinculado, que la IA está activa para ese paciente y que no pasó el máximo del día (R11). Lee la ficha (016), que nunca está en el teléfono.
5. Le pide la respuesta a Claude (modelo rápido, Haiku) con las reglas fijas (R4) y, como herramientas, solo las acciones permitidas en la ficha (R5).
6. Revisa cada frase: como máximo dos, y si menciona un día o una hora que no está en los datos del momento, la cambia por una frase segura (R4).
7. Cada frase revisada va a ElevenLabs (modelo Flash, el más rápido) y el audio vuelve al teléfono en trozos, que suenan mientras llegan.
8. Si Claude pidió una acción, la función comprueba que esté permitida y la manda al final. El número de teléfono sale de la ficha, nunca de la IA. El teléfono la ejecuta con los frenos de siempre (R5, R6).
9. Si no hay internet, algo falla o pasan 6 segundos sin audio, el teléfono dice la frase de reserva con su propia voz (R8).
10. La función guarda la pregunta, la respuesta y la acción para el cuidador, y se borran solas a los 7 días (R10).

## Servidor (`functions/`, TypeScript)

| Función | Para qué | Quién la llama |
| --- | --- | --- |
| `responder` | Lo de arriba. HTTPS con el token de Firebase del teléfono; responde en trozos: texto, audio PCM, acción y fin. Siempre despierta (una instancia mínima) para evitar el arranque de 1 a 3 segundos. | teléfono del paciente |
| `preview` | La vista previa de la 016: misma respuesta, sin voz y sin ejecutar nada. | cuidador |
| `voices` | Las voces en español de ElevenLabs con su muestra, para elegir (R7): primero la del video, si la cuenta puede usarla, y cuál suena por defecto. | cuidador |

- Secretos: `ANTHROPIC_API_KEY` y `ELEVENLABS_API_KEY`; el modelo de Claude también va en la configuración, para cambiarlo sin publicar la app.
- Región `us-central1`, junto a Firestore (`nam5`).
- Despliegue desde GitHub Actions con una cuenta de servicio solo para desplegar, guardada como secreto de GitHub. Necesita el plan Blaze.

## Datos

| Ruta | Qué guarda | Escribe | Lee |
| --- | --- | --- | --- |
| `config` del paciente (015) | IA activa, nombre del asistente, opción experimental, voz, frase de reserva, máximo diario | el cuidador | el teléfono (va con la copia local) y la función |
| `patients/{pid}` `aiConsentAt` | cuándo aceptó el cuidador qué datos salen (R13) | el cuidador | la función |
| `patients/{pid}/talks/{id}` | pregunta, respuesta, acción, hora y `expireAt` (TTL de 7 días) | la función | el cuidador, que también puede borrar |
| `patients/{pid}/usage/{día}` | respuestas usadas ese día | la función | el cuidador |

## Cambios en la app

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `AiClient.java` (nuevo) | Llama a `responder`, lee los trozos y reproduce el PCM con `AudioTrack` mientras llega; tono a los 2 s, reserva a los 6 s. | R3, R8, R9 |
| `AiText.java` (nuevo) | Sin Android, con pruebas: detectar el nombre del asistente, quitarlo, y leer los trozos de la respuesta. | R2 |
| `ListenerService.java` | Si un resultado final no es una frase fija y es para la IA, pausa el micrófono y llama a `AiClient`; la acción pasa por `Actions` como hoy. | R1, R2, R5, R6 |
| `ProfileActivity` o una pantalla nueva | Activar con el consentimiento, nombre del asistente, voz con muestra, frase de reserva, máximo diario, opción experimental, vista previa y conversaciones de 7 días. | R2, R7, R8, R10, R11, R13 |
| `firebase/firestore.rules` | `talks`: lee y borra el cuidador; `usage`: lee el cuidador; nadie más escribe. | R10, R11 |

## Riesgos

- **Transcripción:** el modelo pequeño de Vosk acierta bien con frases cortas conocidas, pero con habla libre de personas mayores se equivoca más. El cuidador verá lo que se oyó en el registro, para saber si la IA falló o no entendió. Es lo primero que hay que medir.
- **Tiempo de respuesta:** depende de la conexión del teléfono; con datos móviles lentos puede pasar de 3 segundos.
- **Costo:** la IA cuesta poco por respuesta; ElevenLabs cobra por carácter. Con el máximo de 50 respuestas de unos 120 caracteres son unos 180.000 caracteres al mes por paciente; con uso normal, bastante menos. La instancia siempre despierta tiene un costo mensual fijo.
- **Privacidad:** el texto, la ficha y los datos del momento van a Anthropic y a ElevenLabs. El cuidador lo acepta al activar (R13); el audio nunca sale.
