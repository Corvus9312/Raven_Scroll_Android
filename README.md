# Raven's Scroll

Android TXT 閱讀器，支援本機書庫（SAF）與 Google Drive，並與同名 VS Code 擴充共享閱讀進度。

---

## 功能

| 類別 | 功能 |
|------|------|
| **閱讀器** | WebView 渲染，章節自動偵測與側欄導覽 |
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
│   └── CharsetDetector.kt   BOM + CJK 啟發式編碼偵測
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

## Release 簽章設定（選用）

如需自動化 release 簽章，在專案根目錄建立 `keystore.properties`（**已列入 .gitignore**）：

```properties
storeFile=../my-release-key.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

然後在 `app/build.gradle.kts` 中引用：

```kotlin
val keystoreProps = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { java.util.Properties().apply { load(it.inputStream()) } }

android {
    signingConfigs {
        create("release") {
            storeFile     = keystoreProps?.getProperty("storeFile")?.let { file(it) }
            storePassword = keystoreProps?.getProperty("storePassword")
            keyAlias      = keystoreProps?.getProperty("keyAlias")
            keyPassword   = keystoreProps?.getProperty("keyPassword")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }
}
```

---

## Drive 進度格式（VS Code 相容）

`corvus-progress.json`（存於 Drive appDataFolder）：

```json
{
  "<driveFileId>": {
    "scrollTop": 12345,
    "percent": 87
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
