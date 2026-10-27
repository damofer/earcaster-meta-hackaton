# 016 · Plan técnico

## Resumen

La ficha vive en Firestore, en un documento que **solo lee el cuidador**: `patients/{pid}/private/profile`. No viaja al teléfono de la persona, porque allí no hace falta: en la spec 017, la función del servidor la lee al responder. Se edita desde el modo cuidador (spec 015) en una pantalla nueva, **Ficha**, junto a «Frases y ajustes».

La **vista previa** (R3) necesita la IA, que llega con la función de la spec 017. Hasta entonces la pantalla dice que llega con la conversación.

## Datos

`patients/{pid}/private/profile`:

| Campo | Qué guarda |
| --- | --- |
| `name`, `callMe` | cómo se llama y cómo le gusta que la llamen |
| `home` | dónde vive y con quién |
| `family` | familia y personas cercanas, en texto libre |
| `routine` | su rutina del día |
| `likes` | lo que le gusta y lo que la calma |
| `sensitive` | temas delicados y cómo responderlos |
| `free` | campo libre |
| `calls` | contactos que la IA puede llamar: `{name, number, relation}` («Carlos», «+56…», «su hijo») |
| `light` | puede encender o apagar la linterna |
| `doses` | nombres de las tomas que puede anotar (las de sus frases) |
| `help` | puede mandar el aviso de ayuda (con los contactos del aviso de sus frases) |
| `updatedAt` | cuándo se guardó |

Los contactos para llamar van en una lista aparte de `family` porque la IA necesita el número y el parentesco juntos para entender «llama a mi hijo».

**Límite de 4.000 caracteres** (R6): la suma de los textos más el nombre y el parentesco de cada contacto. El número no cuenta.

## Reglas

`patients/{pid}/private/{doc}`: leer y escribir solo el cuidador del paciente. Borrar al paciente o la cuenta borra también la ficha.

## Cambios en la app

| Archivo | Cambio | Requisitos |
| --- | --- | --- |
| `Profile.java` (nuevo) | La ficha como objeto: a mapa de Firestore y de vuelta, cuenta de caracteres. Sin Android, con pruebas JUnit. | R1, R2, R6 |
| `ProfileActivity` (nueva) | Las secciones con su ejemplo, el aviso de R4, el contador, las acciones permitidas (todas apagadas al empezar), **Guardar** y **Borrar la ficha**. Las tomas salen de las frases del paciente. | R1–R6 |
| `Cloud.java` | Leer, guardar y borrar la ficha; borrar `private` al borrar al paciente. | R5 |
| `CaregiverActivity`, `item_patient.xml` | Botón **Ficha** en cada paciente. | — |
| `firebase/firestore.rules` | La regla de `private`. | R5 |

## Riesgos

- **Datos sensibles:** la pantalla pide no poner diagnósticos ni dosis (R4), pero no puede impedirlo. La ficha sale del teléfono solo hacia Firebase, y en la 017 hacia la IA.
- **Contactos repetidos:** un contacto puede estar en el aviso de ayuda y también en «puede llamar»; son permisos distintos y se guardan aparte.
