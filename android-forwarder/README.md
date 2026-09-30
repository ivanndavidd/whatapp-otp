# WA OTP Forwarder (Android)

Aplikasi Android yang membaca notifikasi WhatsApp, WhatsApp Business, dan SMS pada **primary device**, kemudian meneruskan isinya ke Ingest Server.

Authentication antara Android Forwarder dan Ingest Server menggunakan **ECDSA P-256 digital signature**. Setiap device memiliki pasangan private key dan public key sendiri.

## Arsitektur

```text
WhatsApp / SMS
      │
      ▼
Primary Android Device
      │
      │ NotificationListenerService
      ▼
WA OTP Forwarder
      │
      │ deviceId + notification data + ECDSA signature
      ▼
Ingest Server
      │
      │ Public key dari database
      │
      ▼
ECDSA Signature Verification
      │
      ├── Valid   → Process notification
      │
      └── Invalid → Reject request
```

Private key hanya disimpan pada Android device dan **tidak dikirim ke server**.

Backend hanya menyimpan:

```text
deviceId
publicKey
```

## Persyaratan

* Android 7.0 (API 24) atau lebih baru.
* WhatsApp atau WhatsApp Business terpasang dan notifikasinya aktif.
* SMS notification dapat dibaca oleh aplikasi jika digunakan.
* HP dapat mengakses Ingest Server melalui jaringan.
* JDK 17 atau lebih baru untuk build (JDK 21 sudah teruji).
* Android SDK dan ADB tersedia jika melakukan instalasi melalui USB.

---

## Build APK

Masuk ke folder project dan arahkan sesi PowerShell ke JDK 21:

```powershell
cd D:\wa-otp-receiver\android-forwarder

$env:JAVA_HOME='C:\Program Files\Java\jdk-21'

$env:Path="$env:JAVA_HOME\bin;$env:Path"

java -version
```

Pastikan file `local.properties` menunjuk ke lokasi Android SDK.

Contoh:

```properties
sdk.dir=C\:\\Users\\ivan.david\\AppData\\Local\\Android\\Sdk
```

File `local.properties` bersifat lokal dan tidak disimpan di GitHub.

Kemudian jalankan clean build dan lint:

```powershell
.\gradlew.bat clean assembleDebug

.\gradlew.bat lintDebug
```

Build berhasil apabila terminal menampilkan:

```text
BUILD SUCCESSFUL
```

APK akan dibuat di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Laporan lint tersedia di:

```text
app/build/reports/lint-results-debug.html
```

---

## Instalasi di HP Fisik

Aktifkan **Developer Options** dan **USB Debugging** pada HP.

Sambungkan HP menggunakan USB, kemudian jalankan:

```powershell
adb devices
```

Pastikan HP muncul sebagai:

```text
List of devices attached
XXXXXXXX    device
```

Jika muncul:

```text
XXXXXXXX    unauthorized
```

unlock HP dan terima dialog **Allow USB debugging** pada HP.

Setelah device sudah berstatus `device`, install APK:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK juga dapat disalin ke HP dan dibuka langsung. Android mungkin meminta izin untuk memasang aplikasi dari sumber tersebut.

---

# Konfigurasi Device

Setiap Android device menggunakan:

```text
Device ID
Private Key
```

Backend memiliki pasangan:

```text
Device ID
Public Key
```

Private key digunakan Android untuk membuat digital signature, sedangkan backend menggunakan public key untuk melakukan verifikasi.

## 1. Buat Device pada Webportal

Buat device melalui halaman **Manage Device** pada OTP Webportal.

Setelah device dibuat, backend akan menghasilkan:

```text
Device ID
Private Key
```

Private key hanya diberikan saat proses pembuatan device dan tidak disimpan sebagai private key yang dapat diambil kembali oleh backend.

Simpan informasi tersebut dengan aman.

## 2. Provision Device melalui QR

Pada Android Forwarder, buka aplikasi dan gunakan fitur **scan QR code**.

QR code harus berisi format:

```text
deviceId|privateKey
```

Contoh:

```text
device-001|MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHA0IAB...
```

Aplikasi akan memisahkan:

```text
Device ID
    ↓
device-001

Private Key
    ↓
MIGHAgEAMBMGByqGSM49AgEG...
```

Kemudian keduanya disimpan pada aplikasi Android.

Private key harus berupa **Base64-encoded PKCS#8 EC private key**.

