# OTP Receiver (Android App)

Aplikasi Android berbasis `NotificationListenerService` yang bertugas menangkap notifikasi OTP yang masuk ke perangkat Android, baik dari WhatsApp, WhatsApp Business, maupun SMS, lalu meneruskannya ke Ingest Server terpusat.

## Latar Belakang & Arsitektur

WhatsApp dan SMS hanya menampilkan pesan OTP tertentu secara lengkap pada **primary device**. Karena itu, aplikasi ini menggunakan satu jalur: membaca notifikasi WhatsApp/SMS pada perangkat Android utama dan meneruskannya ke Ingest Server.

Solusinya adalah menjadikan HP atau emulator Android yang kita kontrol sebagai **primary device**, lalu membaca OTP dari notifikasi perangkat tersebut.

```text
┌─ Android device (primary device) ────────────────┐
│                                                  │
│  WhatsApp / WhatsApp Business / SMS              │
│                    │                             │
│                    ▼                             │
│          WA OTP Forwarder                        │
│          NotificationListenerService             │
│                    │                             │
│                    │  Sign request               │
│                    │  with device private key    │
│                    ▼                             │
│              POST /ingest                        │
└────────────────────┼─────────────────────────────┘
                     │
                     ▼
              OTP Webportal
              (Repository Terpisah)
                     │
                     ├── Simpan DB
                     └── Kirim Email Massal
```

