# Diseño: Liquid Phone dentro de la suite

SPDX-License-Identifier: GPL-3.0-or-later

Qué reutiliza esta app, qué escribió para sí y qué conviene saber antes de hacer messages.

## Origen de lo copiado

Copias de **Liquid Contacts, commit `b39077d`** (es la última versión de esos archivos):

- `ui/glass/` (`Components.kt`, `Controls.kt`, `Dialog.kt`, `Theme.kt`, `UiIcons.kt`). No es igual a Calendar `25ca0d6`: suma `pillVeil()` en `Theme.kt` y su uso en `Controls.kt`. Se copió esta porque es el cristal que quedó como referencia de la suite. Hoy coincide con la de Calendar. Solo cambia el paquete.
- `ui/screens/AppearanceCard.kt`, `ui/screens/Common.kt`, `util/AppIcons.kt`, `util/Strings.kt`, `util/Display.kt`, `data/Stores.kt`, `res/values*/strings_glass.xml`, y los textos de país y código de área.
- `ui/components/ContactComponents.kt`, y en `:core`: `model/Contact.kt`, `phone/PhoneNumbers.kt`, `text/ContactText.kt`, `avatar/Avatars.kt` con sus tests. Sin cambios salvo el paquete.

`contacts/SystemContacts.kt` es solo la parte de lectura de Contacts, más un filtro en cada consulta. Se dejaron afuera las lecturas que esta app no usa (`dataRows`, `resolve`, `loadForEdit`, `loadFull`, `readPhoto`, cuentas y etiquetas). `MainActivity` sigue la estructura de la de Contacts.

Valores del cristal: la tabla de `DESIGN.md` de Liquid Contacts. Rojo destructivo: `DangerButton` con `#9F1818` de Contacts, sin cambios.

## Ajustes

`data/AppSettings.kt`, en `files/settings.json`: los 8 campos de la suite con los mismos nombres y valores (`SettingsFormatTest` lo comprueba), `defaultCountry`, `defaultAreaCode` y `areaCodeNoticeDismissed` de Contacts, y los propios `simPolicy`, `primarySimSlot`, `startTab` y `keypadHaptics`. El modo seguro y el simulacro van aparte, en `files/dev_state.json`, que solo existe en debug.

Íconos: los 5 estilos en el orden de siempre (Liquid por defecto, Material, Samsung, iPhone, Motorola) más monocromo. Los fondos y recipientes son los de Contacts; el dibujo es un tubo de teléfono.

## La elección de chip

`core/sim/CallDecider.kt` decide; `calls/Dialer.kt` ejecuta y es el único lugar que marca. El orden importa:

1. Sin número, nada.
2. **Emergencia:** marcador del sistema. Si la comprobación falla, se trata igual: `ACTION_DIAL` no marca solo. Va antes de permisos, chips y simulacro.
3. Códigos con `*` o `#`: marcador del sistema.
4. Sin `CALL_PHONE`: marcador del sistema. Sin `READ_PHONE_STATE`: decide el sistema.
5. Ranuras repetidas o inválidas, en el sistema o en el ajuste `primarySimSlot`: preguntar.
6. El único chip activo no es el principal: confirmar en cada llamada, nombrándolo.
7. El chip que pide el botón o la política. Si se puede usar, ese y ningún otro. Si no, preguntar; si era el principal, nombrando el otro.

El chip principal es un ajuste de esta app, guardado como ranura (0 o 1), porque el Samsung del usuario no deja cambiar el del sistema. La cuenta no se guarda porque se trata como opaca: el costo es que al cambiar los chips de lugar el principal cambia de significado, y el README lo avisa. Tocar a propósito el botón del otro chip, o que la política apunte a él, no pregunta aunque el principal esté caído: no es un cambio silencioso, se pidió ese chip.

`CallDeciderTest` recorre todas las combinaciones de chips, estados, políticas, pedidos e historial y comprueba que el chip de una llamada sea siempre exactamente el pedido.

## Rendimiento

Build `profile` en el SM-S721B (pantalla de 120 Hz, 8,3 ms por cuadro), con 500 contactos y 500 llamadas en memoria y `dumpsys gfxinfo` del propio paquete. Desplazamientos automáticos de 18 pasadas:

| Pantalla | Fondo | Cuadros lentos | p50 | p90 | p99 |
|---|---|---|---|---|---|
| Recientes | Liso (por defecto) | 1,90 % | 6 ms | 9 ms | 14 ms |
| Contactos | Liso (por defecto) | 1,89 % | 6 ms | 8 ms | 15 ms |
| Recientes | En movimiento | 6,80 % | 9 ms | 17 ms | 22 ms |
| Contactos | En movimiento | 3,59 % | 8 ms | 15 ms | 19 ms |

