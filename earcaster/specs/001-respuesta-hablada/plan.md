# 001 · Plan técnico

## Resumen

Se usa el `TextToSpeech` de Android (sin internet si hay voz en español instalada). Una clase nueva decide si la frase suena o habla, y el servicio que escucha la usa en vez de llamar directo al reproductor de sonidos.

## Cambios

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `SpokenText.java` (nuevo) | Cambia `{hora}`, `{dia}`, `{fecha}`, `{año}`, `{saludo}` por su valor en español. Java puro, sin Android, para probarlo con JUnit. | R2, R3 |
| `Speaker.java` (nuevo) | Envuelve `TextToSpeech`: se inicia una vez, elige el español del teléfono, habla con velocidad y volumen, avisa al terminar en el hilo principal. Mismo contrato que `SoundPlayer` (`isPlaying`, `stop`, aviso siempre una sola vez). | R4, R5, R6, R8 |
| `Responder.java` (nuevo) | Responde a una frase: `SoundPlayer` si es Sonido, `Speaker` si es Voz. Si no hay voz, toca el sonido de notificación y avisa. Crea el `Speaker` solo cuando hace falta. | R1, R9 |
| `Trigger.java` | Campos `speak` (boolean) y `speech` (texto). Se guardan en el mismo JSON; si faltan, `speak = false`. Método `withPhrase()` para copiar una frase con otra forma de decirla sin olvidar campos. | R1, R13 |
| `Prefs.java` | `speechRate` (0,5–1,5; por defecto 0,85). | R5 |
| `ListenerService.java` | Usa `Responder` en vez de `SoundPlayer`. `ACTION_SILENCE` y `onDestroy` paran ambos. Las variantes se copian con `withPhrase()`. | R7, R8 |
| `MainActivity.java` | Selector Sonido/Voz, campo de texto, botones de datos y vista previa con `Responder`. Validación al empezar. Etiqueta «Voz: …» en la lista. Control de velocidad en Ajustes. | R10, R11, R12, R5 |
| `sheet_editor.xml` | Selector, grupo de Voz y grupo de Sonido (el de hoy). | R10 |
| `sheet_settings.xml` | Deslizador «Velocidad de la voz». | R5 |
| `AndroidManifest.xml` | `<queries>` para `android.intent.action.TTS_SERVICE`: sin esto, Android 11+ no deja ver el motor de voz. | R4 |
| `app/build.gradle` | JUnit para las pruebas de `SpokenText`; versión 1.7 (código 8). | — |
| `.github/workflows/build.yml` | Corre las pruebas antes de compilar el APK. | — |

## Datos guardados

En `triggers` (JSON), cada frase suma dos claves opcionales:

```json
{"phrase": "qué día es hoy", "speak": true, "speech": "Hoy es {dia} {fecha}", "start_ms": 0, "end_ms": 0}
```

Una frase con Voz conserva su sonido anterior por si el cuidador vuelve a Sonido.

## Detalles de `Speaker`

- `TextToSpeech.OnInitListener` y `UtteranceProgressListener` llegan en otros hilos: todo se pasa al hilo principal.
- Idioma: el del teléfono si es español (es-CO, es-MX…); si no, `es-ES`; si no, `es`. Si `setLanguage` devuelve `LANG_MISSING_DATA` o `LANG_NOT_SUPPORTED`, no hay voz.
- Si se pide hablar antes de que termine de iniciarse, el texto espera y se dice al estar listo.
- `stop()` llama a `tts.stop()` y avisa en seguida; los avisos tardíos del motor (`onStop`, `onDone`) se ignoran porque el identificador ya no coincide.
- Audio con `USAGE_MEDIA` y `CONTENT_TYPE_SPEECH`, el mismo canal que los sonidos.

## Riesgos

- **Teléfonos sin voz en español:** algunos traen solo inglés. Mitigación: R9 (aviso y sonido de reserva) y el enlace a los ajustes de texto a voz.
- **El motor tarda en iniciar** (1–2 s la primera vez): se inicia al empezar a escuchar, no al detectar la frase.
- **Sin SDK de Android en el entorno de desarrollo:** la compilación completa y las pruebas corren en GitHub Actions en cada push.
