package com.sathiai.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val MODEL = "openai/gpt-oss-20b"

// v0.2 is a personal prototype. Groq recommends keeping API keys on a trusted backend,
// not in a client app. This build stores the key locally only so the prototype can be tested.
// Do not distribute an APK containing a key.

data class Reply(val style: String, val text: String, val why: String = "")

data class AiResult(val replies: List<Reply>, val booster: List<String>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SathiAIApp() }
    }
}

@Composable
fun SathiAIApp() {
    var message by remember { mutableStateOf("") }
    var contextText by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Auto") }
    var style by remember { mutableStateOf("Natural") }
    var result by remember { mutableStateOf<AiResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    MaterialTheme {
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("✨ Sathi AI") },
                        actions = { TextButton(onClick = { showSettings = true }) { Text("Settings") } }
                    )
                }
            ) { pad ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("💬 Reply Assistant", style = MaterialTheme.typography.headlineSmall)
                        Text("Hinglish • Latin Nepali • English • Mixed chat")
                    }
                    item {
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Paste or type their message") },
                            minLines = 4
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = contextText,
                            onValueChange = { contextText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Optional: previous chat/context") },
                            minLines = 2
                        )
                    }
                    item { ChoiceRow("Relationship", listOf("Auto", "Friend", "Crush", "GF/BF", "Other"), relationship) { relationship = it } }
                    item { ChoiceRow("Reply style", listOf("Natural", "Funny", "Playful", "Flirty", "Romantic", "Caring", "Confident", "Short"), style) { style = it } }
                    item {
                        Button(
                            onClick = {
                                loading = true; error = null
                                scope.launch {
                                    try {
                                        result = GroqClient.analyze(ctx, message, contextText, relationship, style)
                                    } catch (e: Exception) {
                                        error = e.message ?: "Something went wrong"
                                    } finally { loading = false }
                                }
                            },
                            enabled = message.isNotBlank() && !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (loading) "Thinking…" else "✨ Analyse & Suggest Replies") }
                    }
                    error?.let { msg -> item { Text("⚠️ $msg", color = MaterialTheme.colorScheme.error) } }
                    result?.let { r ->
                        item {
                            Text("Suggested replies", style = MaterialTheme.typography.titleLarge)
                            Text("Auto-send is OFF. You choose what to copy/send.")
                        }
                        items(r.replies) { reply -> ReplyCard(reply, ctx) }
                        if (r.booster.isNotEmpty()) {
                            item { Text("💡 Conversation Booster", style = MaterialTheme.typography.titleLarge) }
                            items(r.booster) { q -> Card(Modifier.fillMaxWidth()) { Text(q, Modifier.padding(14.dp)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.take(4).forEach { option ->
                FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) })
            }
        }
        if (options.size > 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.drop(4).forEach { option ->
                    FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) })
                }
            }
        }
    }
}

@Composable
private fun ReplyCard(reply: Reply, ctx: Context) {
    var showWhy by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reply.style, style = MaterialTheme.typography.labelLarge)
            Text(reply.text, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyText(ctx, reply.text) }) { Text("Copy") }
                TextButton(onClick = { showWhy = !showWhy }) { Text("Why?") }
            }
            if (showWhy && reply.why.isNotBlank()) Text(reply.why)
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(GroqClient.getKey(ctx)) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("⚙️ Settings", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Groq API key") },
            visualTransformation = PasswordVisualTransformation()
        )
        Button(onClick = { GroqClient.saveKey(ctx, key.trim()); saved = true }, enabled = key.isNotBlank()) { Text("Save key") }
        if (saved) Text("✅ API key saved on this device.")
        Text("Model: $MODEL")
        Text("Auto-send: OFF")
        Text("Privacy note: Groq recommends keeping API keys on a trusted backend. This v0.2 direct-API mode is for your personal prototype only; do not share an APK containing your key.")
    }
}

private fun copyText(ctx: Context, text: String) {
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Sathi AI reply", text))
}

object GroqClient {
    private const val PREF = "sathi_ai"
    private const val KEY = "groq_api_key"

    fun getKey(ctx: Context): String = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    fun saveKey(ctx: Context, key: String) { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, key).apply() }

    suspend fun analyze(ctx: Context, message: String, contextText: String, relationship: String, style: String): AiResult = withContext(Dispatchers.IO) {
        val apiKey = getKey(ctx)
        require(apiKey.isNotBlank()) { "Open Settings and save your Groq API key first." }

        val system = """
You are Sathi AI, a personal reply assistant. Generate natural replies for real chats.
Understand Hinglish, Hindi/English mix, Latin Nepali, English, slang, abbreviations, emojis and typos.
Never auto-send anything. Keep replies human, not robotic, not overly formal.
Respect the selected relationship and requested style, but adapt to the incoming message.
Return ONLY valid JSON with this exact shape:
{"replies":[{"style":"Natural","text":"...","why":"..."},...],"booster":["question 1","question 2"]}
Give 5 replies with varied but useful styles. Each reply should be sendable as-is.
Give 2 short conversation-booster questions only when they fit naturally; otherwise return an empty array.
""".trimIndent()
        val user = "Incoming message: $message\nPrevious context: ${contextText.ifBlank { "none" }}\nRelationship: $relationship\nPreferred style: $style"
        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
            put("temperature", 0.7)
            put("max_completion_tokens", 700)
            put("response_format", JSONObject().put("type", "json_object"))
        }.toString()

        val conn = (URL(GROQ_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 40000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Groq API error $code: ${cleanError(raw)}")

        val root = JSONObject(raw)
        val content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val data = JSONObject(content)
        val repliesJson = data.optJSONArray("replies") ?: org.json.JSONArray()
        val replies = buildList {
            for (i in 0 until repliesJson.length()) {
                val x = repliesJson.getJSONObject(i)
                add(Reply(x.optString("style", "Reply"), x.optString("text", ""), x.optString("why", "")))
            }
        }.filter { it.text.isNotBlank() }
        val boosterJson = data.optJSONArray("booster") ?: org.json.JSONArray()
        val booster = buildList { for (i in 0 until boosterJson.length()) add(boosterJson.optString(i)) }.filter { it.isNotBlank() }
        AiResult(replies, booster)
    }

    private fun cleanError(raw: String): String = try {
        val o = JSONObject(raw); o.optJSONObject("error")?.optString("message") ?: raw.take(300)
    } catch (_: Exception) { raw.take(300) }
}
