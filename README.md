# Raven's Scroll

Android TXT / EPUB 閱讀器，支援本機書庫（SAF）與 Google Drive，並與同名 VS Code 擴充共享閱讀進度。

---

## 功能

| 類別 | 功能 |
|------|------|
| **閱讀器** | WebView 渲染，章節自動偵測與側欄導覽 |
| **EPUB 支援** | 解析 spine／內建目錄，保留排版與圖片並沿用使用者字型與主題；零依賴解析，並以斷網沙箱（攔截網路請求）與解壓炸彈上限做防護 |
| **進度同步** | 讀取 / 寫入 Google Drive appDataFolder 的 `corvus-progress.json`，格式與 VS Code 擴充相容 |
| **跨裝置還原** | 以 `percent` 還原位置，不依賴裝置特定的 `scrollTop`（桌面 VS Code ↔ 手機皆正確） |
| **本機書庫** | SAF 授權資料夾，支援多書庫管理、進度追蹤 |
| **編碼支援** | UTF-8 / UTF-16 LE / UTF-16 BE / UTF-32 LE / UTF-32 BE（含 BOM）、BIG5、GB18030 |
| **偏好設定** | 字型、字級、行距、主題，同步至 Drive `corvus-prefs.json` |
| **連續閱讀** | 同資料夾內下一本書預告橫幅（進度 ≥ 95%） |
| **Drive 書庫** | 資料夾進度一覽（`✓ N/total`），檔案完結標記（`✓ 完結`） |

---

## 技術架構

```
app/
├── data/
│   ├── db/            Room — 本機書庫（Book、Folder）
│   ├── drive/         DriveApiClient — Google Drive REST API
│   ├── model/         DriveItem、Book、Folder
│   └── repository/    BookRepository、DriveRepository
├── domain/
│   ├── CharsetDetector.kt   BOM + CJK 啟發式編碼偵測
│   ├── EpubParser.kt        EPUB 解析（zip/OPF/spine/目錄，零依賴）
│   └── BookFormats.kt       格式判斷與 ZIP 位元組嗅探
└── ui/
    ├── drive/         DriveScreen + DriveViewModel（Drive 書庫）
    ├── library/       本機書庫管理
    ├── reader/        ReaderScreen + ReaderViewModel + ReaderBridge
    ├── recent/        最近閱讀紀錄
    ├── navigation/    Compose Navigation
    └── theme/         Material 3 主題
```

**主要技術：** Kotlin · Jetpack Compose · Material 3 · MVVM · Room · DataStore · WebView · SAF · Google Drive API v3 · Coroutines

**最低 API：** 26（Android 8.0）

---

## 版本歷程

| 版本 | 說明 |
|------|------|
| 1.0.4 | 新增 EPUB 閱讀支援：解析 spine／內建目錄、保留 HTML 排版並沿用字型與主題、圖片內嵌；以 WebView 斷網沙箱與解壓炸彈上限防護惡意內容。資料夾下載改為遞迴（含巢狀子資料夾，攤平存放）；書庫支援刪除書籍／整個資料夾 |
| 1.0.3 | 資料夾計數改為已完結數（≥ 95%），與 VS Code 端一致 |
| 1.0.2 | 修正 UTF-8 BOM 偵測：BOM 存在時直接以 UTF-8 解碼，不再回退至 CJK 編碼，避免含 BOM 的混合編碼檔案亂碼 |
| 1.0.1 | 時間戳記同步（取代百分比比較）、重置進度功能、閱讀器預設字級 17 / 行距 1.6、調整字體時滾動位置固定 |
| 1.0.0 | 初始發布 |

---

## 建置前置作業

### 1. Google Cloud Console 設定

1. 前往 [Google Cloud Console](https://console.cloud.google.com/) 建立（或選擇）專案
2. 啟用 **Google Drive API**
3. 建立 **OAuth 2.0 用戶端憑證**（類型：Android）
   - 填入 `com.corvus.bookreader` 作為套件名稱
   - 填入你的 debug / release 簽章 SHA-1 指紋
4. 設定 OAuth 同意畫面，新增以下 scope：
   - `https://www.googleapis.com/auth/drive.readonly`
   - `https://www.googleapis.com/auth/drive.appdata`
5. 下載 `google-services.json`，放至 `app/` 目錄下

> **注意：** `google-services.json` 已列入 `.gitignore`，**請勿提交**。

### 2. 取得 debug SHA-1

```bash
./gradlew signingReport
```

### 3. 建置

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需先設定 signing config）
./gradlew assembleRelease
```

---

## Release 簽章設定

1. 複製 `keystore.properties.template` 為 `keystore.properties`（已列入 `.gitignore`）
2. 填入實際的金鑰路徑與密碼
3. 執行打包腳本：

```powershell
.\build-release.ps1
```

腳本會自動讀取 `keystore.properties`、設定環境變數並執行 Gradle 打包，輸出 APK 位於 `app/build/outputs/apk/release/app-release.apk`。

---

## Drive 進度格式（VS Code 相容）

`corvus-progress.json`（存於 Drive appDataFolder）：

```json
{
  "<driveFileId>": {
    "scrollTop": 12345,
    "percent": 87,
    "updatedAt": 1716400000000
  }
}
```

`corvus-prefs.json`：

```json
{
  "fontSize": 14,
  "lineHeight": 1.3,
  "fontFamily": "lxgw",
  "theme": "dark"
}
```

兩份檔案同時被 VS Code 擴充與本 app 讀寫，進度以 `percent` 欄位跨裝置還原，`scrollTop` 僅作為同裝置備援。

---

## .gitignore 重點說明

| 規則 | 原因 |
|------|------|
| `google-services.json*` | 含 OAuth Client ID，不得公開 |
| `keystore.properties` | 含簽章密碼 |
| `*.jks` / `*.keystore` / `*.p12` | 簽章金鑰 |
| `local.properties` | SDK 本機路徑（Android Studio 自動產生） |
| `.gradle/` / `**/build/` | 編譯快取 |
| `.idea/` | IDE 個人設定 |
| `.claude/` | Claude Code 會話資料 |

---

## 授權

本專案採 MIT 授權。字型 [LXGW WenKai](https://github.com/lxgw/LxgwWenKai) 採 SIL Open Font License 1.1。
