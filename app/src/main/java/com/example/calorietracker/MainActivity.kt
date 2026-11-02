package com.example.calorietracker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

// Data classes for chat and food tracking
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val calories: Int? = null,
    val foodItems: List<String> = emptyList()
)

data class FoodEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val calories: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// OpenRouter API data classes (OpenAI-compatible format)
@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val max_tokens: Int = 150,
    val temperature: Double = 0.7,
    val top_p: Double = 0.9
)

@Serializable
data class OpenRouterChoice(
    val message: OpenRouterMessage,
    val finish_reason: String? = null
)

@Serializable
data class OpenRouterUsage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

@Serializable
data class OpenRouterResponse(
    val id: String? = null,
    val choices: List<OpenRouterChoice> = emptyList(),
    val usage: OpenRouterUsage? = null,
    val error: OpenRouterError? = null
)

@Serializable
data class OpenRouterError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

// Enhanced local data persistence helper
class LocalDataManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("calorie_tracker", Context.MODE_PRIVATE)

    fun saveFoodEntries(entries: List<FoodEntry>) {
        val entriesString = entries.joinToString("|") { entry ->
            "${entry.id},${entry.name},${entry.calories},${entry.timestamp}"
        }
        prefs.edit().putString("food_entries", entriesString).apply()
    }

    fun loadFoodEntries(): List<FoodEntry> {
        val entriesString = prefs.getString("food_entries", "") ?: ""
        if (entriesString.isEmpty()) return emptyList()

        return entriesString.split("|").mapNotNull { entryString ->
            val parts = entryString.split(",")
            if (parts.size == 4) {
                FoodEntry(
                    id = parts[0],
                    name = parts[1],
                    calories = parts[2].toIntOrNull() ?: 0,
                    timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                )
            } else null
        }
    }

    fun saveChatMessages(messages: List<ChatMessage>) {
        val messagesString = messages.joinToString("|") { message ->
            "${message.id},${message.text},${message.isUser},${message.timestamp},${message.calories ?: "null"},${message.foodItems.joinToString(";")}"
        }
        prefs.edit().putString("chat_messages", messagesString).apply()
    }

    fun loadChatMessages(): List<ChatMessage> {
        val messagesString = prefs.getString("chat_messages", "") ?: ""
        if (messagesString.isEmpty()) return emptyList()

        return messagesString.split("|").mapNotNull { messageString ->
            val parts = messageString.split(",")
            if (parts.size >= 5) {
                ChatMessage(
                    id = parts[0],
                    text = parts[1],
                    isUser = parts[2].toBoolean(),
                    timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    calories = if (parts[4] == "null") null else parts[4].toIntOrNull(),
                    foodItems = if (parts.size > 5) parts[5].split(";").filter { it.isNotEmpty() } else emptyList()
                )
            } else null
        }
    }

    fun saveHealth(health: Int) {
        prefs.edit().putInt("health", health).apply()
    }

    fun loadHealth(): Int {
        return prefs.getInt("health", 75)
    }

    fun getTodayCalories(): Int {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return loadFoodEntries().filter { it.timestamp >= today }
            .sumOf { it.calories }
    }

    // OpenRouter API key storage
    fun saveOpenRouterApiKey(apiKey: String) {
        prefs.edit().putString("openrouter_api_key", apiKey).apply()
    }

    fun loadOpenRouterApiKey(): String? {
        return prefs.getString("openrouter_api_key", null)
    }

    // Legacy HF API key methods (for backward compatibility)
    fun saveApiKey(apiKey: String) {
        prefs.edit().putString("hf_api_key", apiKey).apply()
    }

    fun loadApiKey(): String? {
        return prefs.getString("hf_api_key", null)
    }
}

