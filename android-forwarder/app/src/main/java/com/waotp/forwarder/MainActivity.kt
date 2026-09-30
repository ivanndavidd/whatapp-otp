package com.waotp.forwarder

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.waotp.scanner.QRCodeScanner

/**
 * Layar konfigurasi aplikasi.
 *
 * Digunakan untuk mengatur:
 * - Server URL
 * - Nomor telepon SIM 1 dan SIM 2
 * - Jenis akun WhatsApp pada masing-masing SIM
 * - Device ID
 * - Private Key
 *
 * Layar ini juga menyediakan tombol untuk membuka pengaturan
 * Notification Access agar NotificationListenerService dapat
 * membaca notifikasi.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*
         * SharedPreferences digunakan untuk menyimpan konfigurasi
         * aplikasi agar tetap tersedia setelah aplikasi ditutup
         * atau device melakukan restart.
         *
         * PREFS yang digunakan sama dengan PREFS milik
         * OtpNotificationListener.
         */
        val prefs = getSharedPreferences(
            OtpNotificationListener.PREFS,
            MODE_PRIVATE
        )

        // Input Server URL
        val urlInput = findViewById<EditText>(
            R.id.serverUrl
        )

        // Konfigurasi SIM 1
        val phone1Input = findViewById<EditText>(
            R.id.phone1
        )

        val waGroup1 = findViewById<RadioGroup>(
            R.id.waGroup1
        )

        // Konfigurasi SIM 2
        val phone2Input = findViewById<EditText>(
            R.id.phone2
        )

        val waGroup2 = findViewById<RadioGroup>(
            R.id.waGroup2
        )

        // Identitas device yang sudah didaftarkan di website OTP
        val deviceIdInput = findViewById<EditText>(
            R.id.deviceId
        )

        /*
         * Private key digunakan oleh device untuk membuat
         * digital signature sebelum mengirim notifikasi ke server.
         *
         * Private key tidak dikirim ke server.
         */
        val privateKeyInput = findViewById<EditText>(
            R.id.privateKey
        )

        // Tombol untuk membaca Device ID dan Private Key dari QR Code
        val scanPrivateKeyButton = findViewById<Button>(
            R.id.scanPrivateKey
        )

        /*
         * Perangkat yang sudah terpasang mungkin masih menyimpan
         * Server URL lama yang berakhiran `/ingest`.
         *
         * Nilai tersebut disesuaikan ke path baru ketika layar
         * konfigurasi dibuka, kemudian hasilnya langsung disimpan.
         *
         * Dengan begitu, URL yang ditampilkan pada layar dan URL
         * yang benar-benar digunakan oleh aplikasi tetap konsisten.
         */
        val storedUrl = prefs.getString(
            "server_url",
            ""
        ) ?: ""

        val resolvedUrl =
            OtpNotificationListener.resolveIngestUrl(
                storedUrl
            )

        if (resolvedUrl != storedUrl) {

            /*
             * Simpan URL yang sudah disesuaikan agar perangkat
             * tidak perlu melakukan penyesuaian yang sama setiap kali
             * aplikasi dibuka.
             */
            prefs.edit()
                .putString(
                    "server_url",
                    resolvedUrl
                )
                .apply()

            Toast.makeText(
                this,
                "Server URL disesuaikan ke ${OtpNotificationListener.INGEST_PATH}",
                Toast.LENGTH_LONG
            ).show()
        }

        // Tampilkan konfigurasi yang tersimpan ke dalam form
        urlInput.setText(resolvedUrl)

        phone1Input.setText(
            prefs.getString(
                "phone1",
                ""
            )
        )

        phone2Input.setText(
            prefs.getString(
                "phone2",
                ""
            )
        )

        deviceIdInput.setText(
            prefs.getString(
                "device_id",
                ""
            )
        )

        privateKeyInput.setText(
            prefs.getString(
                "private_key",
                ""
            )
        )

        /*
         * Kembalikan pilihan jenis WhatsApp SIM 1
         * berdasarkan konfigurasi yang sebelumnya disimpan.
         */
        setWaRadioSelection(
            waGroup1,
            prefs.getString(
                "wa_type1",
                "none"
            ),
            isSim1 = true
        )

        /*
         * Kembalikan pilihan jenis WhatsApp SIM 2
         * berdasarkan konfigurasi yang sebelumnya disimpan.
         */
        setWaRadioSelection(
            waGroup2,
            prefs.getString(
                "wa_type2",
                "none"
            ),
            isSim1 = false
        )

        // =========================================================
        // PEMBACAAN QR CODE
        // =========================================================

        scanPrivateKeyButton.setOnClickListener {

            /*
             * QR Code yang dibuat oleh website menggunakan format:
             *
             * deviceId|privateKey
             *
             * Contoh:
             *
             * ANDROID-001|MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEH...
             *
             * Device ID dan private key digabungkan supaya operator
             * tidak perlu memasukkan Device ID secara manual.
             */
            QRCodeScanner.scan(
                activity = this,

                onSuccess = { scannedText ->

                    /*
                     * Pisahkan isi QR menggunakan karakter `|`.
                     *
                     * `|` digunakan sebagai separator karena karakter
                     * tersebut tidak termasuk dalam alphabet Base64
                     * standar yang digunakan untuk private key.
                     *
                     * limit = 2 berarti String hanya dibagi menjadi
                     * maksimal dua bagian:
                     *
                     * bagian pertama -> Device ID
                     * bagian kedua  -> Private Key
                     *
                     * Dengan begitu, seluruh isi setelah separator
                     * pertama tetap dianggap sebagai private key.
                     */
                    val parts = scannedText.split(
                        "|",
                        limit = 2
                    )

                    /*
                     * QR dianggap tidak valid apabila tidak menghasilkan
                     * tepat dua bagian.
                     */
                    if (parts.size != 2) {

                        Toast.makeText(
                            this,
                            "QR Code tidak valid. Format harus Device ID|Private Key.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@scan
                    }

                    /*
                     * Ambil Device ID dari bagian pertama.
                     */
                    val scannedDeviceId =
                        parts[0].trim()

                    /*
                     * Ambil private key dari bagian kedua.
                     */
                    val scannedPrivateKey =
                        parts[1].trim()

                    /*
                     * Pastikan kedua nilai benar-benar memiliki isi.
                     */
                    if (
                        scannedDeviceId.isEmpty() ||
                        scannedPrivateKey.isEmpty()
                    ) {

                        Toast.makeText(
                            this,
                            "QR Code tidak valid. Device ID atau Private Key kosong.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@scan
                    }

                    /*
                     * Setelah QR berhasil dibaca, masukkan Device ID
                     * dan Private Key secara otomatis ke form.
                     */
                    deviceIdInput.setText(
                        scannedDeviceId
                    )

                    privateKeyInput.setText(
                        scannedPrivateKey
                    )

                    Toast.makeText(
                        this,
                        "Device ID dan Private Key berhasil dibaca dari QR Code.",
                        Toast.LENGTH_SHORT
                    ).show()
                },

                onError = { exception ->

                    /*
                     * Tampilkan error apabila scanner gagal membaca
                     * atau memproses QR Code.
                     */
                    Toast.makeText(
                        this,
                        "Gagal membaca QR Code: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                },

                onCanceled = {

                    /*
                     * User menutup scanner tanpa menyelesaikan
                     * proses scanning.
                     *
                     * Tidak perlu melakukan apa-apa.
                     */
                }
            )
        }

        // =========================================================
        // MENYIMPAN KONFIGURASI
        // =========================================================

        findViewById<Button>(
            R.id.save
        ).setOnClickListener {

            /*
             * Ambil jenis akun WhatsApp yang dipilih untuk
             * masing-masing SIM.
             */
            val waType1 = getWaRadioValue(
                waGroup1
            )

            val waType2 = getWaRadioValue(
                waGroup2
            )

            /*
             * Satu jenis akun WhatsApp tidak boleh digunakan
             * pada kedua SIM sekaligus.
             *
             * Contoh yang tidak diperbolehkan:
             *
             * SIM 1 -> WhatsApp Personal
             * SIM 2 -> WhatsApp Personal
             */
            if (
                waType1 != "none" &&
                waType1 == waType2
            ) {

                val errorMsg =
                    if (waType1 == "personal") {

                        "Error: Kedua SIM tidak bisa diset ke WA Biasa secara bersamaan."

                    } else {

                        "Error: Kedua SIM tidak bisa diset ke WA Business secara bersamaan."
                    }

                Toast.makeText(
                    this,
                    errorMsg,
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            /*
             * Ambil Device ID dari input.
             *
             * Device ID harus sama dengan ID device yang
             * sudah didaftarkan pada website OTP.
             */
            val deviceId = deviceIdInput.text
                .toString()
                .trim()

            if (deviceId.isEmpty()) {

                deviceIdInput.error =
                    "Device ID wajib diisi"

                deviceIdInput.requestFocus()

                Toast.makeText(
                    this,
                    "Error: Device ID wajib diisi.",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            /*
             * Ambil private key dari input.
             *
             * Private key diperoleh dari QR Code yang dibuat
             * oleh website OTP.
             */
            val privateKey = privateKeyInput.text
                .toString()
                .trim()

            if (privateKey.isEmpty()) {

                privateKeyInput.error =
                    "Private Key wajib diisi"

                privateKeyInput.requestFocus()

                Toast.makeText(
                    this,
                    "Error: Private Key wajib diisi.",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            /*
             * Simpan seluruh konfigurasi ke SharedPreferences.
             *
             * Konfigurasi ini nantinya dibaca kembali oleh
             * OtpNotificationListener ketika ada notifikasi baru.
             */
            prefs.edit()

                // Server endpoint yang digunakan untuk mengirim OTP
                .putString(
                    "server_url",
                    OtpNotificationListener.resolveIngestUrl(
                        urlInput.text.toString()
                    )
                )

                // Nomor telepon yang digunakan pada SIM 1
                .putString(
                    "phone1",
                    phone1Input.text
                        .toString()
                        .trim()
                )

                // Jenis akun WhatsApp pada SIM 1
                .putString(
                    "wa_type1",
                    waType1
                )

                // Nomor telepon yang digunakan pada SIM 2
                .putString(
                    "phone2",
                    phone2Input.text
                        .toString()
                        .trim()
                )

                // Jenis akun WhatsApp pada SIM 2
                .putString(
                    "wa_type2",
                    waType2
                )

                /*
                 * Device ID digunakan oleh NotificationListener
                 * sebagai identitas device ketika mengirim request.
                 */
                .putString(
                    "device_id",
                    deviceId
                )

                /*
                 * Private key digunakan oleh NotificationListener
                 * untuk membuat ECDSA signature.
                 *
                 * Private key hanya digunakan secara lokal.
                 */
                .putString(
                    "private_key",
                    privateKey
                )

                .apply()

            Toast.makeText(
                this,
                "Saved",
                Toast.LENGTH_SHORT
            ).show()
        }

        // =========================================================
        // NOTIFICATION ACCESS
        // =========================================================

        findViewById<Button>(
            R.id.grantAccess
        ).setOnClickListener {

            /*
             * Buka halaman pengaturan Android untuk memberikan
             * izin Notification Access kepada aplikasi.
             *
             * Tanpa izin ini, NotificationListenerService tidak
             * dapat menerima callback ketika notifikasi masuk.
             */
            startActivity(
                Intent(
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                )
            )
        }
    }

    /**
     * Mengatur RadioButton WhatsApp berdasarkan nilai konfigurasi
     * yang tersimpan di SharedPreferences.
     *
     * Nilai yang digunakan:
     * - "personal" -> WhatsApp biasa
     * - "business" -> WhatsApp Business
     * - nilai lainnya -> None
     */
    private fun setWaRadioSelection(
        group: RadioGroup,
        value: String?,
        isSim1: Boolean
    ) {

        if (isSim1) {

            when (value) {

                "personal" ->
                    group.check(
                        R.id.waPersonal1
                    )

                "business" ->
                    group.check(
                        R.id.waBiz1
                    )

                else ->
                    group.check(
                        R.id.waNone1
                    )
            }

        } else {

            when (value) {

                "personal" ->
                    group.check(
                        R.id.waPersonal2
                    )

                "business" ->
                    group.check(
                        R.id.waBiz2
                    )

                else ->
                    group.check(
                        R.id.waNone2
                    )
            }
        }
    }

    /**
     * Mengubah pilihan RadioButton menjadi nilai String
     * yang dapat disimpan ke SharedPreferences.
     *
     * Return value:
     * - "personal" -> WhatsApp biasa
     * - "business" -> WhatsApp Business
     * - "none" -> Tidak menggunakan WhatsApp pada SIM tersebut
     */
    private fun getWaRadioValue(
        group: RadioGroup
    ): String {

        return when (group.checkedRadioButtonId) {

            R.id.waPersonal1,
            R.id.waPersonal2 ->
                "personal"

            R.id.waBiz1,
            R.id.waBiz2 ->
                "business"

            else ->
                "none"
        }
    }
}