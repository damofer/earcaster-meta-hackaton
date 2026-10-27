# 008 · Acciones por voz: llamar, encender la luz y anotar una toma

**Etapa:** 2 · Rutina, agenda y avisos
**Estado:** Implementada; falta comprobarla en un teléfono

## Por qué

Para muchas personas mayores, buscar un contacto y marcar es difícil. Decir «llama a Carlos» o «enciende la luz» les da autonomía sin tocar la pantalla.

## Requisitos

- **R1.** Además de Sonido y Voz, una frase puede responder con una **acción**: llamar a un contacto, encender la linterna, apagarla o anotar una toma («ya me la tomé», spec 007). Las acciones no suenan solas a una hora fija.
- **R2.** **Llamar:** el cuidador elige el contacto con el selector de la agenda (la app guarda solo su nombre y número). La app primero dice «Llamando a Carlos» y luego marca con el altavoz activado. Necesita el permiso de llamadas, que se pide al empezar a escuchar. Si se toca «Parar sonido» mientras dice «Llamando a…», no llama.
- **R3.** Mientras dura la llamada la app no puede usar el micrófono; al colgar vuelve a escuchar sola.
- **R4.** **Linterna:** «enciende la luz» la enciende y dice «Luz encendida»; «apaga la luz» la apaga. Se apaga sola a los 10 minutos para no gastar batería.
- **R5.** Para evitar llamadas por error, la frase de llamar debe tener al menos dos palabras y se exige la precisión alta de Ajustes, sea cual sea la elegida para las demás frases.
- **R6.** **Anotar una toma:** la frase anota la toma con el nombre que escribió el cuidador, como «dicho en voz alta» (la anotación menos fiable, spec 007 R2), y dice «Lo anoto: pastilla de la mañana, a las 9 y 5 de la mañana».
- **R7.** «Escuchar cómo suena» en el editor dice la frase de la acción sin hacerla.

## Criterios de aceptación

1. **Dado** la frase «llama a Carlos» con la acción llamar a Carlos, **cuando** la persona la dice, **entonces** la app dice «Llamando a Carlos» y la llamada sale con altavoz.
2. **Dado** una llamada en curso, **cuando** se cuelga, **entonces** la app vuelve a escuchar sin que nadie toque nada.
3. **Dado** la linterna encendida por voz, **cuando** pasan 10 minutos, **entonces** se apaga sola.
4. **Dado** una conversación en la televisión con la palabra «Carlos», **cuando** no se dice «llama a Carlos» completo, **entonces** no se llama.
