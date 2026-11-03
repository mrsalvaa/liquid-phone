<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Liquid Phone

[English](README.md) | **Español**

App de teléfono para Android con doble SIM, con la misma estética de cristal líquido que el resto de
la suite (Calendar, Clock, Gallery, Calc, Contacts). **Todavía no es un marcador predeterminado:** el
marcador del sistema sigue instalado y es el que atiende las llamadas.

## Privacidad

- Sin red, telemetría, estadísticas, informes de fallos ni Google Play Services. 100 % software libre. No pide el permiso de Internet.
- Sin identificación de llamantes ni listas en la nube, y sin grabación de llamadas.
- Tus datos quedan en los proveedores del sistema (contactos y registro de llamadas). La app no tiene base de datos propia. Sin copias de seguridad ni transferencia.

## Funciones

- Teclado con letras en las teclas y búsqueda mientras escribís, por letras (T9 en español: la ñ va con la 6 y las tildes no cuentan) o por número. Mantener el 0 pone "+"; mantener borrar borra todo.
- Recientes del registro del sistema: agrupa llamadas seguidas del mismo número y del mismo chip, distingue perdidas, entrantes y salientes, y reconoce el contacto en cualquier formato argentino.
- Favoritos y contactos, solo de lectura. "Agregar a contactos" abre la app de contactos que tengas.
- Números formateados según el país por defecto.
- Apariencia compartida con la suite: estilo Liquid o Sólido, tema, fondo, énfasis y 5 íconos más uno monocromo.

## Doble SIM

**Qué es la SIM 1.** Es la ranura lógica 0 del sistema (`SubscriptionInfo.getSimSlotIndex() == 0`), sea física o eSIM, y no la bandeja de plástico. En el Samsung SM-S721B probado, con Android 16, la ranura 1 es la eSIM y la ranura 2 es la física. La documentación de Android avisa que ese índice puede repetirse o ser inválido; en esos casos la app pregunta, nunca adivina.

**Chip principal.** Se elige en Ajustes (SIM 1 o SIM 2, con el nombre y el color de cada chip; por defecto, la SIM 1). Es para los celulares como los Samsung, donde a veces el sistema no deja cambiarlo. Vale solo en esta app: no cambia el chip predeterminado del sistema.

Se guarda la ranura, no el chip. **Si cambiás los chips de lugar, el principal pasa a ser el que quedó en esa ranura.** Si la ranura guardada es inválida o el sistema informa ranuras repetidas, pregunta.

**Chip para llamar con un toque** (recientes y favoritos), en Ajustes:

1. Siempre el chip principal. Es lo predeterminado.
2. Preguntar siempre.
3. La cuenta predeterminada del sistema.
4. El último chip con el que llamaste a ese número, según las llamadas salientes del registro. Sin historial, el chip principal.

**Reglas:**

- Nunca cambia de chip en silencio, porque el otro puede tener otra tarifa. Si el chip principal no está, está desactivado, no tiene servicio o estás en modo avión, confirma cada vez nombrando el otro: "El chip principal no está. ¿Llamar con [otro chip]?".
- En el teclado y en el detalle hay dos botones de llamar: primero y destacado el principal, después el otro. Son una elección explícita y pasan por encima de la política. Si el chip tocado no tiene servicio, ofrece ese mismo (por si hay llamadas por Wi-Fi), el otro o cancelar.
- Con un solo chip no hay selector. Si ese chip es el principal, llama sin preguntar; si no lo es, vale la misma confirmación en cada llamada.
- En recientes cada entrada muestra su chip y se puede filtrar por chip. Una entrada de un chip que ya no está se muestra como tal, sin adivinar. El detalle permite devolver la llamada con el chip original.
- **Emergencias:** siempre `ACTION_DIAL` sin chip, por el flujo estándar del sistema. La app no las reimplementa ni las intercepta.
- **Códigos con `*` y `#`:** también van al marcador del sistema.

El permiso de leer el estado del teléfono no da acceso a ICCID, IMSI, IMEI ni al número de línea, y la app tampoco los pide. Nombre visible, color y ranura es todo lo que usa. El id de cuenta de llamada se trata como opaco: solo se compara, nunca se muestra, ni se guarda, ni se registra.

