<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Liquid Phone

**English** | [Español](README.es.md)

Dual-SIM phone app for Android, with the same liquid glass look as the rest of the suite (Calendar,
Clock, Gallery, Calc, Contacts). **It is not a default dialer yet:** the system dialer stays installed
and handles calls.

## Privacy

- No network, telemetry, analytics, crash reporting or Google Play Services. 100% free software. It doesn't request the Internet permission.
- No caller ID or cloud lists, and no call recording.
- Your data stays in the system providers (contacts and call log). The app has no database of its own. No backups or transfers.

## Features

- Keypad with letters on the keys and search as you type, by letters (Spanish T9: ñ is on 6 and accents don't count) or by number. Hold 0 for "+"; hold delete to clear.
- Recents from the system call log: groups consecutive calls from the same number on the same SIM, tells missed, incoming and outgoing apart, and recognizes the contact in any Argentine format.
- Favorites and contacts, read-only. "Add to contacts" opens your contacts app.
- Numbers formatted for the default country.
- Appearance shared with the suite: Liquid or Solid style, theme, background, accent and 5 icons plus a monochrome one.

## Dual SIM

**What SIM 1 is.** It's the system's logical slot 0 (`SubscriptionInfo.getSimSlotIndex() == 0`), physical or eSIM, not the plastic tray. On the tested Samsung SM-S721B with Android 16, slot 1 is the eSIM and slot 2 is the physical SIM. Android's documentation warns the index may repeat or be invalid; in those cases the app asks and never guesses.

**Primary SIM.** You choose it in Settings (SIM 1 or SIM 2, shown with each SIM's name and color; SIM 1 by default). It's for phones like Samsung's, where the system sometimes doesn't let you change it. It applies to this app only: it doesn't change the system's default SIM.

The slot is what gets saved, not the SIM. **If you swap your SIMs, the primary becomes whichever one is now in that slot.** If the saved slot is invalid or the system reports repeated slots, the app asks.

**SIM for one-tap calls** (recents and favorites), in Settings:

1. Always the primary SIM. This is the default.
2. Always ask.
3. The system default account.
4. The last SIM you called that number with, from the outgoing calls in the log. No history: the primary SIM.

**Rules:**

- It never switches SIMs silently, since the other one may have a different rate. If the primary SIM is missing, disabled, has no service or the phone is in airplane mode, it confirms every time and names the other one: "The primary SIM is not there. Call with [other SIM]?".
- The keypad and details have two call buttons: the primary first and highlighted, then the other. They are an explicit choice and override the policy. If the tapped SIM has no service, it offers that one (in case of Wi-Fi calling), the other one or cancel.
- With a single SIM there is no selector. If it's the primary, it calls without asking; if it isn't, the same confirmation applies on every call.
- In recents each entry shows its SIM and can be filtered by SIM. An entry from a SIM no longer present is shown as such, without guessing. The detail screen can call back with the original SIM.
- **Emergencies:** always `ACTION_DIAL` with no SIM, through the system's standard flow. The app doesn't reimplement or intercept them.
- **Codes with `*` and `#`:** also go to the system dialer.

The phone state permission gives no access to ICCID, IMSI, IMEI or the line number, and the app doesn't ask for them. Visible name, color and slot are all it uses. The call account id is treated as opaque: it is only compared, never shown, stored or logged.

## Permissions

Requested when needed, after explaining why. If you deny any of them, the rest keeps working.

| Permission | What for | If denied |
|---|---|---|
| `READ_CONTACTS` | Searching from the keypad, showing names and favorites | Dials without names |
| `READ_CALL_LOG` | Recents and "last SIM used" | No recents; that policy uses the primary SIM |
| `WRITE_CALL_LOG` | Deleting recents; requested when deleting | Can't delete |
| `READ_PHONE_STATE` | Listing SIMs and call accounts | No selector; the system decides |
| `CALL_PHONE` | Calling with the chosen SIM | The system dialer opens with the number |

Nothing else: verified in the release APK. The manifest also blocks `INTERNET`, `ACCESS_NETWORK_STATE`, location and `READ_PHONE_NUMBERS` even if a library asked for them. Service state is read without location.

Call log permissions are restricted on Android. When installing an APK by hand, the system may ask you to allow the app's restricted settings.

## Safe test mode

Debug and profile only (`dev.liquidphone.debug`, "Phone (test)"). The release contains none of this (0 matches in its code).

- **Safe mode, on by default.** The app only sees contacts with source `liquidphone-debug-N` and calls inserted by the tool. The filter is inside each SQL query: real data isn't even read. It can only be turned off by hand, in Settings. While off, the window can't be captured (`FLAG_SECURE`).
- **Call simulation, on by default.** Calling shows "Would call [number] with [SIM n]" and doesn't dial. It also only turns off by hand. Emergencies don't go through the simulation.
- **Fake data.** The tool creates 24 contacts and 40 calls, and deletes only what it created, row by row. While they exist, a notice says so: they also show up in system apps, and **you shouldn't make real calls** until they're deleted, since Android deletes each SIM's oldest calls past 500. That's why the tool refuses to go past 450, and the 500 entries of the performance test only exist in memory.

```bash
P=dev.liquidphone.debug
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_GENERATE -p $P       # create
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_CLEAR -p $P          # delete everything
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_STATUS -p $P         # status, counts only
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_MEMORY -p $P --ei on 1 # 500 rows in memory
adb logcat -d -s LiquidPhoneDev:I
```

The same delete is a button in Settings. `tools/safe-capture.sh` takes a capture only if safe mode is on and the app is in the foreground, and crops the system bars. No adb command turns off safe mode or the simulation.

## Known limitations

- **Not the default dialer**, and doesn't handle `tel:` from other apps. Ongoing calls are shown by the system dialer.
- Emergency calls can't choose a SIM and always need a tap on call in the system dialer.
- Service state arrives a moment after opening the app; until then, service isn't assumed to be missing.
- With Wi-Fi calling a SIM can show "no service" and still call: that's why the question offers using it anyway.
- A contact with several numbers calls from its details, to choose which one.
- The default area code only exists for Argentina.
- Favorites are marked in the contacts app: this one doesn't write to the address book.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :core:test :app:testDebugUnitTest   # tests
./gradlew :app:lintDebug
./gradlew :app:assembleDebug                  # dev.liquidphone.debug, "Phone (test)"
./gradlew :app:assembleProfile                # same, optimized: real performance
./gradlew :app:assembleRelease
```

## Structure

```
core/   Android-free logic, tested on the JVM: T9, call log grouping, SIM policy, numbers, search, sorting and avatars
app/    The app: calls/ (log and dialing), contacts/ (reading the address book), sim/ (SIMs), ui/
tools/  Safe capture for testing
```

## What to copy to messages

When copying, change the package. None of this dials or touches the call log.

**Pure logic (`core/src/main/kotlin/dev/liquidphone/core/`), needs `libphonenumber`:**

| File | What it does |
|---|---|
| `phone/PhoneNumbers.kt` | Normalization and `PhoneMatcher`; unchanged from Contacts |
| `text/ContactText.kt` | `Fold`, `SpanishOrder`, `ContactSearch`; unchanged |
| `avatar/Avatars.kt` | Initials and stable color; unchanged |
| `model/Contact.kt` | Data models; unchanged |
| `sim/CallDecider.kt` | `SimChip`, opaque `AccountKey` and `SimCountry`. `CallDecider` only if messages chooses a SIM with the same rules |

**Their tests:** `phone/PhoneNumbersTest.kt`, `text/ContactTextTest.kt`, `avatar/AvatarsTest.kt`, `sim/CallDeciderTest.kt`.

**Android (`app/src/main/java/dev/liquidphone/`):**

| File | What it does |
|---|---|
| `contacts/SystemContacts.kt` | Read-only address book, with a per-query filter |
| `sim/SystemSims.kt` | Active SIMs, accounts and service state without location |
| `ui/components/ContactComponents.kt` | Avatar, flat row, index, search, notice, destructive button |

**Debug (`app/src/debug/java/dev/liquidphone/`):** `DevTools.kt` and `DevReceiver.kt` are the model for safe mode. Rewrite the data, not the mechanism.

**Don't copy:** `calls/`, `core/calls/`, `core/dial/` and the screens, which are specific to this app.

## Dependencies

Besides AndroidX, Compose and Kotlin, the APK includes:

| Library | Version | License | Network |
|---|---|---|---|
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) (glass effect) | 1.0.0 | Apache-2.0 | None |
| [libphonenumber](https://github.com/google/libphonenumber) (base artifact only) | 9.0.42 | Apache-2.0 | None: country data is inside the jar |

For tests only, **not in the APK**: kotlin-test (Apache-2.0) and JUnit 4 (EPL-1.0).

### Audit

- **Full tree** (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`): 96 artifacts, all Apache-2.0 according to their POMs; `libphonenumber` and `listenablefuture` declare the license in their parent POM. All compatible with GPL-3.0-or-later.
- **No Google Play Services, Firebase or datatransport**, and no `HttpURLConnection` or OkHttp: 0 matches in the release APK's code.
- **Final manifest:** only the 5 permissions above. Exported: only the 5 icon aliases. `tools:node="remove"` removes androidx.core's internal permission, `androidx.startup` and the profileinstaller receiver.
- **F-Droid:** `allowBackup=false`, `dependenciesInfo` off, no ART profiles in the APK. Compose brings `libandroidx.graphics.path.so` (Apache-2.0, source in AOSP) from Maven; the repo has no binaries.

<details>
<summary>The 96 release dependencies</summary>

| Dependency | Version | License |
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

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
