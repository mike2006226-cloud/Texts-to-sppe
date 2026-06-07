package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: TtsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.initialize(this)

        setContent {
            MyApplicationTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PurpleBackground
                ) {
                    TtsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

data class TtsState(
    val isInitialized: Boolean = false,
    val text: String = "Morning Meditation: Take a deep breath and let the tranquility of the sunrise wash over you. Every new day is a fresh opportunity to reset your mindset and embrace the peace within.",
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val availableLanguages: List<Locale> = emptyList(),
    val selectedLanguage: Locale? = null,
    val availableVoicesForLanguage: List<Voice> = emptyList(),
    val selectedVoice: Voice? = null,
    val isPlaying: Boolean = false,
    val showLanguageDialog: Boolean = false,
    val showVoiceDialog: Boolean = false
)

class TtsViewModel : ViewModel(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    fun initialize(context: android.content.Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            _state.update { it.copy(isInitialized = true) }
            loadVoices()
        } else {
            Log.e("TtsViewModel", "Initialization Failed!")
        }
    }

    private fun loadVoices() {
        val tts = this.tts ?: return
        try {
            val allVoices = tts.voices ?: return
            
            // Extract unique locales from voices
            val uniqueLocales = allVoices.mapNotNull { it.locale }
                .distinctBy { "${it.language}-${it.country}" }
                .sortedBy { it.displayName }

            val initialLocale = Locale.US
            val initialLang = uniqueLocales.find { it.language == initialLocale.language && it.country == initialLocale.country } 
                              ?: uniqueLocales.firstOrNull()
            
            _state.update { it.copy(
                availableLanguages = uniqueLocales,
                selectedLanguage = initialLang
            ) }
            
            if (initialLang != null) {
                updateVoicesForLanguage(initialLang)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateVoicesForLanguage(locale: Locale) {
        val tts = this.tts ?: return
        try {
            val allVoices = tts.voices ?: return
            val voicesForLang = allVoices.filter { 
                it.locale.language == locale.language && it.locale.country == locale.country 
            }.sortedBy { it.name }
            
            val selectedVoice = voicesForLang.firstOrNull()
            if (selectedVoice != null) {
                tts.voice = selectedVoice
            }
            
            _state.update { it.copy(
                availableVoicesForLanguage = voicesForLang,
                selectedVoice = selectedVoice
            ) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onLanguageSelected(locale: Locale) {
        _state.update { it.copy(selectedLanguage = locale, showLanguageDialog = false) }
        updateVoicesForLanguage(locale)
    }

    fun onVoiceSelected(voice: Voice) {
        tts?.voice = voice
        _state.update { it.copy(selectedVoice = voice, showVoiceDialog = false) }
    }

    fun onTextChanged(text: String) {
        _state.update { it.copy(text = text) }
    }
    
    fun onClearText() {
        _state.update { it.copy(text = "") }
    }

    fun onSpeedChanged(speed: Float) {
        tts?.setSpeechRate(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun onPitchChanged(pitchDelta: Float) {
        val current = _state.value.pitch
        var newPitch = current + pitchDelta
        newPitch = newPitch.coerceIn(0.1f, 2.0f)
        // round to 1 decimal place
        newPitch = Math.round(newPitch * 10f) / 10f
        tts?.setPitch(newPitch)
        _state.update { it.copy(pitch = newPitch) }
    }

    fun toggleLanguageDialog(show: Boolean) {
        _state.update { it.copy(showLanguageDialog = show) }
    }

    fun toggleVoiceDialog(show: Boolean) {
        _state.update { it.copy(showVoiceDialog = show) }
    }

    fun generateAudio() {
        val tts = this.tts ?: return
        val currentState = _state.value
        
        if (currentState.isPlaying) {
            tts.stop()
            _state.update { it.copy(isPlaying = false) }
        } else {
            if (currentState.text.isNotBlank()) {
                _state.update { it.copy(isPlaying = true) }
                // In a real app we'd use UtteranceProgressListener to reset `isPlaying` when done
                tts.speak(currentState.text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
                // For simplicity, just reset it right away or after a delay (omitted here for simple implementation)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsScreen(viewModel: TtsViewModel) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PurplePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Echo Voice",
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                )
            )
        },
        containerColor = PurpleBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Text Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 300.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, PurpleOutline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "INPUT TEXT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PurplePrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "${state.text.length} / 5000",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextField(
                        value = state.text,
                        onValueChange = { viewModel.onTextChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = PurplePrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            color = TextSecondary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        ),
                        placeholder = { 
                            Text("Type or paste your text here...", color = TextTertiary) 
                        }
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { 
                            // Pasting clipboard logic could go here
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = PurplePrimary)
                        }
                        IconButton(onClick = { viewModel.onClearText() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear", tint = PurplePrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Profile and Language
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Voice Profile
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleSecondary)
                        .border(1.dp, PurpleOutline, RoundedCornerShape(16.dp))
                        .clickable { viewModel.toggleVoiceDialog(true) }
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "VOICE PROFILE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurpleOnSecondary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.selectedVoice?.name?.let {
                                    if (it.length > 15) it.take(15) + "..." else it
                                } ?: "Default",
                                fontWeight = FontWeight.Medium,
                                color = PurpleOnSecondary,
                                maxLines = 1
                            )
                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = PurpleOnSecondary)
                        }
                    }
                }

                // Language
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleTertiary)
                        .border(1.dp, PurpleOutline, RoundedCornerShape(16.dp))
                        .clickable { viewModel.toggleLanguageDialog(true) }
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "LANGUAGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.selectedLanguage?.displayName?.let {
                                    if (it.length > 12) it.take(12) + "..." else it
                                } ?: "Select",
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Icon(Icons.Default.Language, contentDescription = null, tint = TextSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, PurpleOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Playback Speed", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                        Text("${String.format(Locale.getDefault(), "%.2f", state.speed)}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                    }
                    Slider(
                        value = state.speed,
                        onValueChange = { viewModel.onSpeedChanged(it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = PurplePrimary,
                            activeTrackColor = PurplePrimary,
                            inactiveTrackColor = Color(0xFFE7E0EC)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Pitch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PurpleTertiary)
                            .border(1.dp, PurpleOutline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pitch", fontSize = 12.sp, color = TextSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.onPitchChanged(-0.1f) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Decrease", tint = PurplePrimary)
                            }
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f", state.pitch),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(36.dp)
                            )
                            IconButton(onClick = { viewModel.onPitchChanged(0.1f) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Increase", tint = PurplePrimary)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(1.dp, PurpleOutline, CircleShape)
                            .background(Color.Transparent, CircleShape)
                            .clip(CircleShape)
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Download", tint = TextSecondary)
                    }

                    Button(
                        onClick = { viewModel.generateAudio() },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (state.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, 
                                    contentDescription = "Play/Stop", 
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (state.isPlaying) "Stop" else "Generate Audio", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(1.dp, PurpleOutline, CircleShape)
                            .background(Color.Transparent, CircleShape)
                            .clip(CircleShape)
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Favorite", tint = TextSecondary)
                    }
                }
            }
        }
    }

    if (state.showLanguageDialog) {
        Dialog(onDismissRequest = { viewModel.toggleLanguageDialog(false) }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Language", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(state.availableLanguages) { lang ->
                            Text(
                                text = lang.displayName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onLanguageSelected(lang) }
                                    .padding(vertical = 12.dp),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showVoiceDialog) {
        Dialog(onDismissRequest = { viewModel.toggleVoiceDialog(false) }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Voice Profile", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
                    if (state.availableVoicesForLanguage.isEmpty()) {
                        Text("No specific voices available for this language.", color = TextSecondary, modifier = Modifier.padding(top = 16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(state.availableVoicesForLanguage) { voice ->
                                Text(
                                    text = voice.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onVoiceSelected(voice) }
                                        .padding(vertical = 12.dp),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
