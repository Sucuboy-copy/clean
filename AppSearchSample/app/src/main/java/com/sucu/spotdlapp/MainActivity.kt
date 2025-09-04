package com.sucu.spotdlapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arthenica.mobileffmpeg.Config
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.sucu.spotdlapp.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var python: Python
    private var outputDirectory: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDownloading = false

    companion object {
        private const val REQUEST_CODE_STORAGE_PERMISSION = 100
        private const val REQUEST_CODE_DIRECTORY_SELECTION = 101
        private const val REQUEST_CODE_MANAGE_STORAGE = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Python
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        python = Python.getInstance()

        // Configurar listeners
        setupListeners()

        // Verificar y solicitar permisos
        checkPermissions()

        // Inicializar directorio de salida
        setupOutputDirectory()

        // Inicializar FFmpeg
        initializeFFmpeg()

        appendLog("Aplicación iniciada. Listo para descargar.")
    }

    private fun setupListeners() {
        binding.selectDirectoryButton.setOnClickListener {
            selectOutputDirectory()
        }

        binding.downloadButton.setOnClickListener {
            if (isDownloading) {
                appendLog("Ya hay una descarga en progreso.")
                return@setOnClickListener
            }

            if (checkPermissions()) {
                startDownload()
            }
        }

        binding.clearLogButton.setOnClickListener {
            binding.logTextView.text = ""
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                true
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                false
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                true
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_STORAGE_PERMISSION
                )
                false
            }
        }
    }

    private fun setupOutputDirectory() {
        outputDirectory = File(Environment.getExternalStorageDirectory(), "spotdl")
        if (!outputDirectory!!.exists()) {
            outputDirectory!!.mkdirs()
        }
        updateDirectoryUI()
    }

    private fun updateDirectoryUI() {
        binding.directoryTextView.text = "Directorio: ${outputDirectory?.absolutePath}"
    }

    private fun initializeFFmpeg() {
        thread {
            try {
                val ffmpegVersion = Config.getVersion()
                appendLog("MobileFFmpeg inicializado. Versión: $ffmpegVersion")
            } catch (e: Exception) {
                appendLog("Error al inicializar FFmpeg: ${e.message}")
            }
        }
    }

    private fun selectOutputDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(intent, REQUEST_CODE_DIRECTORY_SELECTION)
    }

    private fun startDownload() {
        val url = binding.urlEditText.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Por favor, introduce una URL de Spotify", Toast.LENGTH_SHORT).show()
            return
        }

        val argsText = binding.argsEditText.text.toString().trim()
        val args = if (argsText.isNotEmpty()) argsText.split(" ") else emptyList()

        isDownloading = true
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.progressBar.progress = 0
        binding.progressTextView.text = "Estado: Preparando..."

        thread {
            try {
                // Crear directorio temporal
                val tempDir = File(filesDir, "spotdl_temp")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }

                // Construir comando SpotDL
                val command = mutableListOf<String>()
                command.add("download")
                command.add(url)
                command.add("--output")
                command.add(File(tempDir, "{artist} - {title}.{output-ext}").absolutePath)

                // Añadir argumentos adicionales
                if (args.isNotEmpty()) {
                    command.addAll(args)
                }

                appendLog("Ejecutando SpotDL con comando: ${command.joinToString(" ")}")

                // Ejecutar SpotDL
                val spotdlModule = python.getModule("spotdl_wrapper")
                val result = spotdlModule.callAttr("run_spotdl", command, tempDir.absolutePath)

                if (result != null && result.toInt() == 0) {
                    // Mover archivos al directorio de destino
                    moveFilesToOutputDirectory(tempDir)
                    appendLog("Descarga completada con éxito.")
                    handler.post {
                        binding.progressTextView.text = "Estado: Completado"
                        binding.progressBar.progress = 100
                    }
                } else {
                    appendLog("Error en la descarga. Código de salida: $result")
                    handler.post {
                        binding.progressTextView.text = "Estado: Error"
                    }
                }
            } catch (e: Exception) {
                appendLog("Error durante la descarga: ${e.message}")
                handler.post {
                    binding.progressTextView.text = "Estado: Error"
                }
            } finally {
                isDownloading = false
                handler.post {
                    binding.progressBar.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun moveFilesToOutputDirectory(tempDir: File) {
        val files = tempDir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isFile && (file.name.endsWith(".mp3") || file.name.endsWith(".flac") || 
                    file.name.endsWith(".wav") || file.name.endsWith(".m4a"))) {
                    
                    val destination = File(outputDirectory, file.name)
                    
                    // Manejar duplicados
                    if (destination.exists()) {
                        val newName = generateUniqueFileName(destination)
                        val newDestination = File(outputDirectory, newName)
                        file.renameTo(newDestination)
                        appendLog("Archivo renombrado: $newName")
                    } else {
                        file.renameTo(destination)
                        appendLog("Archivo guardado: ${file.name}")
                    }
                }
            }
        }
        
        // Limpiar directorio temporal
        tempDir.deleteRecursively()
    }

    private fun generateUniqueFileName(file: File): String {
        val baseName = file.nameWithoutExtension
        val extension = file.extension
        var counter = 1
        var newFile: File
        
        do {
            val newName = "$baseName ($counter).$extension"
            newFile = File(file.parent, newName)
            counter++
        } while (newFile.exists())
        
        return newFile.name
    }

    private fun appendLog(message: String) {
        handler.post {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"
            binding.logTextView.append(logEntry)
            
            // Auto-scroll
            val scrollView = binding.logTextView.parent as? android.widget.ScrollView
            scrollView?.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_DIRECTORY_SELECTION -> {
                if (resultCode == RESULT_OK && data != null) {
                    val treeUri = data.data
                    if (treeUri != null) {
                        contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        
                        val documentsTree = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, treeUri)
                        if (documentsTree != null && documentsTree.canWrite()) {
                            outputDirectory = File(documentsTree.uri.path!!)
                            updateDirectoryUI()
                            appendLog("Directorio de salida cambiado a: ${outputDirectory?.absolutePath}")
                        }
                    }
                }
            }
            
            REQUEST_CODE_MANAGE_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        appendLog("Permisos de almacenamiento concedidos")
                    } else {
                        appendLog("Permisos de almacenamiento denegados")
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                appendLog("Permisos de almacenamiento concedidos")
            } else {
                appendLog("Permisos de almacenamiento denegados")
                AlertDialog.Builder(this)
                    .setTitle("Permisos necesarios")
                    .setMessage("La aplicación necesita permisos de almacenamiento para guardar las descargas")
                    .setPositiveButton("Solicitar de nuevo") { _, _ ->
                        checkPermissions()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos si es necesario
    }
}