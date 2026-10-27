# 015 · Modo cuidador y modo paciente

**Etapa:** 4 · Cuidador conectado e IA
**Estado:** Implementada (sin notificaciones con la app cerrada ni entrar con Google); falta comprobarla con dos teléfonos

## Por qué

Hoy el cuidador configura la app en el mismo teléfono que usa la persona. Si vive en otra casa, tiene que ir hasta allá para cambiar una frase o una visita, y de lo que pasa solo se entera por el SMS de ayuda. Con dos modos, el cuidador maneja todo desde su propio teléfono, para una o varias personas, y el teléfono de la persona solo escucha y responde.

## Historias

1. Como cuidador, quiero cambiar las frases, la agenda y los recordatorios de mi madre desde mi teléfono, sin ir a su casa.
2. Como cuidador de varias personas (mis dos padres, o los residentes de un hogar), quiero ver a cada una por separado.
3. Como cuidador, quiero enterarme en mi teléfono cuando se anota una toma o se dispara un aviso de ayuda.
4. Como persona cuidada, no quiero cuentas, contraseñas ni menús.

## Requisitos

- **R1.** La primera vez que se abre, la app pregunta «¿Quién va a usar este teléfono?» con dos botones grandes, **Soy el cuidador** y **Es para la persona que cuido**, y un enlace más pequeño, **Usar en un solo teléfono, sin cuenta**, que deja la app como hoy.
- **R2.** **Modo cuidador:** el cuidador entra con su cuenta (Google o correo) y ve la lista de sus pacientes. Cada paciente tiene sus frases, su agenda de visitas, sus recordatorios, sus contactos, su contexto (spec 016) y sus acciones permitidas. El cuidador puede añadir, editar y quitar pacientes.
- **R3.** Un paciente está ligado a un solo cuidador; un cuidador puede tener varios pacientes.
- **R4.** **Vincular:** en el modo cuidador, **Vincular teléfono** muestra un código QR y un código de 8 letras y números (por si la cámara no sirve), válidos 10 minutos. El teléfono de la persona lo lee una vez y queda vinculado. La persona no crea cuenta ni escribe contraseñas.
- **R5.** **Modo paciente:** es la pantalla de la spec 004 (hora y fecha en grande, sin menús, salida con PIN). Un teléfono vinculado arranca siempre en este modo.
- **R6.** **Sincronización:** lo que el cuidador cambia llega al teléfono de la persona en menos de un minuto si hay internet. Ese teléfono guarda una copia y, sin internet, sigue funcionando con lo último que recibió; los recordatorios se reprograman al recibir cambios.
- **R7.** Del teléfono de la persona al cuidador viajan solo: las tomas anotadas (007), los avisos de ayuda (009; como notificación con la app cerrada cuando se active el plan Blaze, y siempre además del SMS), si la app está escuchando («El teléfono de María dejó de escuchar hace 2 horas») y, si el cuidador activa la spec 017, el texto de las conversaciones con la IA. Nunca audio.
- **R8.** El cuidador puede desvincular un teléfono: ese teléfono borra los datos del paciente y vuelve a la pregunta de R1.
- **R9.** Al crear la cuenta, la app explica qué datos se guardan en la nube y para qué, y pide aceptarlo. El cuidador puede borrar su cuenta con todos los datos de sus pacientes.
- **R10.** Sin cuenta todo sigue como hoy: nada de lo que ya funciona necesita la nube.

## Criterios de aceptación

1. **Dado** el cuidador con María vinculada, **cuando** cambia desde su teléfono la hora de la visita fija de Ana, **entonces** en menos de un minuto «¿cuándo viene Ana?» en el teléfono de María dice la hora nueva.
2. **Dado** el teléfono de María sin internet, **cuando** ella pregunta «¿qué día es hoy?» o suena un recordatorio, **entonces** funciona igual que con internet.
3. **Dado** que María pulsa «Hecho» en un recordatorio, **cuando** hay internet, **entonces** el cuidador ve la toma en su registro.
4. **Dado** un código QR de hace más de 10 minutos, **cuando** se lee, **entonces** no vincula y pide uno nuevo.
5. **Dado** un cuidador con dos pacientes, **cuando** edita las frases de uno, **entonces** las del otro no cambian.
6. **Dado** que el cuidador desvincula el teléfono de María, **cuando** se abre la app en ese teléfono, **entonces** pregunta otra vez quién la usa y no queda ninguna frase de María.

## Decisiones

- **Servidor: Firebase** (decidido el 19 de octubre de 2026): Authentication para las cuentas del cuidador, Firestore con copia local en el teléfono, Cloud Messaging para las notificaciones y Cloud Functions para guardar las claves de la IA de la spec 017. Las funciones necesitan el plan de pago por uso (Blaze), que tiene una cuota gratuita; hasta activarlo, el cuidador ve tomas y avisos al abrir la app.
- **Cuenta del cuidador: correo y contraseña.** Entrar con Google queda para después, porque necesita firmar la app siempre con la misma clave guardada como secreto en GitHub. El teléfono del paciente entra como anónimo.

## Preguntas abiertas

- **Varios cuidadores por paciente** (Carlos y Lucía cuidan a su madre): ¿uno principal que invita a otros, con permiso de solo ver o de editar?
- **Datos de salud:** las tomas y el contexto son datos sensibles; antes de publicar hay que revisar la ley de datos personales del país donde se use.

## Relación con otras specs

- La spec 012 (exportar e importar la configuración) deja de hacer falta si se hace esta.
- El resumen diario (011) puede llegarle al cuidador por esta vía.