// Enhanced local food database with more comprehensive entries
class LocalFoodDatabase {
    private val foodDatabase = mapOf(
        // Fruits
        "apple" to 52, "banana" to 89, "orange" to 47, "strawberry" to 32,
        "grape" to 62, "watermelon" to 30, "pineapple" to 50, "mango" to 60,
        "peach" to 39, "pear" to 57, "cherry" to 63, "blueberry" to 57,

        // Vegetables
        "carrot" to 25, "broccoli" to 25, "spinach" to 7, "lettuce" to 5,
        "tomato" to 18, "cucumber" to 12, "onion" to 40, "pepper" to 20,
        "potato" to 77, "sweet potato" to 86, "corn" to 86, "peas" to 81,

        // Grains & Carbs
        "rice" to 130, "bread" to 265, "pasta" to 131, "cereal" to 367,
        "oats" to 389, "quinoa" to 120, "noodles" to 138, "bagel" to 245,

        // Proteins
        "chicken" to 165, "beef" to 250, "pork" to 242, "fish" to 206,
        "salmon" to 208, "tuna" to 184, "egg" to 155, "tofu" to 76,
        "beans" to 245, "lentils" to 230, "turkey" to 135, "shrimp" to 85,

        // Dairy
        "milk" to 42, "yogurt" to 59, "cheese" to 113, "butter" to 717,
        "cream" to 345, "ice cream" to 207,

        // Snacks & Fast Food
        "pizza" to 266, "burger" to 540, "fries" to 365, "sandwich" to 300,
        "cookie" to 502, "cake" to 257, "chocolate" to 546, "chips" to 536,
        "donut" to 452, "candy" to 400,

        // Beverages
        "coffee" to 2, "tea" to 1, "soda" to 39, "juice" to 45,
        "beer" to 43, "wine" to 83, "water" to 0,

        // Nuts & Seeds
        "almonds" to 579, "peanuts" to 567, "walnuts" to 654, "seeds" to 574
    )

    fun searchFoods(query: String): Map<String, Int> {
        val lowercaseQuery = query.lowercase()
        return foodDatabase.filter { (food, _) ->
            lowercaseQuery.contains(food) || food.contains(lowercaseQuery)
        }
    }

    fun getAllFoods(): Map<String, Int> = foodDatabase
    fun getCaloriesForFood(food: String): Int? {
        return foodDatabase[food.lowercase()]
    }
}

// Enhanced Qwen processor with CURRENT 2025 free models
class QwenProcessor(private val apiKey: String?) {
    private val foodDatabase = LocalFoodDatabase()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"

        // UPDATED list of free models available in 2025
        private val FREE_MODELS = listOf(
            "qwen/qwq-32b:free",                    // Qwen reasoning model (confirmed working)
            "deepseek/deepseek-chat-v3-0324:free", // DeepSeek V3 (excellent for coding/reasoning)
            "google/gemma-2-9b-it:free",           // Google Gemma 2
            "meta-llama/llama-3.2-3b-instruct:free", // Llama 3.2
            "microsoft/phi-3-medium-4k-instruct:free" // Microsoft Phi-3
        )

