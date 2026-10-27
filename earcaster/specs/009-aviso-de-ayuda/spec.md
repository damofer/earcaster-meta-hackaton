# 009 · Aviso de ayuda

**Etapa:** 2 · Rutina, agenda y avisos
**Estado:** Implementada (sin la ubicación de R4); falta comprobarla en un teléfono

## Por qué

Si la persona dice «ayuda» o «me caí», alguien de la familia debería enterarse. Es la única función que saca información del teléfono, por eso la activa el cuidador a propósito y la app es clara sobre lo que no puede garantizar.

## Requisitos

- **R1.** El cuidador elige uno o dos contactos y las frases de ayuda («ayuda», «me caí», «socorro»).
- **R2.** Al oír una frase de ayuda la app dice «¿Necesitas ayuda? Voy a avisar a Carlos. Si estás bien, di: estoy bien» y espera 10 segundos; si en ese tiempo se oye «estoy bien», «cancela», «falsa alarma» o un «no» claro (la frase entera), cancela y dice «De acuerdo, no aviso a nadie». «Parar sonido» también cancela. Si no, envía el aviso y dice «Ya avisé a Carlos».
- **R3.** El aviso es un SMS: «Earcaster: María dijo "me caí" a las 17:10. Llama para saber cómo está». El nombre de la persona lo escribe el cuidador; sin nombre dice «Alguien». Si ningún SMS sale en 20 segundos, la app dice «No pude mandar el mensaje. Llamo a Carlos» y llama al primer contacto con el altavoz (spec 008).
- **R4.** Opcional, solo si el cuidador lo activa y da el permiso: la ubicación aproximada en el SMS. **Queda para otra versión:** leer la ubicación desde segundo plano necesita un permiso más delicado; se decide con lo que se aprenda probando el resto.
- **R5.** Al activarlo, la app muestra este aviso y pide aceptarlo: «Earcaster no es un servicio de emergencias. Puede no oír, el teléfono puede cerrarla o no tener señal. Para emergencias use un botón de pánico o llame al número de emergencias».
- **R6.** Nada más sale del teléfono: ni grabación, ni texto de lo que se dijo antes.
- **R7.** Nota de publicación: Google Play limita el permiso de enviar SMS a apps de mensajes. Instalando el APK directamente no hay problema; para publicarla en la tienda habría que cambiar el SMS por una llamada o una notificación.

## Criterios de aceptación

1. **Dado** el aviso activado con Carlos, **cuando** la persona dice «me caí» y no dice nada más, **entonces** a los 10 segundos Carlos recibe el SMS y la app dice «Ya avisé a Carlos».
2. **Dado** el mismo caso, **cuando** la persona dice «estoy bien» antes de 10 segundos, **entonces** no se envía nada.
3. **Dado** un teléfono sin saldo para SMS, **cuando** se dispara el aviso, **entonces** la app llama a Carlos.
4. **Dado** el aviso sin aceptar la advertencia, **cuando** se intenta activar, **entonces** no se activa.
