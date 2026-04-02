# Project Knowledge: RakuRaku IME (EZ Input Method)

## Dictionary and Assets
- The primary dictionary source is `app/src/main/assets/ezbig.utf-8.cin`.
- This file was converted from the original Big5 `ezbig.cin` using `iconv -c -f BIG5-HKSCS -t UTF-8`.
- Licensing and attribution for the dictionary data (高衡緒 and 輕鬆資訊企業社) are maintained in `app/src/main/assets/LICENSE_EZBIG.md`, `gpl.txt`, and `ezphrase.txt`.

## Technical Understanding of 輕鬆輸入法 (EZ Input Method)

### 1. Shape-Based Radical Philosophy
EZ is a radical-based system that balances stroke order with visual identification. It decomposes characters into fundamental components called "roots" (字根).

### 2. Full-Keyboard Root Mapping
EZ utilizes 48 keys across four rows, including numbers and punctuation, as first-class roots:
- **Number Row (1-0, -, =):** Major radicals (e.g., `2` for `車`, `4` for `言`, `6` for `雨`, `=` for `母`).
- **Letter Rows:** QWERTY mapping for roots (e.g., `q` for `手`, `v` for `女`, `z` for `辶`, `x` for `又`).
- **Punctuation Roots:** Keys like `[` (匚), `]` (】), `;` (寸), `'` (Ｌ), and `/` (ㄙ) are integral to character sequences.

### 3. Keystroke Sequences
Sequences are often hybrid, mixing letters, numbers, and symbols (e.g., `z4` for "這", `v=` for "姆"). This multi-modal approach results in very short codes (typically 1–3 keys) for common characters.

### 4. Phrase Composing Rules
EZ uses highly efficient shorthand logic for phrases (詞彙), which is why the dictionary contains nearly 100,000 entries:
- **The "First Root" Principle:** Phrases typically use the first root of the most significant characters rather than full character sequences.
- **Internal Delimiters:** The `,` (，) and `.` (＼) keys are used as internal delimiters to signal phrase mode and separate component roots (e.g., `z,4` for "這句話").
- **Prefix Shortcuts:** Common prefixes like `''`, `,,`, or `..` are used for idioms and common phrases (e.g., `''33` for "斷斷續續").
- **Positional Encoding:** 2-character words often combine the first root of the first character and the last root of the second character.
- **Visual "Anchors":** Sequences prioritize distinct visual components to aid memory and recognition.

### 5. Selection Logic
Key conflict management is essential because numbers are both roots and selection triggers. 
- **Rule:** Number keys continue the root sequence by default.
- **Selection Mode:** Pressing **Space** triggers "Selection Mode" (if multiple candidates exist), during which number keys act as selection triggers for the candidate bar.