Repository:
* [OTP Web Portal](https://github.com/NS2006/otp-webportal)

### Authentication
Setiap Android device memiliki pasangan kunci kriptografi:

```text
Device
├── Device ID
├── Private Key  ← disimpan di Android device
│
└── Public Key   ← disimpan di OTP Webportal
```

Sistem menggunakan **ECDSA P-256 dengan SHA-256** untuk menandatangani request.
Private key **tidak pernah dikirim ke server** dan tidak disimpan oleh backend.
Backend hanya menyimpan public key yang digunakan untuk memverifikasi signature dari setiap request. Dengan demikian, setiap device mempunyai identitas kriptografisnya sendiri.

---

## Endpoint Ingest
Endpoint ingest yang digunakan adalah:

```text
POST /dangerously-skip-login/ingest
```

Webportal berada di belakang Apps Gateway, dan prefix:
```text
/dangerously-skip-login/**
```
adalah path yang dapat diakses tanpa login gateway.

Forwarder tidak memegang sesi login gateway. Authentication request dilakukan menggunakan **digital signature dari private key device**.

Server masih menerima endpoint `/ingest` untuk sementara untuk kompatibilitas, tetapi endpoint tersebut **tidak digunakan untuk pemasangan baru**.

---

# Setup Forwarder App

## Persyaratan Build
* JDK 17 atau lebih baru
* JDK 21 sudah teruji
* Android SDK Platform 34
* Android SDK Build Tools
* Android 7.0 (API 24) atau lebih baru untuk perangkat tujuan

Build APK debug dari PowerShell:

```powershell
cd android-forwarder

$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

java -version
```

Pastikan file `local.properties` menunjuk ke lokasi Android SDK. Contoh:

```properties
sdk.dir=C\:\\Users\\ivan.david\\AppData\\Local\\Android\\Sdk
```

File tersebut bersifat lokal dan tidak disimpan di GitHub. Kemudian jalankan
clean build dan lint:
```powershell
.\gradlew.bat clean assembleDebug
.\gradlew.bat lintDebug
```

APK akan dibuat di:

```text
android-forwarder/app/build/outputs/apk/debug/app-debug.apk
```
Install ke HP yang sudah mengaktifkan USB debugging:

```powershell
adb devices

adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

# Konfigurasi Device

Setelah aplikasi terpasang:

1. Buka aplikasi.
2. Isi **Server URL** dengan endpoint ingest webportal.
3. Masukkan **Device ID** dan **Private Key** melalui QR Code provisioning.
4. Tekan **Simpan**.
5. Tekan **Beri Izin Notification Access**, lalu aktifkan `WA OTP Forwarder`.
6. Pastikan notifikasi WhatsApp/SMS dan aktivitas background tidak diblokir oleh perangkat.

## Server URL
Server URL harus menunjuk ke endpoint:

```text
/dangerously-skip-login/ingest
```

Contoh HTTP untuk testing di jaringan lokal:

```text
http://192.168.1.10:3000/dangerously-skip-login/ingest
```

Contoh HTTPS untuk server produksi:

```text
https://otp.example.com/dangerously-skip-login/ingest
```

Berkat konfigurasi Network Security, HTTP dapat digunakan untuk testing di jaringan lokal.

> Untuk production, gunakan HTTPS.

### Android Emulator

Jangan menggunakan:

```text
http://10.0.2.2:3000
```

pada HP fisik.

`10.0.2.2` adalah alamat khusus Android Emulator AVD untuk mengakses `localhost` komputer host.

---

# Device Provisioning

Setiap device harus didaftarkan terlebih dahulu pada OTP Webportal.

Saat device dibuat, backend akan:

1. Menerima `Device ID`.
2. Membuat pasangan ECDSA P-256 key pair.
3. Menyimpan **public key** ke database.
4. Mengembalikan **private key** satu kali kepada admin.
5. Private key ditampilkan pada webportal dalam modal satu kali.
6. QR Code dibuat dari kombinasi:

```text
Device ID|Private Key
```

Contoh:
```text
ANDROID-001|MIGHAgEAMBMGByqGSM49Ag...
```

Karakter `|` digunakan sebagai separator karena tidak termasuk dalam standard Base64 yang digunakan untuk private key.

## Provisioning ke Android
Pada aplikasi Android:

1. Tekan **Scan QR Code**.
2. Scan QR Code yang ditampilkan oleh OTP Webportal.
3. Aplikasi akan membaca:
```text
Device ID|Private Key
```

4. Device ID dan Private Key akan otomatis dimasukkan ke masing-masing field.
5. Tekan **Simpan**.

Private key hanya ditampilkan oleh webportal saat provisioning device dibuat.

Jika private key tersebut hilang, private key **tidak dapat diambil kembali dari backend**, karena backend hanya menyimpan public key.

Untuk mendapatkan credential baru, buat/provision device baru sesuai prosedur webportal.

---

# Request Authentication

Setiap OTP notification yang diteruskan oleh Android akan ditandatangani menggunakan private key device.

Data yang ditandatangani dibentuk dari:
```text
deviceId|phone|title|text|packageName|postedAt
```

Contoh:
```text
ANDROID-001|628123456789|WhatsApp|Your OTP is 123456|com.whatsapp|1780000000000
```

Data tersebut kemudian ditandatangani menggunakan:
```text
ECDSA P-256
SHA-256
```

Hasil signature dikirim sebagai Base64.

Request ke server berbentuk (Contoh):
```json
{
  "deviceId": "ANDROID-001",
  "phone": "628123456789",
  "title": "WhatsApp",
  "text": "Your OTP is 123456",
  "packageName": "com.whatsapp",
  "postedAt": 1780000000000,
  "signature": "MEUCIQ..."
}
```

Server kemudian:
1. Mencari device berdasarkan `deviceId`.
2. Mengambil public key device dari database.
3. Membentuk kembali canonical data yang sama.
4. Memverifikasi signature menggunakan public key.
5. Jika signature valid, request dapat diproses sebagai request dari device tersebut.

Private key **tidak pernah dimasukkan ke dalam request**.
---

# Security Model
Credential device dibagi menjadi dua bagian:

| Credential  | Lokasi              | Fungsi                  |
| ----------- | ------------------- | ----------------------- |
| Device ID   | Android + Webportal | Identitas device        |
| Private Key | Android device      | Menandatangani request  |
| Public Key  | Webportal database  | Memverifikasi signature |

Backend **tidak menyimpan private key**.

Contoh:
```text
Android
────────────────────────────
Device ID
Private Key
      │
      │ ECDSA Signature
      ▼
POST /dangerously-skip-login/ingest
      │
      ▼
OTP Webportal
────────────────────────────
Device ID
Public Key
      │
      │ Verify Signature
      ▼
Accept / Reject request
```

---

# Notification Access
Setelah konfigurasi selesai:

1. Buka aplikasi.
2. Tekan **Beri Izin Notification Access**.
3. Pilih **WA OTP Forwarder**.
4. Aktifkan notification access.
5. Pastikan WhatsApp/SMS dapat menampilkan notifikasi OTP.

Beberapa vendor Android dapat membatasi aplikasi yang berjalan di background.

Pastikan battery optimization atau background restriction tidak memblokir aplikasi.

---

# Supported Applications

Saat ini forwarder memproses notification dari package berikut:

```text
com.whatsapp
com.whatsapp.w4b
com.android.mms
com.google.android.apps.messaging
com.samsung.android.messaging
```

Jika aplikasi SMS pada perangkat lain menggunakan package berbeda, package tersebut perlu ditambahkan ke `ALLOWED_PACKAGES`.

---

# Scale ke Banyak Nomor

Untuk production:

```text
1 nomor WhatsApp = 1 Android instance
```

Android tidak dapat menjalankan dua akun WhatsApp independen dalam satu instance dengan cara yang sama seperti dua device terpisah.

Untuk skala besar, misalnya puluhan nomor, gunakan **redroid** (Android di Docker) pada server Linux.

Arsitektur:

```text
Linux Server
│
├── redroid instance 1
│   └── WhatsApp + OTP Forwarder
│
├── redroid instance 2
│   └── WhatsApp + OTP Forwarder
│
├── redroid instance 3
│   └── WhatsApp + OTP Forwarder
│
└── ...
        │
        ▼
   OTP Webportal
```

Aplikasi forwarder dan ingest server tidak perlu diubah secara fundamental. Setiap Android instance tetap mempunyai Device ID dan key pair masing-masing.

Lihat:

```text
docs/redroid-server-setup.md
```

untuk konfigurasi redroid.

---

# Repository Structure

```text
whatapp-otp/

├── android-forwarder/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── OtpNotificationListener.kt
│   │   │   │   └── scanner/
│   │   │   │       └── QRCodeScanner.kt
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   ├── values/
│   │   │   │   └── xml/
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   └── build.gradle
│   │
│   └── README.md
│
├── docs/
│   └── redroid-server-setup.md
│
└── README.md
```

---

# Development Notes

Untuk testing lokal, HTTP dapat digunakan pada jaringan lokal.

Untuk production, gunakan HTTPS.

Jika private key hilang atau terekspos, device tersebut sebaiknya dianggap tidak lagi terpercaya dan dilakukan provisioning device baru.