        // Preferred model for food/nutrition tasks
        private const val PRIMARY_MODEL = "qwen/qwq-32b:free"
    }

    suspend fun processMessage(message: String, conversationHistory: List<ChatMessage> = emptyList()): Triple<String, Int, List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isNullOrEmpty()) {
                    println("API key is null or empty, using local processing")
                    return@withContext fallbackToLocalProcessing(message)
                }

                println("Attempting OpenRouter API call")
                val qwenResponse = callQwenAPI(message, conversationHistory)

                if (qwenResponse != null) {
                    println("API call successful")
                    parseQwenResponse(qwenResponse, message)
                } else {
                    println("API call failed, falling back to local processing")
                    fallbackToLocalProcessing(message)
                }
            } catch (e: Exception) {
                println("Qwen processing error: ${e.message}")
                e.printStackTrace()
                fallbackToLocalProcessing(message)
            }
        }
    }

    private suspend fun callQwenAPI(message: String, conversationHistory: List<ChatMessage>): String? {
        // Try primary model first, then fallbacks
        val modelsToTry = listOf(PRIMARY_MODEL) + FREE_MODELS.filter { it != PRIMARY_MODEL }

        for (model in modelsToTry) {
            try {
                println("Trying model: $model")
                val result = makeApiRequest(model, message, conversationHistory)
                if (result != null) {
                    println("Success with model: $model")
                    return result
                }
            } catch (e: Exception) {
                println("Model $model failed: ${e.message}")
                continue
            }
        }
        return null
    }

    private suspend fun makeApiRequest(model: String, message: String, conversationHistory: List<ChatMessage>): String? {
        return try {
            val url = URL(OPENROUTER_API_URL)
            val connection = url.openConnection() as HttpURLConnection

            // Set proper headers
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("HTTP-Referer", "https://calorietracker.app") // More realistic referer
            connection.setRequestProperty("X-Title", "Calorie Tracker App")
            connection.setRequestProperty("User-Agent", "CalorieTracker/1.0 (Android)") // Add user agent

            connection.doOutput = true
            connection.connectTimeout = 20000 // Increased timeout
            connection.readTimeout = 30000

            // Build conversation context
            val messages = mutableListOf<OpenRouterMessage>()

            // System message optimized for QwQ reasoning model
            val systemPrompt = if (model.contains("qwq")) {
                """You are a helpful nutrition assistant. When users mention food:
1. Acknowledge what they ate in a friendly way
2. Give brief, helpful nutrition tips
3. Be encouraging about their food choices
4. Keep responses concise (under 80 words)
5. Use simple, conversational language

Think step by step about the nutritional value, but respond naturally."""
            } else {
                """You are a helpful nutrition assistant. When users mention food:
1. Be friendly and encouraging
2. Acknowledge the foods they mentioned
3. Give brief nutrition tips
4. Keep responses under 80 words"""
            }

            messages.add(OpenRouterMessage(
                role = "system",
                content = systemPrompt
            ))

            // Add recent conversation history (limited to save tokens)
            conversationHistory.takeLast(2).forEach { chatMessage ->
                messages.add(OpenRouterMessage(
                    role = if (chatMessage.isUser) "user" else "assistant",
                    content = chatMessage.text.take(200) // Limit message length
                ))
            }

            // Add current user message
            messages.add(OpenRouterMessage(
                role = "user",
                content = message
            ))

            val request = OpenRouterRequest(
                model = model,
                messages = messages,
                max_tokens = 120, // Reduced for faster/cheaper responses
                temperature = 0.7
            )

            val jsonRequest = json.encodeToString(request)
            println("Request to $model: ${jsonRequest.take(200)}...")

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonRequest)
                writer.flush()
            }

            val responseCode = connection.responseCode
            println("Response code: $responseCode")

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        val response = reader.readText()
                        println("API Response: ${response.take(300)}...")

                        val openRouterResponse = json.decodeFromString<OpenRouterResponse>(response)

                        if (openRouterResponse.error != null) {
                            println("OpenRouter API Error: ${openRouterResponse.error.message}")
                            throw Exception("API Error: ${openRouterResponse.error.message}")
                        }

                        openRouterResponse.choices.firstOrNull()?.message?.content
                            ?: throw Exception("No response content")
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    println("HTTP 401: Invalid API key")
                    throw Exception("Invalid API key - check your OpenRouter key")
                }
                HttpURLConnection.HTTP_BAD_REQUEST -> {
                    val errorResponse = try {
                        BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                    } catch (e: Exception) { "Bad request" }
                    println("HTTP 400: $errorResponse")
                    throw Exception("Bad request - model might not be available")
                }
                429 -> {
                    println("HTTP 429: Rate limited")
                    throw Exception("Rate limited - try again in a moment")
                }
                else -> {
                    val errorResponse = try {
                        BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { it.readText() }
                    } catch (e: Exception) { "Unknown error" }
                    println("HTTP Error $responseCode: $errorResponse")
                    throw Exception("HTTP $responseCode: ${errorResponse.take(100)}")
                }
            }
        } catch (e: Exception) {
            println("API request to $model failed: ${e.message}")
            null
        }
    }

    private fun parseQwenResponse(qwenResponse: String, originalMessage: String): Triple<String, Int, List<String>> {
        val detectedFoods = mutableListOf<String>()
        var totalCalories = 0

        // Detect foods from the original message using local database
        val searchResults = foodDatabase.searchFoods(originalMessage)

        searchResults.forEach { (food, calories) ->
            detectedFoods.add(food)
            totalCalories += calories
        }

        // Apply quantity multiplier if detected
        val quantityMultiplier = extractQuantity(originalMessage)
        totalCalories = (totalCalories * quantityMultiplier).toInt()

        // Clean up the AI response (remove any reasoning artifacts if using QwQ)
        val cleanResponse = qwenResponse
            .replace(Regex("""<thinking>.*?</thinking>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<thought>.*?</thought>""", RegexOption.DOT_MATCHES_ALL), "")
            .trim()

        // Combine AI response with calorie info
        val enhancedResponse = if (detectedFoods.isNotEmpty()) {
            val calorieInfo = "\n\n📊 Detected: ${detectedFoods.joinToString(", ")} - ~${totalCalories} calories"
            cleanResponse + calorieInfo
        } else {
            cleanResponse
        }

        return Triple(enhancedResponse, totalCalories, detectedFoods)
    }

    private fun fallbackToLocalProcessing(message: String): Triple<String, Int, List<String>> {
        val detectedFoods = mutableListOf<String>()
        var totalCalories = 0

        val searchResults = foodDatabase.searchFoods(message)

        searchResults.forEach { (food, calories) ->
            detectedFoods.add(food)
            totalCalories += calories
        }

        val quantityMultiplier = extractQuantity(message)
        totalCalories = (totalCalories * quantityMultiplier).toInt()

        val response = when {
            detectedFoods.isNotEmpty() -> {
                val foodList = detectedFoods.joinToString(", ")
                val quantityText = if (quantityMultiplier > 1) " (${quantityMultiplier}x servings)" else ""
                "Great choice! I found: $foodList$quantityText. Estimated calories: $totalCalories\n\n💡 Local AI active. For personalized nutrition advice, check your OpenRouter connection!"
            }
            else -> {
                "I couldn't identify specific foods. Try: 'I ate an apple', 'Had chicken salad', etc.\n\n🔧 Having trouble? Check your API key and internet connection."
            }
        }

        return Triple(response, totalCalories, detectedFoods)
    }

    private fun extractQuantity(message: String): Float {
        val quantityRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:servings?|portions?|pieces?|cups?|slices?|bowls?|plates?|x)?""")
        val matches = quantityRegex.findAll(message.lowercase())
        return matches.firstOrNull()?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
    }

    fun getSuggestions(): List<String> {
        return listOf(
            "I ate an apple for breakfast",
            "Had grilled chicken with rice",
            "Ate 2 slices of pizza",
            "Drank coffee with milk",
            "Had a salad with dressing",
            "What's a healthy snack?",
            "How many calories should I eat?",
            "Tell me about protein foods"
        )
    }

    // Test function to verify API key with current models
    suspend fun testApiKey(): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isNullOrEmpty()) {
                    return@withContext Pair(false, "❌ API key is empty")
                }

                println("Testing API key with primary model: $PRIMARY_MODEL")
                val testResponse = makeApiRequest(
                    PRIMARY_MODEL,
                    "Hello, can you help me with nutrition?",
                    emptyList()
                )

                if (testResponse != null) {
                    Pair(true, "✅ API key works! Connected to: $PRIMARY_MODEL")
                } else {
                    Pair(false, "❌ API test failed - check logs for details")
                }
            } catch (e: Exception) {
                println("API test exception: ${e.message}")
                val errorMsg = when {
                    e.message?.contains("Invalid API key") == true -> "❌ Invalid API key format"
                    e.message?.contains("401") == true -> "❌ API key not authorized"
                    e.message?.contains("429") == true -> "⚠️ Rate limited - try again later"
                    e.message?.contains("Bad request") == true -> "⚠️ Model unavailable - but key is valid"
                    else -> "❌ Connection error: ${e.message?.take(50)}"
                }
                Pair(false, errorMsg)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalorieTrackerTheme {
                HomePage()
            }
        }
    }
}

@Composable
fun CalorieTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4CAF50),
            secondary = Color(0xFF81C784),
            background = Color(0xFFD9FF88)
        ),
        content = content
    )
}

@Composable
fun HomePage() {
    val context = LocalContext.current
    val dataManager = remember { LocalDataManager(context) }
    val scope = rememberCoroutineScope()

    // Updated to use OpenRouter API key and Qwen processor
    var apiKey by remember { mutableStateOf(dataManager.loadOpenRouterApiKey()) }
    var foodProcessor by remember { mutableStateOf(QwenProcessor(apiKey)) }
    var health by remember { mutableStateOf(dataManager.loadHealth()) }
    var bunnyState by remember { mutableStateOf("idle") }
    var backgroundIndex by remember { mutableStateOf(0) }
    var showChatDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var chatMessages by remember { mutableStateOf(dataManager.loadChatMessages()) }
    var foodEntries by remember { mutableStateOf(dataManager.loadFoodEntries()) }
    var totalCalories by remember { mutableStateOf(dataManager.getTodayCalories()) }
    var isProcessing by remember { mutableStateOf(false) }

    // Auto-decrease health over time and save state
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000) // Every 30 seconds
            if (health > 0) {
                health = (health - 2).coerceAtLeast(0) // Slower decrease
                dataManager.saveHealth(health)
            }
        }
    }

    // Only idle state, eating is triggered when food is added
    LaunchedEffect(health) {
        if (bunnyState != "eating") {
            bunnyState = "idle"
        }
    }

    // Reset bunny to idle after eating
    LaunchedEffect(bunnyState) {
        if (bunnyState == "eating") {
            delay(3000) // Show eating animation for 3 seconds
            bunnyState = "idle"
        }
    }

    val backgroundColors = listOf(
        Color(0xFFFFFFFF) // White
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColors[backgroundIndex % backgroundColors.size],
                        backgroundColors[backgroundIndex % backgroundColors.size].copy(alpha = 0.7f)
                    )
                )
            )
    ) {
        // Top Icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .background(
                        Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF4CAF50)
                )
            }

            Text(
                text = "Calorie Tracker",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                ),
                modifier = Modifier.align(Alignment.CenterVertically)
            )

            IconButton(
                onClick = {
                    backgroundIndex = (backgroundIndex + 1) % backgroundColors.size
                },
                modifier = Modifier
                    .background(
                        Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Change Background",
                    tint = Color(0xFF4CAF50)
                )
            }
        }

        // Bunny Animation
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 280.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BunnyAnimation(state = bunnyState, health = health, backgroundIndex = backgroundIndex)

            // Daily Calories Display
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Today's Calories: $totalCalories",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                ),
                modifier = Modifier
                    .background(
                        Color.White.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )

            // AI Status Indicator
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (apiKey.isNullOrEmpty()) "Using Local AI" else "Qwen AI Active 🤖",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (apiKey.isNullOrEmpty()) Color(0xFF666666) else Color(0xFF4CAF50)
                ),
                modifier = Modifier
                    .background(
                        Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(15.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Bottom UI
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Health Section
            Text(
                text = "Health: ${health}%",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E7D32)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Health Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(
                        Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        Color.Gray.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(health / 100f)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = when {
                                    health < 20 -> listOf(Color.Red, Color(0xFFFF5722))
                                    health < 50 -> listOf(Color(0xFFFF9800), Color(0xFFFFB74D))
                                    else -> listOf(Color(0xFF4CAF50), Color(0xFF81C784))
                                }
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chat Button
            Button(
                onClick = { showChatDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(28.dp),
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "Chat",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isProcessing) "Processing..." else "Tell me what you ate",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                )
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        QwenSettingsDialog(
            currentApiKey = apiKey,
            onApiKeyUpdated = { newApiKey ->
                apiKey = newApiKey
                dataManager.saveOpenRouterApiKey(newApiKey ?: "")
                foodProcessor = QwenProcessor(newApiKey)
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Chat Dialog
    if (showChatDialog) {
        ChatDialog(
            messages = chatMessages,
            foodProcessor = foodProcessor,
            isProcessing = isProcessing,
            onMessageSent = { message ->
                isProcessing = true

                // Add user message
                val newMessages = chatMessages + ChatMessage(
                    text = message,
                    isUser = true
                )
                chatMessages = newMessages
                dataManager.saveChatMessages(newMessages)

                // Process with Qwen AI
                scope.launch {
                    try {
                        val (aiResponse, calories, foods) = foodProcessor.processMessage(message, chatMessages)

                        // Add AI response
                        val updatedMessages = chatMessages + ChatMessage(
                            text = aiResponse,
                            isUser = false,
                            calories = calories,
                            foodItems = foods
                        )
                        chatMessages = updatedMessages
                        dataManager.saveChatMessages(updatedMessages)

                        // Update food entries and calories
                        if (calories > 0) {
                            val newEntries = foods.map { food ->
                                FoodEntry(
                                    name = food,
                                    calories = calories / foods.size.coerceAtLeast(1)
                                )
                            }
                            foodEntries = foodEntries + newEntries
                            dataManager.saveFoodEntries(foodEntries)

                            totalCalories = dataManager.getTodayCalories()

                            // Update bunny health and trigger eating animation
                            health = (health + (calories / 50).coerceAtMost(30)).coerceAtMost(100)
                            dataManager.saveHealth(health)
                            bunnyState = "eating" // This triggers the eating animation
                        }
                    } catch (e: Exception) {
                        // Add error message
                        val errorMessage = ChatMessage(
                            text = "Sorry, I couldn't process your message. Please check your internet connection and try again.",
                            isUser = false
                        )
                        chatMessages = chatMessages + errorMessage
                        dataManager.saveChatMessages(chatMessages)
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onDismiss = { showChatDialog = false }
        )
    }
}

@Composable
fun QwenSettingsDialog(
    currentApiKey: String?,
    onApiKeyUpdated: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(TextFieldValue(currentApiKey ?: "")) }
    var isTestingApi by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "🤖 Qwen AI Settings",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "OpenRouter API Key",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.Gray.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    BasicTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            testResult = null // Clear test result when key changes
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = Color.Black
                        ),
                        decorationBox = { innerTextField ->
                            if (apiKeyInput.text.isEmpty()) {
                                Text(
                                    text = "Paste your OpenRouter API key here (starts with sk-or-v1-)",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test API Key Button
                if (apiKeyInput.text.isNotBlank()) {
                    Button(
                        onClick = {
                            scope.launch {
                                isTestingApi = true
                                testResult = null

                                val processor = QwenProcessor(apiKeyInput.text.trim())
                                val (success, message) = processor.testApiKey()

                                testResult = message
                                isTestingApi = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        ),
                        enabled = !isTestingApi
                    ) {
                        if (isTestingApi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing API Key...", color = Color.White)
                        } else {
                            Text("🧪 Test API Key", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test Result Display
                    testResult?.let { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (result.contains("✅"))
                                    Color(0xFFE8F5E8)
                                else
                                    Color(0xFFFFEBEE)
                            )
                        ) {
                            Text(
                                text = result,
                                modifier = Modifier.padding(12.dp),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = if (result.contains("✅"))
                                        Color(0xFF2E7D32)
                                    else
                                        Color(0xFFD32F2F)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Instructions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF0F8FF)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "📋 How to get FREE OpenRouter API Key:",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        )
                        Text(
                            text = """1. Visit openrouter.ai
