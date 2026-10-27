# Earcaster

App de Android que escucha en segundo plano y reproduce un sonido cuando alguien dice una frase (por ejemplo, «trece»).

- Funciona **sin internet**: usa [Vosk](https://alphacephei.com/vosk), un reconocedor de voz que corre en el teléfono. Nada se graba ni se envía.
- Solo la primera vez descarga el modelo de voz en español (unos 40 MB), o el de inglés si la app está en inglés.
- En español o en inglés: sigue el idioma del teléfono y se puede cambiar en **Ajustes › Idioma**. En inglés la asistente se llama Grace.
- Eliges el sonido entre los tonos del teléfono o cualquier archivo de audio (mp3, ogg, wav…).
- Android 8 o superior.

## Cómo conseguir el APK

### Opción A — GitHub (sin instalar nada)
1. Crea una cuenta en github.com y un repositorio nuevo (puede ser privado).
2. Desde un navegador de computador, entra al repositorio → **Add file → Upload files** y arrastra **todo el contenido** de esta carpeta (incluida la carpeta oculta `.github`). Pulsa **Commit changes**.
3. Ve a la pestaña **Actions**: verás «Compilar APK» en marcha. Tarda unos 5 minutos.
4. Cuando termine, en la página principal del repositorio aparece **Releases** a la derecha. Desde el celular, abre la última y descarga `Earcaster.apk`.

Cada vez que cambies algo y lo subas, GitHub vuelve a compilar y publica un APK nuevo.

Si en **Actions** no aparece nada, es que la carpeta oculta `.github` no se subió (pasa en Mac y Windows). Crea el archivo a mano con **Add file → Create new file**, nombre `.github/workflows/build.yml`, y pega el contenido de ese archivo.

### Opción B — Android Studio
1. Abre esta carpeta con **File → Open**.
2. Conecta el teléfono con la depuración USB activada y pulsa ▶ **Run**.
   O usa **Build → Build APK(s)** y copia el APK al teléfono.

## Instalar
Abre `Earcaster.apk` en el teléfono. Android pedirá permitir «instalar apps de origen desconocido» para el navegador o el administrador de archivos: acéptalo. Si Play Protect avisa que la app es desconocida, elige **Instalar de todas formas** (es normal en apps que no vienen de la Play Store).

## Usar
1. Escribe la frase. Los números van con letras: «trece», no «13».
2. Elige cómo responde:
   - **Un sonido**: graba uno, o elige entre **Tonos** o **Archivo**, y pruébalo con ▶.
   - **Una voz**: escribe lo que dirá y la app lo lee en voz alta. Con **+ hora**, **+ día**, **+ fecha** y **+ saludo** dice el dato del momento: «Hoy es {día} {fecha}» se oye «Hoy es lunes 19 de octubre». Necesita una voz en español instalada en el teléfono (Ajustes › Sistema › Idiomas › Salida de texto a voz).
   - **+ visita** dice la próxima visita de alguien: «{visita:Ana}» se oye «Ana viene el domingo a la 1 en punto de la tarde». Las visitas salen de **Ajustes › Agenda de visitas**: del calendario del teléfono (la familia las apunta en un calendario de Google compartido) o de visitas fijas («Ana · los domingos · 13:00»). Con **Después, que suene una grabación**, tras la voz suena, por ejemplo, Ana diciendo «te quiero mucho».
   - **Una acción**: **llamar a un contacto** con el altavoz (la frase necesita dos palabras, como «llama a Carlos»), **encender o apagar la linterna** (se apaga sola a los 10 minutos) o **anotar una toma** («ya me la tomé»). Primero dice lo que va a hacer.
     Con **avisar que necesita ayuda** («me caí»), pregunta «¿Necesitas ayuda?», espera 10 segundos por si oye «estoy bien» y, si no, manda un SMS a uno o dos contactos; si no sale, llama. Earcaster no es un servicio de emergencias.
   - **+ toma** dice si la toma de hoy está anotada: «Sí, hoy está anotada a las 9 y 5 de la mañana» o «Hoy todavía no está anotada». Nunca dice que la tome.
3. Si quieres que suene también a una hora, sin que nadie pregunte, activa **A una hora fija**: hora, días y, si quieres, un botón grande **Hecho** que anota la toma. Un recordatorio puede no tener frase. Las tomas anotadas se ven en **Ajustes › Registro de tomas**.
4. Pulsa **Empezar a escuchar** y da permiso al micrófono y a las notificaciones.
5. Ya puedes salir de la app o bloquear la pantalla. Para parar, usa **Detener** en la app o en la notificación. Los recordatorios suenan aunque la app no esté escuchando.

## Cuidador en otro teléfono
La primera vez, la app pregunta quién va a usar el teléfono:
- **Soy el cuidador**: entras con tu correo y ves a tus pacientes. Para cada uno tocas **Frases y ajustes** (la misma pantalla de arriba, guardada en la nube) y **Vincular teléfono**, que muestra un código QR y un código de 8 letras y números válido 10 minutos.
- **Es para la persona que cuido**: en su teléfono, lee el QR o escribe el código, elige un PIN y toca **Empezar a escuchar**. Queda una pantalla con la hora, el día y la fecha, sin menús. Lo que cambies en tu teléfono le llega en menos de un minuto; sin internet sigue con lo último que recibió. Para salir, mantén 3 segundos la esquina de arriba a la derecha y escribe el PIN.
- **Usar en un solo teléfono, sin cuenta**: como siempre. Se puede cambiar después en **Ajustes**.

Con **Ficha** cuentas quién es la persona y qué puede hacer la IA sin preguntarte, y pruebas qué respondería. Con **Asistente** activas la conversación con IA: la persona dice el nombre del asistente y su pregunta («Clara, ¿viene Ana hoy?») y le contesta una voz natural; sus frases siguen respondiendo primero, y sin internet. Ahí eliges el nombre, el modelo de IA (Claude o Meta), la frase de reserva y el máximo de respuestas al día, y ves las conversaciones de los últimos 7 días. En la lista ves si su teléfono está escuchando, las tomas anotadas y los avisos de ayuda del último día. Lo que se oye nunca sale del teléfono. Para compilar con la nube hace falta `app/google-services.json` (en GitHub, el secreto `GOOGLE_SERVICES_JSON`) y publicar `firebase/firestore.rules` en el proyecto de Firebase; sin ese archivo la app funciona en un solo teléfono.

## Si deja de sonar
- **Batería**: en Xiaomi, Samsung, Huawei y otros, el sistema puede cerrar apps en segundo plano. Toca **Evitar que el teléfono la cierre** y, si existe, pon la batería de la app en «Sin restricciones».
- **Micrófono ocupado**: mientras hay una llamada, una nota de voz o una grabación, Android no le da el micrófono a esta app. Al terminar, vuelve a escuchar sola; si no, detén y vuelve a empezar.
- **Palabra desconocida**: si la app dice que no conoce una palabra, revisa la tilde o usa otra palabra común.
- **Suena cuando no debe**: frases de una sola palabra corta pueden confundirse con otras parecidas. Con dos o tres palabras acierta más.

## Archivos principales
- `ListenerService.java`: servicio en primer plano que escucha y dispara el sonido.
- `MainActivity.java`: la pantalla (frase, sonido, volumen, empezar/detener).
- `ModelManager.java`: descarga e instala el modelo de voz.
- `SoundPlayer.java`: reproduce el sonido elegido.
- `Responder.java`, `Speaker.java`, `SpokenText.java`: respuesta hablada con la hora, el día o la fecha.
- `Agenda.java`, `Weekly.java`: próxima visita, del calendario del teléfono o de las visitas fijas.
- `Reminders.java`, `ReminderService.java`, `ReminderActivity.java`: recordatorios a horas fijas con botón «Hecho».
- `DoseLog.java`: registro de tomas de los últimos 30 días (con cuidador, también en su teléfono).
- `Actions.java`: acciones por voz (llamar, linterna, anotar una toma).
- `HelpAlert.java`: SMS del aviso de ayuda.
- `WelcomeActivity.java`, `CaregiverActivity.java`, `PatientActivity.java`: quién usa el teléfono, la lista del cuidador y la pantalla de la persona.
- `Cloud.java`, `PatientSync.java`, `LinkCode.java`: Firebase, la copia local en el teléfono de la persona y los códigos para vincular.
- `Profile.java`, `ProfileActivity.java`: la ficha del paciente para la IA, las acciones permitidas y la vista previa.
- `AiText.java`, `AiClient.java`, `AiActivity.java`: cuándo lo que se oye es para la IA, la respuesta con voz que suena mientras llega y los ajustes del asistente.
- `Lang.java`, `LangPicker.java`, `res/values-en/`: el idioma de la app (español o inglés) y sus textos en inglés.
- `functions/`: la función de Firebase que llama a Claude o a Muse Spark de Meta, según el modelo que elija el cuidador, y a ElevenLabs (las claves viven ahí, nunca en la app). Se despliega con el flujo `Función de IA` de GitHub Actions.

## Página web
La landing está en `web/index.html` y se publica en https://earcaster.web.app junto con el último APK (`/Earcaster.apk`). Está en español e inglés: muestra el idioma del navegador (español si el navegador está en español, inglés si no), tiene un selector ES/EN arriba y `?lang=en` o `?lang=es` abre un idioma concreto. Las capturas están en `web/img/`.
Cada push a `main` compila el APK y, si existe el secreto `FIREBASE_SERVICE_ACCOUNT` en el repositorio, publica la web en Firebase Hosting (proyecto `earcaster`, ver `firebase.json` y `.firebaserc`).

## Cómo se desarrolla
Cada función nueva empieza como una spec en [`specs/`](specs/README.md): qué debe pasar y cómo se comprueba, luego el plan y las tareas, y al final el código.
