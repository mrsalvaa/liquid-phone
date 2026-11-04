<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Liquid Phone

**English** | [Español](README.es.md)

Dual-SIM phone app for Android, with the same liquid glass look as the rest of the suite (Calendar,
Clock, Gallery, Calc, Contacts). You can use it alongside the system dialer, or choose it as your phone app so it also handles calls. The system dialer stays installed
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
- **Emergencies:** always with no SIM chosen and through the system's standard flow, from any SIM. When it isn't your phone app they open the dialer; when it is, they go through Telecom. The app doesn't reimplement, block or intercept them.
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
| `POST_NOTIFICATIONS` | Incoming, in-progress and missed call; asked when choosing it as phone app | No such notifications; incoming opens the screen directly |
| `USE_FULL_SCREEN_INTENT` | Show incoming calls over the lock screen | It stays a notification |
| `WAKE_LOCK` | Turn the screen off with the proximity sensor | Can't be denied; no dialog for this permission |

Nothing else: verified in the release APK. The manifest also blocks `INTERNET`, `ACCESS_NETWORK_STATE`, location and `READ_PHONE_NUMBERS` even if a library asked for them. Service state is read without location.

Call log permissions are restricted on Android. When installing an APK by hand, the system may ask you to allow the app's restricted settings.

## Using it as your phone app

It's optional and your decision: the app also works without it. **Settings → Phone app → Use as phone app** shows the system dialog. Nothing asks for it on its own.

While it is your phone app:

- **Incoming call:** over the lock screen, with answer, reject and the SIM it comes in on. With the phone in use, as a notification with those same buttons. Ringtone and vibration are the system's: they follow your ringtone, your volume and Do Not Disturb.
- **Call in progress:** mute, audio output, hold, tone keypad, add, swap, merge and hang up. Only what the network allows is shown. Hang up always stays in view.
- **Proximity sensor:** turns the screen off with the phone at your ear; not on speaker, Bluetooth or headset.
- **Notifications:** incoming, in progress and missed, the latter with the SIM.
- **Number blocking:** in Settings or a call's details. It uses the phone's own list, which stays there, and the system rejects those calls before they ring. It stops blocking for a while after an emergency, so they can call you back. If you later change phone apps, the list stays in the phone.
- **`tel:` links from other apps:** leave the number written on the keypad. They never dial on their own.

**To go back to the previous dialer:** Settings → Apps → Choose default apps → Phone app, and pick the one you had. The system dialer stays installed the whole time.

## Test contacts

Debug and profile only (`dev.liquidphone.debug`, "Phone (test)"). The release contains none of this. The test build sees the phone's real contacts and calls, and **the call button places real calls**.

The tool creates 24 varied contacts: several numbers, email, photo, long name, no phone and two sharing a number. It uses numbers from ranges reserved for fiction and names with "(prueba)", and deletes only what it created, row by row. If the address book has contacts from another source, it refuses to create anything. It doesn't touch the call log.

```bash
P=dev.liquidphone.debug
adb shell pm grant $P android.permission.WRITE_CONTACTS                    # once per install
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_GENERATE -p $P       # create
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_CLEAR -p $P          # delete what it created
adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_STATUS -p $P         # status, counts only
adb logcat -d -s LiquidPhoneDev:I
```

`tools/safe-capture.sh` takes a capture only if the app is in the foreground, and crops the system bars. Since the app shows real data, don't capture Recents or a contact's history if there are real calls.

## Lesson: sync apps read the system contacts

**Never create test contacts with real-format numbers.** A contact created in the system can be read by every app with the contacts permission, and sync apps may send it to their servers. A made-up number in a real format can be someone's line.

In the first test, with made-up Argentine numbers, 2 entries from another app showed up attached to fake contacts, and went away on their own once those were deleted. That's why the tool now only creates numbers from ranges reserved for fiction (`+1 NXX 555-01XX` and `+44 7700 900XXX`), checks the range before every write and refuses to create data if the contacts aren't empty. Other apps can still read those contacts.

## Known limitations

- The app sends no message when rejecting, doesn't record, and has no visual voicemail or video calls.
- **Not tested on a real phone:** see "Qué se probó" in `DESIGN.md`.
- Emergency calls can't choose a SIM and always need a tap on call in the system dialer.
- Service state arrives a moment after opening the app; until then, service isn't assumed to be missing.
- With Wi-Fi calling a SIM can show "no service" and still call: that's why the question offers using it anyway.
- A contact with several numbers calls from its details, to choose which one.
- The default area code only exists for Argentina.
- Favorites are marked in the contacts app: this one doesn't write to the address book. The star on a contact opens it in Liquid Contacts.

## Download

Get the APK from [Releases](../../releases) and install it. Changes in each version are in the [changelog](CHANGELOG.md).

Check that the file wasn't altered:

```bash
sha256sum -c liquid-phone-v1.1.0.apk.sha256
```

```
b3d63dca7ba98259ce56a4aed638f470a034f27599d2a627d496ede2ea609b06  liquid-phone-v1.1.0.apk
```

And that it's signed with the project key (SHA-256 fingerprint of the certificate):

```bash
apksigner verify --print-certs liquid-phone-v1.1.0.apk
```

```
58:E9:4E:BC:07:52:A9:62:D0:CF:DB:B2:AD:2F:2F:EC:2F:9E:C1:D9:A4:0E:52:3E:2B:C4:2F:DE:60:D5:B8:05
```

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
| `contacts/SystemContacts.kt` | Read-only address book |
| `sim/SystemSims.kt` | Active SIMs, accounts and service state without location |
| `ui/components/ContactComponents.kt` | Avatar, flat row, index, search, notice, destructive button |

**Debug (`app/src/debug/java/dev/liquidphone/`):** `DevTools.kt` and `DevReceiver.kt` create and delete test contacts over adb. Rewrite the data, not the mechanism.

**Don't copy:** `calls/`, `core/calls/`, `core/dial/` and the screens, which are specific to this app.

## Dependencies

Besides AndroidX, Compose and Kotlin, the APK includes:

| Library | Version | License | Network |
|---|---|---|---|
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) (glass effect) | 1.0.0 | Apache-2.0 | None |
| [libphonenumber](https://github.com/google/libphonenumber) (base artifact only) | 9.0.42 | Apache-2.0 | None: country data is inside the jar |

For tests only, **not in the APK**: kotlin-test (Apache-2.0) and JUnit 4 (EPL-1.0).

### Audit

- **Full tree** (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`): 96 artifacts, all Apache-2.0 according to their POMs. Two don't declare it in their direct POM and inherit it from the parent POM, both checked: `libphonenumber` 9.0.42 from `libphonenumber-parent` 9.0.42 and `listenablefuture` 1.0 from `guava-parent` 26.0-android. All compatible with GPL-3.0-or-later.
- **No Google Play Services, Firebase or datatransport**, and no `HttpURLConnection` or OkHttp: 0 matches in the release APK's code.
- **Final manifest:** only the 8 permissions above. Exported: the 5 icon aliases, `MainActivity` (DIAL and VIEW `tel:`, never CALL), the call service (only Telecom can connect) and the missed-call receiver, which uses nothing from the intent. `tools:node="remove"` removes androidx.core's internal permission, `androidx.startup` and the profileinstaller receiver.
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
