# RakuRaku IME TODOs

This list tracks the missing features and planned improvements for RakuRaku IME, modeled after professional IME standards (like JustArray and Gboard).

## Input & Logic
- [x] **Frequency-Based Sorting:** Dynamically move frequently used characters to the front of the candidate list based on user history.
- [ ] **Auto-Select:** Automatically commit a character if it is the only possible match for a sequence.
- [ ] **Short Code (簡碼) & Special Code support:** Implement specific EZ-method optimizations for common phrases.
- [ ] **English Word Prediction:** Suggest English words as you type in English/Shifted mode.
- [ ] **Physical Keyboard Support:** Logic to handle typing when a Bluetooth or USB keyboard is attached.
- [ ] **Pro Shift Key:** Implement "one-shot" shift (resets after one key) and "Caps Lock" (double-tap).

## User Interface (UI)
- [ ] **Advanced Symbol Panels:** Categorized symbol pages (Punctuation, Math, Arrows, Emoticons) instead of a single row.
- [ ] **Key Alternates:** Show a popup of related characters (e.g., accented vowels) on long-press.
- [ ] **Adjustable Keyboard Height:** Allow users to scale the keyboard height in settings.
- [ ] **Dynamic Material You Theming:** support wallpaper-derived colors on Android 12+.
- [ ] **Split Keyboard Layout:** A specialized layout for landscape orientation or tablets.

## System & Settings
- [ ] **Settings Screen:** A dedicated UI in `MainActivity` to manage preferences.
    - [ ] Vibration intensity/toggle.
    - [ ] Dictionary management (re-import/reset).
    - [ ] "About" section with full attribution for **高衡緒** and **輕鬆資訊企業社**.
- [ ] **Accessibility (TalkBack):** Add comprehensive `semantics` labels for candidates, roots, and function keys.

## Technical Debt / Performance
- [ ] **Database Optimization:** Ensure Room queries for prefix matching remain performant as usage history grows.
- [ ] **Initialization UX:** Add a progress bar for the first-run dictionary import.