- Teclado con búsqueda T9 sobre 500 contactos, fondo liso: entre 13 y 19 ms por tecla (muestra chica, 7 teclas).
- Con el fondo en movimiento el teclado dibuja unos 15 cuadros por segundo incluso en reposo, a 23 ms cada uno. Es un costo del fondo animado, que no es el predeterminado.
- Arranque en frío: 215 ms.

## Contraste

Medido sobre capturas en 7 combinaciones de tema, estilo y fondo; es el peor valor de cada texto:

| Texto | Peor contraste |
|---|---|
| Nombre en rojo de una perdida | 5,10:1 |
| Texto secundario (hora) | 5,28:1 |
| Botón de llamar destacado | 4,91:1 |
| Iniciales del avatar | 5,27:1 |

## Excepciones locales de ui/glass

**Pendiente de llevar a Contacts y Calendar.** Toda la `ui/glass` es copia de Contacts salvo esto, en `ui/glass/GlassExceptions.kt`. En `Theme.kt` solo hay dos llamadas a esa capa.

| Valor | Contacts | Esta app | Por qué |
|---|---|---|---|
| Texto sobre color (`readableOn`) | Casi negro `#0B0D12` si la luminancia supera 0,45; si no, blanco | Negro puro si supera 0,179; si no, blanco | 0,179 es donde blanco y negro dan el mismo contraste, así que siempre llega a 4,5:1. Con el valor de Contacts: iniciales de avatar a 2,35:1 y botón destacado a 3,6–4,1:1, en tema claro. |
| Texto secundario, paleta clara | 58 % | 66 % | Hora y datos secundarios: daba 4,39:1. |
| Texto secundario, paleta lisa clara | 58 % | 66 % | Igual. |
| Texto secundario, paleta sólida clara | 55 % | 66 % | Daba 3,97:1. |

Los temas oscuros no cambian de opacidad; `readableOn` vale en todos. Los valores medidos de arriba son con las excepciones activas.

**Para revertir:** `GlassExceptions.ENABLED = false` deja el aspecto exacto de Contacts, y `ReadableOnTest` sigue probando la función de la capa. Para sacarla del todo, borrar el archivo y las dos llamadas de `Theme.kt`.

## Números de los tests

Los tests de `:core` no usan números argentinos que puedan ser de alguien, y `TestNumbersGuardTest` lo controla sobre las fuentes de los tests. Argentina no tiene un rango reservado para ficción como `555-01XX`, así que se construyen:

- **Área 11, con abonado de 8 dígitos que empieza con 1 y nunca con 15:** `011 1345-6789`, `011 15 1345-6789`, `+54 9 11 1345-6789`, `1321-0000`.
- **Por qué con 1:** los servicios especiales usan `1XY`, así que un número local con 1 adelante chocaría con ellos al marcarlo. `15` es el prefijo de móvil.
- **Por qué no con 0:** es el prefijo de acceso, y además `localDigits` quita los ceros iniciales de un número local, por lo que esos tests dejarían de probar lo que deben.
- **Por qué sirve:** `libphonenumber` acepta cualquier abonado en el área 11 (`11\d{8}`), así que la lógica se prueba igual.

