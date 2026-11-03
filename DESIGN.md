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

`data/AppSettings.kt`, en `files/settings.json`: los 8 campos de la suite con los mismos nombres y valores (`SettingsFormatTest` lo comprueba), `defaultCountry`, `defaultAreaCode` y `areaCodeNoticeDismissed` de Contacts, y los propios `simPolicy`, `startTab` y `keypadHaptics`. El modo seguro y el simulacro van aparte, en `files/dev_state.json`, que solo existe en debug.

Íconos: los 5 estilos en el orden de siempre (Liquid por defecto, Material, Samsung, iPhone, Motorola) más monocromo. Los fondos y recipientes son los de Contacts; el dibujo es un tubo de teléfono.

## La elección de chip

`core/sim/CallDecider.kt` decide; `calls/Dialer.kt` ejecuta y es el único lugar que marca. El orden importa:

1. Sin número, nada.
2. **Emergencia:** marcador del sistema. Si la comprobación falla, se trata igual: `ACTION_DIAL` no marca solo. Va antes de permisos, chips y simulacro.
3. Códigos con `*` o `#`: marcador del sistema.
4. Sin `CALL_PHONE`: marcador del sistema. Sin `READ_PHONE_STATE`: decide el sistema.
5. Ranuras repetidas o inválidas, o un único chip fuera de la ranura 1: preguntar.
6. El chip que pide el botón o la política. Si se puede usar, ese y ningún otro. Si no, preguntar.

`CallDeciderTest` recorre todas las combinaciones de chips, estados, políticas, pedidos e historial y comprueba que el chip de una llamada sea siempre exactamente el pedido.

## Lecciones para messages

| Problema | Por qué pasaba | Solución |
|---|---|---|
| **`AGGREGATION_MODE_DISABLED` deja el contacto invisible.** Estuvo en el plan para que un contacto ficticio no se juntara con uno real. | La documentación dice que la ficha no tiene contacto y no sale en `Contacts`. | `AGGREGATION_MODE_SUSPENDED`, un nombre con "(prueba)" que nunca coincide con uno real, y el filtro excluye cualquier contacto que tenga fichas ajenas. |
| **Filas ficticias en el registro pueden borrar filas reales.** | `CallLog.addCall` borra las más viejas de cada cuenta a partir de 500, y las ficticias cuentan. | 500 entradas solo en memoria; en el registro real unas 40, con tope de 450 por chip, un aviso mientras existan y borrarlas al terminar. |
| **La `ui/glass` de Contacts no era la de Calendar `25ca0d6`.** | `DESIGN.md` de Contacts dice "sin cambios", pero después se agregó `pillVeil()`. | Comparar los archivos con `diff` antes de anotar un origen. |
| **La ranura 1 no es la bandeja física.** | `getSimSlotIndex` es la ranura lógica: en el celu probado, la 0 es la eSIM. | Llamarla "SIM 1" como el sistema, y nunca deducir nada de `isEmbedded`. |
| **`getPhoneAccount()` pide `READ_PHONE_NUMBERS`.** | Desde target 31. Ese permiso da el número de línea. | Nombre y color salen de `SubscriptionInfo`; `getPhoneAccount` no se usa. |
| **El id de cuenta del registro podría ser un identificador del chip.** | Depende del celu y de la versión. En el celu probado no lo es. | `AccountKey` opaca: solo se compara y su `toString` no la muestra, con un test. |
| **Una llamada de emergencia no puede salir por `placeCall`.** | La documentación solo la permite al marcador predeterminado o al del sistema. | `ACTION_DIAL`, también con el simulacro encendido. |
| **Las filas ficticias no prueban el formato real.** | Las escribe la misma herramienta con las mismas cuentas, así que siempre coinciden. | `DEV_ACCOUNT_CHECK` cuenta, solo cantidades, cuántas filas reales coinciden con los chips. |

### Para probar

- El modo seguro y el simulacro no tienen orden por adb a propósito: solo se apagan a mano.
- `tools/safe-capture.sh` se niega si el modo seguro está apagado o la app no está en primer plano, y recorta las barras. Antes de cada tanda, No molestar.
- `adb logcat` solo con `-s LiquidPhoneDev:I` o `-s AndroidRuntime:E`; la herramienta solo registra cantidades y decisiones, nunca números.
