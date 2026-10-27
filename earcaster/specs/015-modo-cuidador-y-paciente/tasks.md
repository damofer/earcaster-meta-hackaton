# 015 · Tareas

- [x] T1. Gradle: Firebase solo si hay `google-services.json` (en GitHub, el secreto `GOOGLE_SERVICES_JSON`); `Cloud.available()`.
- [x] T2. `firebase/firestore.rules`.
- [x] T3. Modo del teléfono y pantalla de bienvenida (R1, R10): `WelcomeActivity`; desde Ajustes, «Usar con un cuidador en otro teléfono».
- [x] T4. Cuidador: cuenta con correo, lista de pacientes, añadir, renombrar y quitar (R2, R3, R9): `CaregiverActivity`.
- [x] T5. Editor por paciente: la misma pantalla de frases con los ajustes del paciente en archivos aparte; lee y envía la configuración y sube las grabaciones (R2).
- [x] T6. Vincular: código y QR en el cuidador; lector y código escrito en el paciente (R4).
- [x] T7. Teléfono del paciente: pantalla básica de la 004 (`PatientActivity`) y `PatientSync` hacia los ajustes locales; bajar grabaciones (R5, R6).
- [x] T8. Tomas, avisos de ayuda y última señal hacia el cuidador (R7).
- [x] T9. Desvincular y borrar la cuenta (R8, R9).
- [x] T10. Pruebas JUnit de lo que no depende de Android (`LinkCodeTest`, `AgoTest`).
- [ ] T11. Comprobar los 6 criterios con dos teléfonos.

## Pendiente

- Notificaciones con la app cerrada (tomas y avisos): necesitan Cloud Functions y el plan Blaze.
- Entrar con Google: necesita firmar la app siempre con la misma clave.
- El calendario del teléfono de la persona no se puede activar desde el cuidador; desde allí, solo visitas fijas.
