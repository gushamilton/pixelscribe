package com.example.ambientpixel.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ambientpixel.R
import com.example.ambientpixel.data.NoteEntity
import com.example.ambientpixel.viewmodel.ConsultationViewModel
import com.example.ambientpixel.viewmodel.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationScreen(viewModel: ConsultationViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        }
    }

    val uiState = viewModel.uiState
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings") },
            text = {
                Column {
                    Text("System Prompt:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = uiState.systemPrompt,
                        onValueChange = { viewModel.updateSystemPrompt(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("Enter system prompt...") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("About", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version_label),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AmbientPixel Scribe") },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.screen) {
                Screen.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.lastError != null) {
                            Text(
                                text = uiState.lastError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.startRecording()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                modifier = Modifier
                                    .size(200.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "RECORD",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Recent Consultations",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(uiState.history) { note ->
                                NoteHistoryItem(note)
                            }
                        }
                    }
                }

                Screen.Recording -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatElapsed(uiState.elapsedSeconds),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        Button(
                            onClick = { viewModel.stopRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                            modifier = Modifier
                                .height(80.dp)
                                .fillMaxWidth()
                        ) {
                            Text(text = "STOP RECORDING", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                Screen.Processing -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = uiState.processingMessage,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                Screen.Result -> {
                    var selectedTab by remember { mutableIntStateOf(0) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        if (uiState.lastError != null) {
                            Text(
                                text = uiState.lastError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Final Note") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Raw Transcript") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedTab == 0) {
                            TextField(
                                value = uiState.cleanedNote,
                                onValueChange = { viewModel.updateCleanedNote(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                placeholder = { Text("Cleaned note will appear here") }
                            )
                        } else {
                            TextField(
                                value = uiState.rawTranscript,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                placeholder = { Text("Raw transcript will appear here") }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = { viewModel.reset() }) {
                                Text(text = "NEW CONSULTATION")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteHistoryItem(note: NoteEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { /* TODO: Implement viewing old notes */ }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(note.createdAt)),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.cleanedNote,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, remaining)
}
