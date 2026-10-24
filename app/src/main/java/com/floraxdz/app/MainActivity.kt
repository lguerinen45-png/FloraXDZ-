package com.floraxdz.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

const val PLANTNET_API_KEY = "2b101jvIJGNlYa6n1Vp1TmrV5u"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FloraXDZApp()
            }
        }
    }
}

var selectedImageUri by mutableStateOf<Uri?>(null)
var selectedBitmap by mutableStateOf<Bitmap?>(null)
var identificationResult by mutableStateOf<String?>(null)
var isProcessing by mutableStateOf(false)
var selectedOrgan by mutableStateOf("auto")

fun saveToHistory(context: Context, plantName: String, scientificName: String, confidence: Int) {
    val prefs = context.getSharedPreferences("FloraXDZ_History", Context.MODE_PRIVATE)
    val historyJson = prefs.getString("history", "[]") ?: "[]"
    val jsonArray = JSONArray(historyJson)
    
    val newItem = JSONObject().apply {
        put("name", plantName)
        put("scientific", scientificName)
        put("confidence", confidence)
        put("date", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
    }
    jsonArray.put(newItem)
    prefs.edit().putString("history", jsonArray.toString()).apply()
}

fun getHistory(context: Context): List<JSONObject> {
    val prefs = context.getSharedPreferences("FloraXDZ_History", Context.MODE_PRIVATE)
    val historyJson = prefs.getString("history", "[]") ?: "[]"
    val jsonArray = JSONArray(historyJson)
    val list = mutableListOf<JSONObject>()
    for (i in 0 until jsonArray.length()) {
        list.add(jsonArray.getJSONObject(i))
    }
    return list.reversed()
}

@Composable
fun FloraXDZApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        showSplash = false
    }
    if (showSplash) SplashScreen() else MainScreen()
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(colors = listOf(NaamaGreen, NaamaGreenLight))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("🌿", fontSize = 100.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Université de Naâma", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.width(60.dp).height(4.dp).background(NaamaRed))
            Spacer(modifier = Modifier.height(24.dp))
            Text("Pr. GUERINE Lakhdar", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Algérie 🇩🇿", fontSize = 18.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHistory by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf(getHistory(context)) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) { selectedBitmap = bitmap; selectedImageUri = null }
    }

    val galleryLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        if (uri != null) { selectedImageUri = uri; selectedBitmap = null }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) cameraLauncher.launch(null)
    }

    val organs = listOf("auto" to " Auto", "leaf" to "🍃 Feuille", "flower" to "🌸 Fleur", "fruit" to "🍎 Fruit", "bark" to "🌳 Écorce", "stem" to "🌱 Tige")

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Historique des identifications") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    if (historyList.isEmpty()) {
                        Text("Aucune plante identifiée pour le moment.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    } else {
                        historyList.forEach { item ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.getString("name"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(item.getString("scientific"), fontStyle = FontStyle.Italic, fontSize = 12.sp, color = Color.DarkGray)
                                    Text("Confiance: ${item.getInt("confidence")}%", fontSize = 12.sp)
                                    Text(item.getString("date"), fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("Fermer") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    context.getSharedPreferences("FloraXDZ_History", Context.MODE_PRIVATE).edit().remove("history").apply()
                    historyList = getHistory(context)
                }) { Text("Effacer", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("FloraXDZ", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { 
                    historyList = getHistory(context)
                    showHistory = true 
                }) {
                    Text("📜 Historique")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Identifiez les plantes en Algérie", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            Text("Quel organe photographiez-vous ?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            val rows = organs.chunked(3)
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (value, label) ->
                        val isSelected = selectedOrgan == value
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NaamaGreen else Color.LightGray.copy(alpha = 0.3f))
                                .border(width = 1.dp, color = if (isSelected) NaamaGreen else Color.Gray, shape = RoundedCornerShape(8.dp))
                                .clickable { selectedOrgan = value }.padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = label, fontSize = 12.sp, color = if (isSelected) Color.White else Color.Black, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📸 Conseils :", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("• Photo nette et bien éclairée", fontSize = 12.sp)
                    Text("• Un seul organe à la fois", fontSize = 12.sp)
                    Text("• Plante = 70% de la photo", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { if (hasCameraPermission) cameraLauncher.launch(null) else permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f)) {
                    Text("📷", fontSize = 24.sp); Spacer(modifier = Modifier.width(8.dp)); Text("Caméra")
                }
                Button(onClick = { galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Text("🖼️", fontSize = 24.sp); Spacer(modifier = Modifier.width(8.dp)); Text("Galerie")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            val imageToShow = selectedBitmap ?: selectedImageUri?.let { uri ->
                try { val inputStream: InputStream? = context.contentResolver.openInputStream(uri); BitmapFactory.decodeStream(inputStream) } catch (e: Exception) { null }
            }

            imageToShow?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Plante",
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    isProcessing = true
                    identificationResult = "Analyse en cours..."
                    scope.launch { identifyPlantFromBitmap(context, bitmap, selectedOrgan) }
                }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing) {
                    if (isProcessing) { 
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp)) 
                    }
                    Text(if (isProcessing) "Identification..." else "Identifier la plante")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            identificationResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Résultat :", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(result, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

suspend fun identifyPlantFromBitmap(context: Context, bitmap: Bitmap, organ: String) {
    try {
        withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val byteArray = outputStream.toByteArray()

            val client = OkHttpClient.Builder().connectTimeout(45, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).writeTimeout(45, TimeUnit.SECONDS).build()
            val requestBody = byteArray.toRequestBody("image/jpeg".toMediaType())
            val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("images", "plant.jpg", requestBody).build()
            
            val url = "https://my-api.plantnet.org/v2/identify/$organ?api-key=$PLANTNET_API_KEY&include-related-images=false&no-reject=false&nb-results=5&lang=fr"
            val request = Request.Builder().url(url).post(multipartBody).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val results = json.getJSONArray("results")
                var resultText = ""
                
                var bestName = ""
                var bestScientific = ""
                var bestConfidence = 0

                for (i in 0 until minOf(results.length(), 3)) {
                    val result = results.getJSONObject(i)
                    val species = result.getJSONObject("species")
                    val scientificName = species.getString("scientificNameWithoutAuthor")
                    val commonNames = species.optJSONArray("commonNames")
                    val commonName = if (commonNames != null && commonNames.length() > 0) commonNames.getString(0) else "Nom commun non disponible"
                    val score = result.getDouble("score")
                    val confidence = (score * 100).toInt()
                    
                    if (i == 0) {
                        bestName = commonName
                        bestScientific = scientificName
                        bestConfidence = confidence
                    }

                    resultText += "${i + 1}. $commonName\n   Nom scientifique: $scientificName\n   Confiance: $confidence%\n\n"
                }
                
                if (bestName.isNotEmpty() && bestName != "Nom commun non disponible") {
                    saveToHistory(context, bestName, bestScientific, bestConfidence)
                }

                withContext(Dispatchers.Main) {
                    identificationResult = if (resultText.isNotEmpty()) resultText.trimEnd() else "Aucun résultat trouvé."
                    isProcessing = false
                }
            } else {
                withContext(Dispatchers.Main) {
                    identificationResult = "Erreur du serveur PlantNet. Code: ${response.code}"
                    isProcessing = false
                }
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            identificationResult = "Erreur: ${e.message}"
            isProcessing = false
        }
    }
}

val NaamaGreen = Color(0xFF2E7D32)
val NaamaGreenLight = Color(0xFF4CAF50)
val NaamaRed = Color(0xFFD32F2F)
