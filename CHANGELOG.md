<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Changelog

**English** | [Español](CHANGELOG.es.md)

## Unreleased

### Changed
- During a call, the contact photo is a bit smaller: up to 240 dp, or two thirds of the width.
- A short "bloop" plays when a call ends, whoever hangs up (not when declining or on missed calls).

## 1.2.2

### Changed
- During a call, the contact photo is bigger: up to 280 dp, or three quarters of the width.

## 1.2.1

### Changed
- New app icons. The default (Liquid) icon has a sober graphite background instead of the cyan and pink glow, and its accent is now the same red across the suite. The Material icon is a solid color with a smaller white drawing, without the light-blue frame.
- New Samsung-style icon.
- Settings: "Accent color" is now just "Accent".

## 1.2.0

### Added
- Recents: a button to delete the call history. With a filter on, it only deletes what the filter shows. It always asks first, and says how many calls.
- Recents: multiple selection. Long-press an entry to start; the details stay on the (i) button.

### Changed
- Keypad: taller keys and bigger digits, with a small animation when pressed.
- Recents: the filter window is redesigned, with a group for call type and one for SIM, one choice per group, and a button that says how many calls will show.

## 1.1.1

### Changed
- New Samsung and iPhone icons. The Motorola icon is removed: anyone using it moves to Liquid.
- Recents: filters are in a floating window, with a button next to Settings.
- Landscape call: hang up sits next to the other buttons and the photo is no longer cut off.

### Fixed
- The incoming call opens its screen also when the phone is locked, off or in use. Before, on apps installed outside a store, only the notification appeared.

## 1.1.0

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