## Permisos

Se piden en el momento de usarlos, después de explicar para qué. Si negás cualquiera, el resto sigue funcionando.

| Permiso | Para qué | Si lo negás |
|---|---|---|
| `READ_CONTACTS` | Buscar desde el teclado, poner nombres y mostrar favoritos | Se marca sin nombres |
| `READ_CALL_LOG` | Recientes y "el último chip usado" | Sin recientes; esa política usa el chip principal |
| `WRITE_CALL_LOG` | Borrar entradas de recientes; se pide al borrar | No se puede borrar |
| `READ_PHONE_STATE` | Listar los chips y las cuentas de llamada | Sin selector; decide el sistema |
| `CALL_PHONE` | Llamar con el chip elegido | Se abre el marcador del sistema con el número |

Nada más: verificado en el APK de release. El manifest además bloquea `INTERNET`, `ACCESS_NETWORK_STATE`, la ubicación y `READ_PHONE_NUMBERS` aunque una librería los pidiera. El estado de servicio se lee sin ubicación.

Los permisos del registro de llamadas son restringidos en Android. Al instalar un APK a mano, el sistema puede pedirte que permitas los ajustes restringidos de la app.

## Modo de pruebas seguro

Solo en debug y profile (`dev.liquidphone.debug`, "Teléfono (prueba)"). La release no contiene nada de esto (0 coincidencias en su código).

- **Modo seguro, encendido por defecto.** La app solo ve contactos con origen `liquidphone-debug-N` y las llamadas que insertó la herramienta. El filtro va dentro de cada consulta SQL: lo real ni siquiera se lee. Se apaga únicamente a mano, en Ajustes. Apagado, la ventana no se puede capturar (`FLAG_SECURE`).
- **Simulacro de llamadas, encendido por defecto.** Llamar muestra "Se llamaría a [número] con [SIM n]" y no marca. También se apaga solo a mano. Las emergencias no pasan por el simulacro.
- **Datos ficticios.** La herramienta crea 24 contactos y 40 llamadas, y borra solo lo que creó, fila por fila. Mientras existan, un aviso lo recuerda: también se ven en las apps del sistema, y **no conviene hacer llamadas reales** hasta borrarlos, porque Android borra las llamadas más viejas de cada chip a partir de 500. Por eso la herramienta se niega a pasar de 450, y las 500 entradas de la prueba de rendimiento solo existen en memoria.

```bash
P=dev.liquidphone.debug
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_GENERATE -p $P       # crear
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_CLEAR -p $P          # borrar todo
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_STATUS -p $P         # estado, solo cantidades
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_MEMORY -p $P --ei on 1 # 500 filas en memoria
adb logcat -d -s LiquidPhoneDev:I
```

El mismo borrado está en un botón de Ajustes. `tools/safe-capture.sh` saca una captura solo si el modo seguro está encendido y la app en primer plano, y recorta las barras del sistema. Ninguna orden por adb apaga el modo seguro ni el simulacro.

## Limitaciones conocidas

- **No es el marcador predeterminado** ni atiende `tel:` de otras apps. Las llamadas en curso las muestra el marcador del sistema.
- Las emergencias no se pueden elegir con chip y siempre piden tocar llamar en el marcador del sistema.
- El estado de servicio llega un momento después de abrir la app; hasta entonces no se considera que falte servicio.
- Con llamadas por Wi-Fi, un chip puede figurar "sin servicio" y llamar igual: por eso la pregunta ofrece usarlo de todos modos.
- Un contacto con varios números llama desde su detalle, para elegir cuál.
- El código de área por defecto solo existe para Argentina.
- Los favoritos se marcan en la app de contactos: esta no escribe en la agenda.

## Compilar

