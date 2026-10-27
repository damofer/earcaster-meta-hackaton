# 006 · Recordatorios a horas fijas

**Etapa:** 2 · Rutina, agenda y avisos
**Estado:** Implementada; falta comprobarla en un teléfono

## Por qué

Las rutinas (desayuno, pastillas, paseo, dormir) orientan a una persona con Alzheimer durante el día. Un recordatorio con la voz de un familiar, a la misma hora todos los días, ayuda sin que nadie tenga que estar presente.

## Requisitos

- **R1.** El cuidador crea recordatorios con: hora, días de la semana y respuesta (Sonido o Voz con datos de la spec 001, por ejemplo «{saludo}, mamá. Son {hora}, hora de desayunar»). Se hace en el mismo editor de las frases, con «A una hora fija»: así un recordatorio puede usar una grabación, la voz o los datos de visitas y tomas. Una frase puede ser solo recordatorio, sin palabras que la activen.
- **R2.** El recordatorio suena a su hora aunque la app no esté escuchando y aunque la pantalla esté apagada, usando alarmas exactas de Android. Nota de publicación: el permiso de alarmas exactas (`USE_EXACT_ALARM`) es para apps de alarmas y recordatorios; instalando el APK no hay problema, y para la Play Store habría que justificarlo.
- **R3.** Si la app está escuchando, deja de escuchar mientras suena el recordatorio y luego sigue.
- **R4.** Un recordatorio puede pedir confirmación: en pantalla aparece un botón grande «Hecho» durante 30 minutos. La confirmación la usa la spec 007.
- **R5.** Si el teléfono se reinicia, los recordatorios se vuelven a programar solos.
- **R6.** Los recordatorios no suenan si el teléfono está en modo silencio (sin vibración), salvo que el cuidador marque «Sonar aunque el teléfono esté en silencio». El botón «Hecho» aparece igual.

## Criterios de aceptación

1. **Dado** un recordatorio de lunes a viernes a las 8:00 con Voz «Buenos días, hora de desayunar», **cuando** son las 8:00 de un martes con la pantalla apagada, **entonces** se oye la frase.
2. **Dado** el teléfono reiniciado a las 7:00, **cuando** son las 8:00, **entonces** el recordatorio suena igual.
3. **Dado** que la app está escuchando, **cuando** suena un recordatorio, **entonces** no reacciona a su propia voz y sigue escuchando al terminar.
