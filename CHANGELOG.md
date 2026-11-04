<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Changelog

**English** | [Español](CHANGELOG.es.md)

## Unreleased

### Added
- Keypad with Spanish T9, recents from the system call log, favorites and contacts.
- Dual SIM: SIM policy, two call buttons, SIM and filter in recents, call back with the original SIM. SIMs are never switched silently.
- Primary SIM chosen in Settings, for this app only: it sets the default policy, the highlighted button and when a confirmation is needed because it isn't available.
- Better contrast in light theme: text on color always reaches 4.5:1. It's a local exception from the rest of the suite.
- Optional phone app (dialer role): incoming call over the lock screen with the SIM, call in progress (mute, audio, hold, tones, add, swap, merge, hang up), proximity sensor, notifications, missed call with the SIM and number blocking with the phone's own list. New permissions: `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` and `WAKE_LOCK`.
- Test contacts, debug and profile only.
- Title and add button on the keypad; the one-tap SIM is chosen in a floating window; holding delete clears the number with a short animation; with one SIM there is nothing to choose.
- Faster tab change when tapping the bar.
- New incoming call screen: contact photo, name lower down, and slide to answer or reject. Answer is on the right.
- Keypad and SIM and call buttons in glass on Liquid Glass, with the block more centered.
- Ongoing call: photo in the center, glass buttons, tone keypad hidden by default and animated, over the photo on Liquid Glass.
- Keypad notice to use this as the phone app: otherwise calls open in whichever app is.
- Full contact sheet, with the Liquid Contacts design. The star, open and edit go to Contacts.