Untuk ECDSA P-256, private scalar-nya berukuran 256-bit (32 byte), tetapi Base64 private key **bukan berarti harus memiliki panjang 64 karakter** karena yang di-encode adalah keseluruhan struktur PKCS#8.

## 3. Isi Server URL

Isi **Server URL** dengan endpoint ingest yang dapat dijangkau HP.

Contoh:

```text
https://otp.example.com/dangerously-skip-login/ingest
```

atau untuk server lokal:

```text
http://192.168.1.10:3000/dangerously-skip-login/ingest
```

Jika menggunakan HP fisik, **jangan gunakan `10.0.2.2`**.

Alamat `10.0.2.2` digunakan oleh Android Emulator/AVD untuk mengakses host machine.

Untuk HP fisik, gunakan IP address komputer/server yang dapat dijangkau melalui jaringan, misalnya:

```text
192.168.1.10
```

Path berikut wajib digunakan:

```text
/dangerously-skip-login/ingest
```

Endpoint tersebut merupakan endpoint khusus yang dapat diakses tanpa sesi login gateway. Authentication request tetap dilakukan menggunakan **ECDSA signature** pada level Ingest Server.

## 4. Isi Nomor

Isi **Nomor** dengan nomor WhatsApp yang digunakan pada primary device.

Contoh:

```text
628xxxxxxxxxx
```

Nomor ini akan menjadi bagian dari data yang ditandatangani oleh Android.

## 5. Simpan Konfigurasi

Setelah:

* Server URL
* Nomor
* Device ID
* Private Key

sudah dikonfigurasi, tekan **Simpan**.

Aplikasi akan menggunakan private key tersebut untuk menandatangani setiap notification request.

---

# Authentication Flow

Setiap notification yang dikirim Android memiliki data:

```text
deviceId
phone
title
text
packageName
postedAt
```

Android membuat **canonical string** dengan urutan yang harus sama persis dengan backend:

```text
deviceId|phone|title|text|packageName|postedAt
```

Contoh:

```text
device-001|628123456789|WhatsApp|Your OTP is 123456|com.whatsapp|1759123456789
```

String tersebut kemudian ditandatangani menggunakan:

```text
SHA256withECDSA
```

dengan private key device.

Request yang dikirim ke server memiliki struktur seperti (Contoh):

```json
{
  "deviceId": "device-001",
  "phone": "628123456789",
  "title": "WhatsApp",
  "text": "Your OTP is 123456",
  "packageName": "com.whatsapp",
  "postedAt": 1759123456789,
  "signature": "MEUCIQD..."
}
```

Private key **tidak pernah dimasukkan ke request**.

---

# Permission Notification Access

Setelah konfigurasi selesai:

1. Buka aplikasi **WA OTP Forwarder**.
2. Tekan **Beri Izin Notification Access**.
3. Android akan membuka halaman Notification Access.
4. Cari **WA OTP Forwarder**.
5. Aktifkan permission tersebut.
6. Konfirmasi jika Android menampilkan dialog.
7. Kembali ke aplikasi.

Notification Access diperlukan karena aplikasi menggunakan `NotificationListenerService` untuk membaca notification yang masuk.

Pastikan notifikasi WhatsApp/WhatsApp Business juga aktif.

---

# Pengujian

Setelah konfigurasi selesai:

1. Pastikan server dapat dijangkau dari HP.
2. Pastikan Notification Access sudah aktif.
3. Pastikan WhatsApp/WhatsApp Business mengirimkan notification.
4. Kirim OTP uji ke nomor WhatsApp tersebut.
5. Pastikan notification muncul pada primary device.
6. Periksa log aplikasi Android.
7. Periksa log Ingest Server.

---

# Keamanan

* Private key tidak dikirim ke Ingest Server.
* Backend hanya menyimpan public key.
* Setiap device memiliki pasangan key sendiri.
* Signature dibuat menggunakan ECDSA P-256 dengan SHA-256.
* Jangan memasukkan private key ke source code.
* Jangan commit private key ke GitHub.
* Jangan membagikan QR provisioning yang berisi private key.
* Gunakan HTTPS jika Ingest Server diakses melalui internet.

Untuk pengujian jaringan lokal, HTTP masih dapat digunakan, tetapi traffic antara Android dan server tidak terenkripsi. Dalam kondisi tersebut, isi notification dan signature dapat diamati oleh pihak lain yang dapat mengakses jaringan tersebut.

Untuk deployment production atau server yang dapat diakses melalui internet, gunakan HTTPS.

---