2. Sign up/Login (FREE account)
3. Go to Settings → Keys
4. Create new API key
5. Key format: sk-or-v1-xxxxx
6. Qwen models are FREE!""",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = Color(0xFF666666)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Current Status
                val statusText = when {
                    currentApiKey.isNullOrEmpty() -> "Currently using: Local AI only"
                    testResult?.contains("✅") == true -> "Currently using: Qwen AI (Connected ✅)"
                    else -> "Currently using: ${currentApiKey.take(15)}..."
                }

                Text(
                    text = statusText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.Gray
                        )
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            onApiKeyUpdated(null) // Clear API key
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFFF5722)
                        )
                    ) {
                        Text("Clear Key")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val trimmedKey = apiKeyInput.text.trim().takeIf { it.isNotBlank() }
                            onApiKeyUpdated(trimmedKey)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatDialog(
    messages: List<ChatMessage>,
    foodProcessor: QwenProcessor,
    isProcessing: Boolean,
    onMessageSent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var messageInput by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Food Chat with Qwen AI 🤖",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    )

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🍎 Try saying:",
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF2E7D32)
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                foodProcessor.getSuggestions().forEach { suggestion ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFF5F5F5)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            modifier = Modifier.padding(12.dp),
                                            style = TextStyle(
                                                fontSize = 14.sp,
                                                color = Color(0xFF666666)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(messages) { message ->
                        ChatMessageBubble(message = message)
                    }

                    if (isProcessing) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Card(
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .padding(end = 50.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFF5F5F5)
                                    ),
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomEnd = 16.dp,
                                        bottomStart = 4.dp
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color(0xFF4CAF50)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Qwen is thinking...",
                                            style = TextStyle(
                                                fontSize = 14.sp,
                                                color = Color(0xFF666666)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                Color.Gray.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        BasicTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = Color.Black
                            ),
                            decorationBox = { innerTextField ->
                                if (messageInput.text.isEmpty()) {
                                    Text(
                                        text = "Ask Qwen about food and nutrition...",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            color = Color.Gray
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (messageInput.text.isNotBlank() && !isProcessing) {
                                onMessageSent(messageInput.text)
                                messageInput = TextFieldValue()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (messageInput.text.isNotBlank() && !isProcessing)
                                    Color(0xFF4CAF50)
                                else
                                    Color.Gray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(24.dp)
                            ),
                        enabled = messageInput.text.isNotBlank() && !isProcessing
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeText = dateFormat.format(Date(message.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(
                    start = if (message.isUser) 50.dp else 0.dp,
                    end = if (message.isUser) 0.dp else 50.dp
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser)
                    Color(0xFF4CAF50)
                else
                    Color(0xFFF5F5F5)
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = if (message.isUser) Color.White else Color.Black
                    )
                )

                if (message.calories != null && message.calories > 0) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .background(
                                if (message.isUser)
                                    Color.White.copy(alpha = 0.2f)
                                else
                                    Color(0xFF4CAF50).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔥 ${message.calories} cal",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (message.isUser) Color.White else Color(0xFF4CAF50)
                            )
                        )

                        if (message.foodItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• ${message.foodItems.joinToString(", ")}",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = if (message.isUser)
                                        Color.White.copy(alpha = 0.8f)
                                    else
                                        Color(0xFF666666)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = timeText,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = if (message.isUser)
                            Color.White.copy(alpha = 0.7f)
                        else
                            Color.Gray
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun BunnyAnimation(state: String, health: Int, backgroundIndex: Int) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(GifDecoder.Factory())
            }
            .build()
    }

    var eatingAnimationIndex by remember { mutableStateOf(0) }

    LaunchedEffect(state) {
        if (state == "eating") {
            while (true) {
                delay(1000)
                eatingAnimationIndex = (eatingAnimationIndex + 1) % 4
            }
        }
    }

    val bunnyResourceId = when (state) {
        "eating" -> {
            when (eatingAnimationIndex) {
                0 -> R.drawable.bunny_eating_2
                1 -> R.drawable.bunny_eating_2
                2 -> R.drawable.bunny_eating_2
                3 -> R.drawable.bunny_eating_2
                else -> R.drawable.bunny_idle
            }
        }
        else -> {
            when {
                health < 20 -> R.drawable.bunny_eating_2
                health < 50 -> R.drawable.bunny_eating_2
                else -> R.drawable.bunny_eating_2
            }
        }
    }

    // Match the dynamic background colors from your app
    val backgroundColors = listOf(
        Color(0xFFFFFFFF)  // White
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .background(
                // Match the current app background
                backgroundColors[backgroundIndex % backgroundColors.size],
                shape = RoundedCornerShape(100.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = bunnyResourceId,
            contentDescription = "Bunny Animation",
            imageLoader = imageLoader,
            modifier = Modifier.size(150.dp),
            contentScale = ContentScale.Fit
        )
    }
}