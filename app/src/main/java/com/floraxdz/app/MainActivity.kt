package com.floraxdz.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.InputStream

const val PLANTNET_API_KEY = "2b101jvJGNIYa6n1Vp1TmrV5u"

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
var identificationResult by mutableStateOf<String?>(null)
var isProcessing by mutableStateOf(false)

@Composable
fun FloraXDZApp() {
    var showSplash by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        showSplash = false
    }
    
    if (showSplash) {
        SplashScreen()
    } else {
        MainScreen()
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(NaamaGreen, NaamaGreenLight)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🌿", fontSize = 100.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Université de Naâma", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.width(60.dp).height(4.dp).background(NaamaRed))
            Spacer(modifier = Modifier.height(24.dp))
            Text("Pr. GUERINE Lakhdar", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Algérie 🇩", fontSize = 18.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🌿", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("FloraXDZ", fontWeight = FontWeight.Bold, fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Identifiez les plantes en Algérie", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "image/*"
                    context.startActivity(Intent.createChooser(intent, "Sélectionner une image"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choisir une photo de plante")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            selectedImageUri?.let { uri ->
                val bitmap = remember(uri) {
                    try {
                        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Plante sélectionnée",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            isProcessing = true
                            identificationResult = null
                            scope.launch {
                                identifyPlant(context, uri)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isProcessing) "Identification en cours..." else "Identifier la plante")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            identificationResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
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

suspend fun identifyPlant(context: android.content.Context, uri: Uri) {
    try {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val byteArray = inputStream?.readBytes()
            inputStream?.close()
            
            if (byteArray != null) {
                val client = OkHttpClient()
                val mediaType = MediaType.parse("image/jpeg")
                val requestBody = RequestBody.create(mediaType, byteArray)
                
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("images", "plant.jpg", requestBody)
                    .build()
                
                val request = Request.Builder()
                    .url("https://my-api.plantnet.org/v2/identify/all?api-key=$PLANTNET_API_KEY&include-related-images=false&no-reject=false&nb-results=5&lang=fr")
                    .post(multipartBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val results = json.getJSONArray("results")
                    
                    var resultText = ""
                    for (i in 0 until minOf(results.length(), 3)) {
                        val result = results.getJSONObject(i)
                        val species = result.getJSONObject("species")
                        val scientificName = species.getString("scientificNameWithoutAuthor")
                        val commonNames = species.optJSONArray("commonNames")
                        val commonName = if (commonNames != null && commonNames.length() > 0) {
                            commonNames.getString(0)
                        } else {
                            "Nom commun non disponible"
                        }
                        val score = result.getDouble("score")
                        val confidence = (score * 100).toInt()
                        
                        resultText += "${i + 1}. $commonName\n"
                        resultText += "   Nom scientifique: $scientificName\n"
                        resultText += "   Confiance: $confidence%\n\n"
                    }
                    
                    withContext(Dispatchers.Main) {
                        identificationResult = if (resultText.isNotEmpty()) {
                            resultText.trimEnd()
                        } else {
                            "Aucun résultat trouvé. Essayez une autre photo."
                        }
                        isProcessing = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        identificationResult = "Erreur lors de l'identification. Vérifiez votre connexion internet."
                        isProcessing = false
                    }
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
