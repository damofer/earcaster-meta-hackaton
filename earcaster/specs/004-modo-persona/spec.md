# 004 · Modo persona

**Etapa:** 1 · Respuestas familiares
**Estado:** Pendiente

## Por qué

La pantalla actual está pensada para quien configura. Una persona con Alzheimer puede tocar «Dejar de escuchar» sin querer, o asustarse con menús que no entiende. El modo persona deja una pantalla tranquila que orienta (hora, día, fecha) y que no se puede desactivar por accidente.

## Historias

1. Como cuidador, quiero dejar el teléfono en un modo que mi familiar no pueda desconfigurar.
2. Como persona mayor, quiero ver en grande qué hora y qué día es.
3. Como cuidador, quiero volver a la configuración con un PIN que solo yo sé.

## Requisitos

- **R1.** En Ajustes, **Modo persona** pide crear un PIN de 4 cifras la primera vez y empieza a escuchar si no lo estaba.
- **R2.** La pantalla del modo persona muestra, en letra de al menos 40 sp: la hora, el día y la fecha; debajo, un indicador grande «Te estoy escuchando» en verde mientras escucha. Sin menús ni botones de configuración.
- **R3.** Mientras responde, la pantalla muestra en grande la frase que oyó y un aviso de que está respondiendo.
- **R4.** Para salir hay que mantener pulsada una esquina 3 segundos y escribir el PIN. Tres PIN errados bloquean el intento 1 minuto.
- **R5.** El botón Atrás no sale del modo persona.
- **R6.** Si el teléfono se reinicia o la app se abre de nuevo, vuelve al modo persona (la escucha la tiene que volver a empezar alguien, porque Android no deja abrir el micrófono solo; la pantalla lo dice con un botón grande «Empezar a escuchar»).
- **R7.** Contraste AA como mínimo, en claro y en oscuro.

## Criterios de aceptación

1. **Dado** el modo persona activo, **cuando** se pulsa Atrás o se toca la pantalla, **entonces** sigue en el modo persona y sigue escuchando.
2. **Dado** el modo persona, **cuando** se mantiene la esquina 3 s y se escribe el PIN correcto, **entonces** vuelve a la pantalla del cuidador.
3. **Dado** tres PIN errados seguidos, **cuando** se intenta otra vez antes de un minuto, **entonces** la app no deja escribirlo.
4. **Dado** las 17:10 del lunes 19 de octubre, **cuando** se mira la pantalla, **entonces** se lee «17:10», «lunes» y «19 de octubre» en letra grande.
5. **Dado** el teléfono reiniciado, **cuando** se abre la app, **entonces** aparece el modo persona con el botón «Empezar a escuchar».

## Fuera de esta spec

- Fondo con foto de la familia y pantalla siempre encendida: spec 010 (estación fija).