Hace falta JDK 17 y el SDK de Android (plataforma 35).

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :core:test :app:testDebugUnitTest   # tests
./gradlew :app:lintDebug
./gradlew :app:assembleDebug                  # dev.liquidphone.debug, "Teléfono (prueba)"
./gradlew :app:assembleProfile                # igual, optimizada: rendimiento real
./gradlew :app:assembleRelease
```

## Estructura

```
core/   Lógica sin Android, probada en la JVM: T9, agrupación del registro, política de chip, números, búsqueda, orden y avatares
app/    La app: calls/ (registro y marcado), contacts/ (lectura de la agenda), sim/ (chips), ui/
tools/  Captura segura para las pruebas
```

## Qué copiar a messages

Al copiar, cambiá el paquete. Nada de esto marca ni toca el registro de llamadas.

**Lógica pura (`core/src/main/kotlin/dev/liquidphone/core/`), necesita `libphonenumber`:**

| Archivo | Qué hace |
|---|---|
| `phone/PhoneNumbers.kt` | Normalización y `PhoneMatcher`; sin cambios desde Contacts |
| `text/ContactText.kt` | `Fold`, `SpanishOrder`, `ContactSearch`; sin cambios |
| `avatar/Avatars.kt` | Iniciales y color estable; sin cambios |
| `model/Contact.kt` | Modelos de datos; sin cambios |
| `sim/CallDecider.kt` | `SimChip`, `AccountKey` opaca y `SimCountry`. `CallDecider` solo si messages elige chip con las mismas reglas |

**Sus tests:** `phone/PhoneNumbersTest.kt`, `text/ContactTextTest.kt`, `avatar/AvatarsTest.kt`, `sim/CallDeciderTest.kt`.

**Android (`app/src/main/java/dev/liquidphone/`):**

| Archivo | Qué hace |
|---|---|
| `contacts/SystemContacts.kt` | Solo lectura de la agenda, con filtro por consulta |
| `sim/SystemSims.kt` | Chips activos, cuentas y estado de servicio sin ubicación |
| `ui/components/ContactComponents.kt` | Avatar, fila plana, índice, búsqueda, aviso, botón destructivo |

**Debug (`app/src/debug/java/dev/liquidphone/`):** `DevTools.kt` y `DevReceiver.kt` son el modelo del modo seguro. Hay que reescribir los datos, no el mecanismo.

**No copiar:** `calls/`, `core/calls/`, `core/dial/` y las pantallas, que son propios de esta app.

## Dependencias

Además de AndroidX, Compose y Kotlin, el APK incluye:

| Librería | Versión | Licencia | Red |
|---|---|---|---|
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) (efecto de cristal) | 1.0.0 | Apache-2.0 | Ninguna |
| [libphonenumber](https://github.com/google/libphonenumber) (solo el artefacto base) | 9.0.42 | Apache-2.0 | Ninguna: los datos de los países van dentro del jar |

Solo para los tests, **no van en el APK**: kotlin-test (Apache-2.0) y JUnit 4 (EPL-1.0).

### Auditoría

- **Árbol completo** (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`): 96 artefactos, todos Apache-2.0 según sus POM; `libphonenumber` y `listenablefuture` declaran la licencia en su POM padre. Todos compatibles con GPL-3.0-or-later.
- **Sin Google Play Services, Firebase ni datatransport**, y sin `HttpURLConnection` ni OkHttp: 0 coincidencias en el código del APK de release.
- **Manifest final:** solo los 5 permisos de arriba. Exportados: únicamente los 5 alias de íconos. `tools:node="remove"` quita el permiso interno de androidx.core, `androidx.startup` y el receptor de profileinstaller.
- **F-Droid:** `allowBackup=false`, `dependenciesInfo` apagado, sin perfiles ART en el APK. Compose trae `libandroidx.graphics.path.so` (Apache-2.0, fuente en AOSP) desde Maven; el repo no tiene binarios.

<details>
<summary>Las 96 dependencias de la release</summary>

