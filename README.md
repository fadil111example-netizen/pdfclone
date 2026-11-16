# Lumina PDF

> **Baca. Dengar. Pahami.**

Pembaca PDF Android yang dibangun dari nol dengan Kotlin + Jetpack Compose, dengan
satu pembeda utama: **AI Auto-Baca** — dokumen dibacakan dengan suara yang wajar,
kata yang sedang diucapkan disorot, dan halaman mengikuti suara secara otomatis.

Proyek ini **clean-room**: seluruh kode ditulis dari nol. Tidak ada decompile,
tidak ada kode pihak ketiga yang disalin, tidak ada APK komersial yang dibongkar
atau didistribusikan ulang.

---

## Daftar isi

1. [Fitur](#fitur)
2. [Batasan yang jujur](#batasan-yang-jujur)
3. [Privasi](#privasi)
4. [Membuka di Android Studio](#membuka-di-android-studio)
5. [Membangun lewat GitHub Actions](#membangun-lewat-github-actions)
6. [Menjalankan unit test](#menjalankan-unit-test)
7. [Struktur proyek](#struktur-proyek)
8. [Tumpukan teknologi](#tumpukan-teknologi)
9. [Kredit](#kredit)

---

## Fitur

### Membaca

| Fitur | Keterangan |
| --- | --- |
| Render halaman | `android.graphics.pdf.PdfRenderer`, bitmap dibatasi ±6 MP agar tidak OOM |
| Dua mode baca | **Gulir** (continues) dan **Halaman** (paged) |
| Zoom | Cubit dua jari, plus tombol perbesar/perkecil di bilah atas |
| Warna kertas | Terang, Sepia, Redup, Hitam |
| Filter halaman | Normal, Invert, Grayscale, Kontras tinggi |
| Volume sebagai navigasi | Tombol volume atas/bawah membalik halaman (bisa dimatikan) |
| Layar tetap menyala | Opsional saat membaca |
| Buka dari aplikasi lain | Menerima `ACTION_VIEW` dan `ACTION_SEND` untuk berkas PDF |

### AI Auto-Baca

| Fitur | Keterangan |
| --- | --- |
| Suara natural | Memilih suara neural/online terbaik yang tersedia di perangkat, dengan skor pemeringkatan sendiri |
| Perbaikan teks | Hyphenation, header berulang, nomor halaman, dot leader, ligatur, dan singkatan dibereskan sebelum dibacakan |
| Kalimat sebagai unit | Tiap kalimat menjadi satu potongan audio, sehingga sorotan bergerak wajar |
| Sorotan kata | Kata yang sedang diucapkan disorot secara real-time |
| Auto-scroll | Halaman mengikuti posisi suara |
| Jeda alami | Jeda sebelum judul bab, jeda antarparagraf, dan jeda akhir kalimat sesuai tanda baca |
| Kontrol kecepatan | 0,5× – 2,0× |
| Kontrol nada | 0,5× – 1,6× (sedikit lebih rendah terdengar kurang seperti robot) |
| Pemilih suara | Daftar seluruh suara TTS perangkat, dengan lencana kualitas |
| Deteksi bahasa | Otomatis memilih locale Indonesia, Inggris, Melayu, Spanyol, Prancis, Jerman, atau Arab |
| Pewaktu tidur | 5, 10, 15, 30, 60 menit |
| Navigasi | Sebelumnya / berikutnya per kalimat, slider progres, lompat per halaman |
| Fokus audio | Berhenti saat telepon masuk, melanjutkan setelahnya |
| Autoplay | Mulai membacakan otomatis saat layar dibuka (opsional) |

### Pustaka

- Impor PDF lewat pemilih berkas sistem (Storage Access Framework)
- Pindai otomatis berkas PDF di perangkat (opsional, butuh izin)
- Sampul dokumen dibuat dari judul, bukan thumbnail palsu
- Progres baca, favorit, dan "lanjutkan membaca"
- Pencarian teks di dalam dokumen, dengan progres
- Daftar isi (outline) PDF, dengan bab aktif tersorot
- Penanda buku

### Pengaturan

Tema (sistem/terang/gelap), warna dinamis, ukuran huruf panel baca, font serif,
jeda alami, lewati header berulang, sorotan kata, autoplay, pewaktu tidur.

---

## Batasan yang jujur

Aplikasi ini **tidak** melakukan hal-hal berikut. Ini bukan kekurangan yang
disembunyikan, melainkan batas cakupan yang disengaja:

- **Tidak ada OCR.** PDF hasil pindai (gambar) tanpa lapisan teks tidak bisa
  dibacakan. Aplikasi akan memberi tahu hal ini secara eksplisit.
- **Tidak mengedit PDF.** Tidak ada anotasi, isi formulir, atau penyuntingan teks.
- **Tidak menggabungkan atau memisah** berkas PDF.
- **Tidak mengonversi** PDF ke Word/Excel/gambar, dan sebaliknya.
- **Tidak membubuhkan tanda tangan** digital maupun basah.
- **Tidak ada sinkronisasi awan.** Pustaka hanya ada di perangkat ini.
- **Kualitas suara bergantung pada TTS perangkat.** Lumina PDF memilih suara
  terbaik yang tersedia, tetapi tidak membawa mesin suara sendiri. Pada perangkat
  tanpa Google TTS (atau tanpa paket suara bahasa Indonesia), kualitas suara
  mengikuti apa yang terpasang. Aplikasi menyediakan tautan ke pengaturan TTS dan
  pemasang data suara.
- **PDF terenkripsi/kata sandi** belum dibuka. Aplikasi melaporkan galat dengan
  sopan alih-alih menampilkan halaman kosong.

---

## Privasi

**Semua pemrosesan terjadi di perangkat.** Tidak ada akun, tidak ada unggahan,
tidak ada analitik, tidak ada pelacakan, tidak ada iklan.

- Berkas PDF dibaca lewat URI yang Anda berikan; berkasnya tidak pernah disalin
  atau diubah.
- Teks diekstrak di memori dan hanya disimpan selama sesi (maksimal 2 dokumen
  di cache).
- Pustaka, penanda buku, dan pengaturan disimpan sebagai berkas JSON kecil dan
  `SharedPreferences` di penyimpanan privat aplikasi.
- Satu-satunya lalu lintas jaringan yang mungkin terjadi berasal dari **mesin TTS
  Google** bila Anda memilih suara online. Itu di luar kendali aplikasi dan bisa
  dihindari dengan memilih suara offline.

Aplikasi **tidak** meminta izin internet sendiri.

---

## Membuka di Android Studio

### Yang dibutuhkan

| Komponen | Versi |
| --- | --- |
| Android Studio | Ladybug (2024.2.1) atau lebih baru |
| JDK | 17 |
| Android SDK Platform | 35 |
| Gradle | 8.9 (sudah dipatok oleh wrapper) |
| Android Gradle Plugin | 8.7.3 |

### Langkah

1. Buka Android Studio → **File → Open**.
2. Pilih folder `pdfclone` (folder yang berisi `settings.gradle.kts`).
3. Tunggu sinkronisasi Gradle selesai. Semua dependensi diambil dari Google
   Maven dan Maven Central; tidak ada repositori lain.
4. Pastikan **SDK Platform 35** terpasang
   (**Settings → Languages & Frameworks → Android SDK**).
5. Tekan **Run ▶** untuk memasang ke perangkat/emulator.

### Catatan build

- `minSdk` 24, `targetSdk` 35, `compileSdk` 35.
- Build `release` sudah dikonfigurasi agar bisa dipasang langsung, tetapi
  **ditandatangani dengan kunci debug**. Ganti `signingConfig` di
  `app/build.gradle.kts` dengan keystore Anda sendiri sebelum memublikasikan.
- R8 dan `shrinkResources` aktif pada build release; aturan keep untuk PDFBox,
  serializer, dan konstruktor ViewModel ada di `app/proguard-rules.pro`.

---

## Membangun lewat GitHub Actions

Workflow sudah tersedia di `.github/workflows/build.yml`.

1. Buat repositori baru di GitHub.
2. Dorong seluruh isi folder `pdfclone` ke repositori tersebut:

   ```sh
   git init
   git add .
   git commit -m "Lumina PDF"
   git branch -M main
   git remote add origin https://github.com/<akun>/<repo>.git
   git push -u origin main
   ```

3. Buka tab **Actions** → workflow **APK** berjalan otomatis.
4. Setelah selesai, unduh artefak **lumina-pdf-apk** dari halaman run.
   Di dalamnya ada `app-debug.apk` dan `app-release.apk`.

Workflow juga bisa dijalankan manual lewat **Run workflow**
(`workflow_dispatch`). Bila unit test gagal, laporan tes diunggah sebagai
artefak **lumina-pdf-test-report**.

---

## Menjalankan unit test

Tes berjalan murni di JVM, tanpa emulator:

```sh
./gradlew testDebugUnitTest
```

Yang diuji:

| Berkas | Cakupan |
| --- | --- |
| `app/src/test/java/com/lumina/pdfai/ai/TextNormalizerTest.kt` | Perbaikan hyphenation, soft hyphen, nomor halaman, angka Romawi, dot leader, URL/surel, ligatur, CRLF, deteksi header berulang, perluasan singkatan, `snippet` |
| `app/src/test/java/com/lumina/pdfai/ai/ScriptBuilderTest.kt` | Pemecahan kalimat (singkatan, desimal, elipsis, tanda kutip), struktur chunk, `pageOffsets`, dedupe judul bab, jeda paragraf, pemecahan kalimat panjang |

---

## Struktur proyek

```
pdfclone/
├── .github/workflows/build.yml     # CI: test + assembleDebug + assembleRelease
├── settings.gradle.kts            # rootProject "LuminaPdfAI", modul :app
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml         # katalog versi
│   └── wrapper/
├── gradlew, gradlew.bat
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/lumina/pdfai/
        │   │   ├── LuminaApp.kt           # Application
        │   │   ├── MainActivity.kt        # intent, splash, volume key
        │   │   ├── ai/                    # mesin narasi
        │   │   │   ├── TextNormalizer.kt  # perbaikan teks PDF
        │   │   │   ├── LanguageDetector.kt
        │   │   │   ├── SpeechScript.kt    # ScriptBuilder + model chunk
        │   │   │   ├── TtsEngine.kt       # pembungkus TextToSpeech
        │   │   │   └── NarrationEngine.kt # orkestrasi + jeda + timer
        │   │   ├── data/                  # pustaka, pengaturan, bookmark, scan
        │   │   ├── pdf/                   # PdfRenderer + PDFBox
        │   │   ├── di/Services.kt         # service locator sederhana
        │   │   ├── ui/                    # Compose: home, reader, ai, settings
        │   │   ├── util/                  # Format, VolumeKeyBus, ShareUtils
        │   │   └── ...
        │   └── res/                       # strings, colors, themes, drawable
        └── test/java/com/lumina/pdfai/ai/ # unit test JVM
```

---

## Tumpukan teknologi

| Lapisan | Pilihan |
| --- | --- |
| Bahasa | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.10.01), Material 3 |
| Navigasi | `navigation-compose` 2.8.4 |
| Render PDF | `android.graphics.pdf.PdfRenderer` (platform) |
| Ekstraksi teks | `com.tom-roush:pdfbox-android` 2.0.27.0 |
| Serialisasi | `kotlinx-serialization-json` 1.7.3 |
| Penyimpanan | `SharedPreferences` + JSON di penyimpanan privat |
| Suara | `android.speech.tts.TextToSpeech` |
| DI | Service locator manual (tanpa Hilt/KSP/kapt, agar CI cepat) |

Tidak memakai Pdfium, tidak memakai library PDF komersial, tidak memakai
layanan awan.

---

## Kredit

- Teks PDF diekstrak dengan [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android)
  (Apache License 2.0).
- Ikon dari `androidx.compose.material:material-icons-extended` (Apache License 2.0).
- Semua kode aplikasi ditulis untuk proyek ini.

Aplikasi ini tidak berafiliasi dengan, tidak disponsori oleh, dan tidak memuat
kode dari WPS Office atau produk pihak ketiga lainnya.
