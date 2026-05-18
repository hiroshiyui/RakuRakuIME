# RakuRaku IME 新修輕鬆輸入法

一款 Android 輸入法編輯器（IME），支援**輕鬆輸入法**（EZ Input Method），以 Jetpack Compose 與 Room 資料庫打造。

## 螢幕截圖

<table>
  <tr>
    <td align="center">
      <img src="fastlane/metadata/android/zh-TW/images/phoneScreenshots/01_settings.png" width="220" alt="設定畫面" /><br/>
      <sub>設定 — 輸入法狀態、主題切換、詞庫管理</sub>
    </td>
    <td align="center">
      <img src="fastlane/metadata/android/zh-TW/images/phoneScreenshots/02_ez_keyboard.png" width="220" alt="輕鬆輸入法鍵盤" /><br/>
      <sub>輕鬆輸入法組字中，並顯示候選字詞列</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="fastlane/metadata/android/zh-TW/images/phoneScreenshots/03_english_keyboard.png" width="220" alt="英文鍵盤" /><br/>
      <sub>完整 QWERTY 英文鍵盤，內建 Google-10K 單字預測</sub>
    </td>
    <td align="center">
      <img src="fastlane/metadata/android/zh-TW/images/phoneScreenshots/04_emoji_keyboard.png" width="220" alt="表情符號面板" /><br/>
      <sub>表情符號面板 — 八大分類、共 1,184 個字符</sub>
    </td>
  </tr>
</table>

## 來源與貢獻

本專案內附的字詞對應資料（`ezbig.utf-8.cin`）係由以下作者/單位製作：

- **原始作者：** 高衡緒
- **原始單位：** 輕鬆資訊企業社

用於英文單字預測的字表（`google_10k_english.txt`）取自 Josh Kaufman 的
*Google 10000 English* 語料，源自 Google Web Trillion Word Corpus，以 MIT 授權條款散布。
來源：https://github.com/first20hours/google-10000-english

表情符號分類資料（`emoji.json`）改編自 *MeaninglessKeyboard* 專案，
以 GNU 通用公共授權條款第 3 版（GPL-3.0）重新散布於此。
來源：https://github.com/hiroshiyui/MeaninglessKeyboard

*Roboto Slab* 字型（用於英數鍵帽，檔案內嵌於
`app/src/main/res/font/roboto_slab_*.ttf`）由 Christian Robertson 設計，
以 SIL Open Font License 1.1 授權。完整授權條款置於
`app/src/main/assets/OFL.txt`。來源：https://fonts.google.com/specimen/Roboto+Slab

候選字詞排序所使用的字頻／詞頻資料（`85rest01.csv`、`85rest02.csv`）來自
中華民國教育部《八十五年常用語詞調查報告》之字頻總表與詞頻總表，由
**政府資料開放平臺**公開釋出（資料集編號 45518）。引用時請標示：

> 中華民國教育部（Ministry of Education, R.O.C.）。《八十五年常用語詞調查報告——字頻總表／詞頻總表》。資料來源：政府資料開放平臺，<https://data.nat.gov.tw/dataset/45518>。

本資料依政府資料開放平臺之開放資料授權條款釋出。原始檔以 Big5 編碼，
為方便讀取，本專案內附之版本已透過 `iconv -c -f BIG5 -t UTF-8` 轉碼為
UTF-8（極少數非標準 Big5 位元組於部首／詞目欄位中被略過，不影響本專案
所用的排序優先序資料）。

## 授權條款

### 應用程式

本專案（RakuRaku IME）以 **GNU 通用公共授權條款第 3 版或更新版本**（GPLv3+）授權散布，
完整條款詳見 [LICENSE](LICENSE) 檔案。

### 詞庫資料

`ezbig.utf-8.cin` 詞庫資料另以下列條款授權：
- **GPLv2**（GNU 通用公共授權條款第 2 版，條款全文位於 `app/src/main/assets/gpl.txt`）
- **《輕鬆資訊「輕鬆輸入法大詞庫」公眾授權書》**（由輕鬆資訊企業社提供的輕鬆輸入法詞庫公眾授權書，全文位於 `app/src/main/assets/ezphrase.txt`）

## 致謝

- **高衡緒** — 輸入法發明人
- **蕭易玄** — 輸入法碼表長期維護者

## 功能特色

- 以 **Jetpack Compose** 打造的現代化宣告式使用者介面。
- 輕鬆輸入法鍵盤採用預先繪製的向量鍵帽圖像，一次呈現英數鍵位與對應的輕鬆字根。
- 使用 **Room SQLite** 達成高效能詞庫查詢。
- 裝置端 `.cin` 檔解析及首次啟動初始化。
- 支援前綴比對的動態候選字清單。
- **候選字詞排序內建語料先驗：** 詞庫於建置階段即依據教育部《八十五年常用語詞調查》之字頻／詞頻表灌入靜態權重（採倒數型 `floor(10000 / 排名)`，讓長尾排名仍能拉開差距），全新安裝首次使用時即依照常用程度排序，毋須等待學習頻率累積；使用者實際選字後，學習頻率仍會逐步主導排序。
- 選字後自動推薦下一個字：依據內附詞庫中的多字詞條，從剛選定的字（例如「信」）挖掘常見後續字（「件」「箱」「封」「賴」「任」⋯⋯）並顯示於候選字列；以點選方式選用，不佔用 1–0 數字快捷鍵，避免與輕鬆字根衝突。同樣以上述語料詞頻權重作為次要排序依據。
- 長按模式鍵叫出輸入模式選單，可直接切換到中文輕鬆、英文、數字符號或表情符號，毋須逐次循環。
- 長按帶有替代字元的按鍵（例如標點、重音字母）會彈出常駐選單，手指鬆開後再以點選方式挑選，避免一邊滑動一邊辨認的不便；任意點擊其他按鍵即可關閉選單。
- **使用者詞庫管理**：可新增、編輯、搜尋自訂詞語，並以 CSV 檔備份／還原；自訂詞語在候選字列中固定排於內附語料之前，使您指定的字詞始終可預測地出現。
- **備份與還原**：「設定 → 備份與還原」可將您的使用者詞語與學習頻率匯出為單一壓縮檔（`*.rkbak.gz`，gzipped JSON），於重裝或換機後再匯入合併。匯入時將整個檔案視為不信任輸入：對解壓後容量設有 50 MiB 上限以防止 zip bomb，採嚴格欄位檢查、每列長度與控制字元檢核，並沿用與新增詞語介面相同的輕鬆字根驗證；任一筆資料不合規則即中止整個匯入，不會留下半套還原的資料庫。

## 技術細節

- **Kotlin：** 2.2.10
- **Room：** 2.7.0-alpha11
- **註解處理器：** KSP（Kotlin Symbol Processing）
- **UI：** Material 3 + Compose，搭配 ComposeView 樹狀整合以管理 IME 生命週期。

## 安裝與設定

1. 建置並安裝 APK。
2. 開啟 App 以初始化詞庫（此步驟會解析 assets 目錄中的 `.utf-8.cin` 檔）。
3. 進入 Android 設定 > 系統 > 語言與輸入裝置 > 螢幕鍵盤，啟用 **RakuRakuIME**。

## 相關資源

- [輕鬆輸入法字詞編碼表整理工程](https://github.com/hiroshiyui/EzIM_Tables_Project)
- [輕鬆輸入法之家](https://eshensh.net/ez/)