| Dependencia | Versión | Licencia |
|---|---|---|
| `androidx.activity:activity` | 1.10.1 | Apache-2.0 |
| `androidx.activity:activity-compose` | 1.10.1 | Apache-2.0 |
| `androidx.activity:activity-ktx` | 1.10.1 | Apache-2.0 |
| `androidx.annotation:annotation` | 1.9.1 | Apache-2.0 |
| `androidx.annotation:annotation-experimental` | 1.4.1 | Apache-2.0 |
| `androidx.annotation:annotation-jvm` | 1.9.1 | Apache-2.0 |
| `androidx.arch.core:core-common` | 2.2.0 | Apache-2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 | Apache-2.0 |
| `androidx.autofill:autofill` | 1.0.0 | Apache-2.0 |
| `androidx.collection:collection` | 1.5.0 | Apache-2.0 |
| `androidx.collection:collection-jvm` | 1.5.0 | Apache-2.0 |
| `androidx.collection:collection-ktx` | 1.5.0 | Apache-2.0 |
| `androidx.compose.animation:animation` | 1.9.4 | Apache-2.0 |
| `androidx.compose.animation:animation-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.animation:animation-core` | 1.9.4 | Apache-2.0 |
| `androidx.compose.animation:animation-core-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose:compose-bom` | 2025.10.01 | Apache-2.0 |
| `androidx.compose.foundation:foundation` | 1.9.4 | Apache-2.0 |
| `androidx.compose.foundation:foundation-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout` | 1.9.4 | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.material3:material3` | 1.4.0 | Apache-2.0 |
| `androidx.compose.material3:material3-android` | 1.4.0 | Apache-2.0 |
| `androidx.compose.material:material-icons-core` | 1.7.8 | Apache-2.0 |
| `androidx.compose.material:material-icons-core-android` | 1.7.8 | Apache-2.0 |
| `androidx.compose.material:material-icons-extended` | 1.7.8 | Apache-2.0 |
| `androidx.compose.material:material-icons-extended-android` | 1.7.8 | Apache-2.0 |
| `androidx.compose.material:material-ripple` | 1.9.4 | Apache-2.0 |
| `androidx.compose.material:material-ripple-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.runtime:runtime` | 1.9.4 | Apache-2.0 |
| `androidx.compose.runtime:runtime-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.runtime:runtime-annotation` | 1.9.4 | Apache-2.0 |
| `androidx.compose.runtime:runtime-annotation-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable` | 1.9.4 | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-geometry` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-geometry-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-graphics` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-graphics-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-text` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-text-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-unit` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-unit-android` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-util` | 1.9.4 | Apache-2.0 |
| `androidx.compose.ui:ui-util-android` | 1.9.4 | Apache-2.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 | Apache-2.0 |
| `androidx.core:core` | 1.16.0 | Apache-2.0 |
| `androidx.core:core-ktx` | 1.16.0 | Apache-2.0 |
| `androidx.core:core-viewtree` | 1.0.0 | Apache-2.0 |
| `androidx.customview:customview-poolingcontainer` | 1.0.0 | Apache-2.0 |
| `androidx.emoji2:emoji2` | 1.4.0 | Apache-2.0 |
| `androidx.graphics:graphics-path` | 1.0.1 | Apache-2.0 |
| `androidx.interpolator:interpolator` | 1.0.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-java8` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-process` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.9.4 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android` | 2.9.4 | Apache-2.0 |
| `androidx.profileinstaller:profileinstaller` | 1.4.0 | Apache-2.0 |
| `androidx.savedstate:savedstate` | 1.3.3 | Apache-2.0 |
| `androidx.savedstate:savedstate-android` | 1.3.3 | Apache-2.0 |
| `androidx.savedstate:savedstate-compose` | 1.3.3 | Apache-2.0 |
| `androidx.savedstate:savedstate-compose-android` | 1.3.3 | Apache-2.0 |
| `androidx.savedstate:savedstate-ktx` | 1.3.3 | Apache-2.0 |
| `androidx.startup:startup-runtime` | 1.1.1 | Apache-2.0 |
| `androidx.tracing:tracing` | 1.2.0 | Apache-2.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 | Apache-2.0 |
| `com.googlecode.libphonenumber:libphonenumber` | 9.0.42 | Apache-2.0 |
| `com.google.guava:listenablefuture` | 1.0 | Apache-2.0 |
| `io.github.kyant0:backdrop` | 1.0.0 | Apache-2.0 |
| `org.jetbrains:annotations` | 23.0.0 | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.21 | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-common` | 2.2.21 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.8.1 | Apache-2.0 |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 |

</details>

## Licencia

GPL-3.0-or-later. Ver [LICENSE](LICENSE).
