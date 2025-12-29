package com.example.ambientpixel

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ambientpixel.ui.ConsultationScreen
import com.example.ambientpixel.ui.PixelScribeTheme
import com.example.ambientpixel.viewmodel.ConsultationViewModel
import com.example.ambientpixel.viewmodel.ConsultationViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixelScribeTheme {
                MainContent()
            }
        }
    }

    @Composable
    fun MainContent() {
        var hasPermissions by remember { mutableStateOf(false) }
        val lifecycleOwner = LocalLifecycleOwner.current

        // Check permissions function
        fun checkPermissions(): Boolean {
            val audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val storage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            return audio && storage
        }

        // Listener to re-check permissions when app resumes (returns from Settings)
        LaunchedEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasPermissions = checkPermissions()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { perms: Map<String, Boolean> ->
            // Update state based on results, but ON_RESUME will effectively handle the full check especially for Manage External Storage
            hasPermissions = checkPermissions()
        }
        
        // Initial check and request
        LaunchedEffect(Unit) {
            if (checkPermissions()) {
                hasPermissions = true
            } else {
                // Request permissions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                
                val permsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    permsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    permsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (permsToRequest.isNotEmpty()) {
                    permissionLauncher.launch(permsToRequest.toTypedArray())
                }
            }
        }

        if (hasPermissions) {
            Surface {
                val viewModel: ConsultationViewModel = viewModel(
                    factory = ConsultationViewModelFactory(applicationContext)
                )
                ConsultationScreen(viewModel)
            }
        } else {
            PermissionErrorScreen {
                // Retry logic: check again, or send user to settings if needed
                hasPermissions = checkPermissions()
                if (!hasPermissions) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                }
            }
        }
    }

    @Composable
    fun PermissionErrorScreen(onRetry: () -> Unit) {
        Scaffold { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Audio and File permissions are required to load local AI models and record consults.")
                    Button(onClick = onRetry) {
                        Text("Grant Permissions / Check Again")
                    }
                }
            }
        }
    }
}
