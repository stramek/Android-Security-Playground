package com.example.androidsecurity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import java.io.*

class MainActivity : AppCompatActivity() {

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            PREFS_FILENAME,
            masterKeyAlias,
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val file by lazy { File(filesDir, ENCRYPTED_FILE_NAME) }
    private val encryptedFile by lazy {
        EncryptedFile.Builder(
            file,
            applicationContext,
            masterKeyAlias,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupListeners()
    }

    private fun setupListeners() {
        securePrefsSave.setOnClickListener { encryptPrefsString(securePrefsInput.text.toString()) }
        securePrefsLoad.setOnClickListener {
            result.text = decryptPrefsString() ?: getString(R.string.no_saved_value)
        }
        fileDownload.setOnClickListener { downloadAndEncryptFile() }
        fileDelete.setOnClickListener { deleteFile() }
        fileLoad.setOnClickListener { readFile { file.inputStream() } }
        fileDecrypt.setOnClickListener { readFile { encryptedFile.openFileInput() } }
    }

    private fun encryptPrefsString(value: String) {
        encryptedPrefs.edit {
            putString(ENC_KEY, value)
            apply()
        }
        result.text = getString(R.string.value_encrypted)
    }

    private fun decryptPrefsString(): String? = encryptedPrefs.getString(ENC_KEY, null)

    private fun deleteFile() {
        var success = false
        if (file.exists()) {
            success = file.delete()
        }
        result.text = getString(if (success) R.string.file_deleted else R.string.file_delete_error)
    }

    private fun downloadAndEncryptFile() {
        if (file.exists()) {
            Log.i("TAG", "Encrypted file already exists!")
            result.text = getString(R.string.file_exists)
        } else {
            Log.e("TAG", "Encrypted file does not exist exists! Downloading...")
            val request = Request.Builder().url(FILE_URL).build()
            okHttpClient.newCall(request).enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        result.text = e.message
                        Log.e("TAG", "Error occurred!", e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        runOnUiThread { onFileDownloaded(response.body!!.bytes()) }
                    }
                }
            )
        }
    }

    private fun onFileDownloaded(bytes: ByteArray) {
        var encryptedOutputStream: FileOutputStream? = null
        try {
            encryptedOutputStream = encryptedFile.openFileOutput().apply {
                write(bytes)
            }
            result.text = getString(R.string.file_downloaded)
        } catch (e: Exception) {
            Log.e("TAG", "Could not open encrypted file", e)
            result.text = e.message
        } finally {
            encryptedOutputStream?.close()
        }
    }

    private fun readFile(fileInput: () -> FileInputStream) {
        Log.i("TAG", "Loading file...")
        var fileInputStream: FileInputStream? = null
        try {
            fileInputStream = fileInput()
            val reader = BufferedReader(InputStreamReader(fileInputStream))
            val stringBuilder = StringBuilder()
            reader.forEachLine { line -> stringBuilder.appendln(line) }
            result.text = stringBuilder.toString()
        } catch (e: Exception) {
            Log.e("TAG", "Error occurred when reading file", e)
            result.text = e.message
        } finally {
            fileInputStream?.close()
        }
    }

    companion object {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
        const val ENC_KEY = "ENCRYPT_KEY"
        const val PREFS_FILENAME = "ENCRYPTED_PREFS"

        const val ENCRYPTED_FILE_NAME = "ENCRYPTED_FILE_NAME"
        const val FILE_URL =
            "https://raw.githubusercontent.com/stramek/Android-Security-Playground/master/README.md"
    }
}