**"No asignable" es una inferencia, no una garantía.** No se encontró una regla oficial que prohíba el 1 como primer dígito del abonado. Sale de lo presentado a la UIT ([plan de Argentina](https://www.itu.int/dms_pub/itu-t/oth/02/02/T02020000090001MSWE.docx), [notificación](https://www.itu.int/itudoc/itu-t/number/a/arg/75464_ww9.doc)): el 0 está reservado a los prefijos de acceso y los servicios especiales usan `1XY`. También, de que `libphonenumber` exige de 2 a 8 fuera del área 11. Esos documentos son de 1997 a 1999. **Pendiente de baja prioridad:** contrastarlo con el plan vigente de ENACOM.

**0800 y 0810** usan la misma forma (`0800 133 1234`), con menos respaldo: ahí no hay marcado local que choque.

**Excepciones, con el número sin cambiar:** los del interior (áreas 351, 223 y 2966), listados en `TestNumbersGuardTest`. `libphonenumber` rechaza ahí el abonado con 0 o 1, y estos tests existen para probar áreas de 3 y 4 dígitos, así que deben ser válidos para la biblioteca: un número válido ahí no se puede garantizar como no asignable. Solo viven en los tests; nunca llegan al celu ni al sistema.

**Otros países, con rangos reservados por su regulador:**

| País | Número en los tests | Rango | Fuente |
|---|---|---|---|
| Estados Unidos | `+1 202 555 0143` y otras áreas | `555-0100` a `555-0199` | Reserva de la administración de numeración norteamericana para ficción |
| Reino Unido | `+44 20 7946 0143` | `020 7946 0000` a `0999`; móvil `07700 900000` a `900999` | [Ofcom, números para drama](https://www.ofcom.org.uk/phones-and-broadband/phone-numbers/numbers-for-drama): no se asignan a clientes |
| Australia | `+61 491 570 156` | Lista de números `0491 570 ...` | [ACMA, números para obras creativas](https://www.acma.gov.au/phone-numbers-use-tv-shows-films-and-creative-works) |
| España | `+34 612 ...`, `+34 91 ...` | **Sin rango oficial encontrado**, así que no se cambiaron | Búsqueda sin resultados en CNMC y Plan Nacional de Numeración |

Los rangos de Ofcom y ACMA salen de los resultados de búsqueda sobre esas páginas, porque el acceso directo falló. ACMA actualiza su lista y las copias difieren: revisar la página antes de reutilizar un número.

## Lecciones para messages

| Problema | Por qué pasaba | Solución |
|---|---|---|
| **`AGGREGATION_MODE_DISABLED` deja el contacto invisible.** Estuvo en el plan para que un contacto ficticio no se juntara con uno real. | La documentación dice que la ficha no tiene contacto y no sale en `Contacts`. | `AGGREGATION_MODE_SUSPENDED`, un nombre con "(prueba)" que nunca coincide con uno real, y el filtro excluye cualquier contacto que tenga fichas ajenas. |
| **Las apps de sincronización leen los contactos del sistema: jamás crear contactos de prueba con números de formato real.** Con números argentinos inventados aparecieron 2 fichas ajenas, más nuevas que las ficticias y pegadas a ellas, que se fueron solas menos de 25 s después de borrarlas. | Toda app con permiso de contactos los lee, y las que sincronizan pueden enviarlos a su servidor. Un número inventado con formato real puede ser la línea de alguien. No se leyeron esas fichas: solo se contaron. | `FakeNumbers`: solo `+1 NXX 555-01XX` y `+44 7700 900XXX`, con un control antes de cada escritura y un test. La herramienta se niega a crear datos si la agenda no está vacía y avisa si aparece una ficha ajena. Las listas en memoria (`DEV_MEMORY`) no las ve nadie. |
| **`+44 7700 900XXX` no es válido para `libphonenumber`.** | Es un rango de ficción y la biblioteca no lo da por asignado. | Se muestra tal cual y coincide por la cola. El formato argentino lo cubren los tests de `:core`, no los datos del celu. |
| **Filas ficticias en el registro pueden borrar filas reales.** | `CallLog.addCall` borra las más viejas de cada cuenta a partir de 500, y las ficticias cuentan. | 500 entradas solo en memoria; en el registro real unas 40, con tope de 450 por chip, un aviso mientras existan y borrarlas al terminar. |
| **La `ui/glass` de Contacts no era la de Calendar `25ca0d6`.** | `DESIGN.md` de Contacts dice "sin cambios", pero después se agregó `pillVeil()`. | Comparar los archivos con `diff` antes de anotar un origen. |
| **El chip principal del sistema puede no poder cambiarse.** | En el Samsung probado no hay forma de hacerlo. | Ajuste propio `primarySimSlot`, aclarando en pantalla que no toca el del sistema. Toda la lógica recibe la ranura, nunca asume la 0. |
| **La ranura 1 no es la bandeja física.** | `getSimSlotIndex` es la ranura lógica: en el celu probado, la 0 es la eSIM. | Llamarla "SIM 1" como el sistema, y nunca deducir nada de `isEmbedded`. |
| **`getPhoneAccount()` pide `READ_PHONE_NUMBERS`.** | Desde target 31. Ese permiso da el número de línea. | Nombre y color salen de `SubscriptionInfo`; `getPhoneAccount` no se usa. |
| **El id de cuenta del registro podría ser un identificador del chip.** | Depende del celu y de la versión. En el celu probado no lo es. | `AccountKey` opaca: solo se compara y su `toString` no la muestra, con un test. |
| **Una llamada de emergencia no puede salir por `placeCall`.** | La documentación solo la permite al marcador predeterminado o al del sistema. | `ACTION_DIAL`, también con el simulacro encendido. |
| **Las filas ficticias no prueban el formato real.** | Las escribe la misma herramienta con las mismas cuentas, así que siempre coinciden. | `DEV_ACCOUNT_CHECK` cuenta, solo cantidades, cuántas filas reales coinciden con los chips. |

### Para probar

- El modo seguro y el simulacro no tienen orden por adb a propósito: solo se apagan a mano.
- `tools/safe-capture.sh` se niega si el modo seguro está apagado, si la app no está en primer plano o si el celu está en horizontal, y recorta las barras. Antes de cada tanda, No molestar.
- `adb logcat` solo con `-s LiquidPhoneDev:I` o `-s AndroidRuntime:E`; la herramienta solo registra cantidades y decisiones, nunca números.
