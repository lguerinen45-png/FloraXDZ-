package com.floraxdz.app

import android.Manifest
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
var selectedOrgan by mutableStateOf("auto") // auto, leaf, flower, fruit, bark, stem

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

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            selectedImageUri = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            selectedBitmap = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }

    val organs = listOf("auto" to "Automatique", "leaf" to " Feuille", "flower" to "🌸 Fleur", "fruit" to "🍎 Fruit", "bark" to "🌳 Écorce", "stem" to "🌱 Tige")

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌿", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("FloraXDZ", fontWeight = FontWeight.Bold, fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Identifiez les plantes en Algérie", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            // Menu déroulant pour choisir l'organe
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = organs.first { it.first == selectedOrgan }.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Organe de la plante") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    organs.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedOrgan = value
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Conseils photo
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5DC))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📸 Conseils pour une meilleure identification :", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Photo nette et bien éclairée", fontSize = 12.sp)
                    Text("• Un seul organe (feuille OU fleur)", fontSize = 12.sp)
                    Text("• La plante doit remplir 70% de la photo", fontSize = 12.sp)
                    Text("• Fond neutre si possible", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    if (hasCameraPermission) {
                        cameraLauncher.launch(null)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }, modifier = Modifier.weight(1f)) {
                    Text("📷", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Caméra")
                }
                
                Button(onClick = { galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Text("🖼️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Galerie")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val imageToShow = selectedBitmap ?: selectedImageUri?.let { uri ->
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) { null }
            }

            imageToShow?.let { bitmap ->
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Plante", modifier = Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Crop)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    isProcessing = true
                    identificationResult = "Analyse en cours..."
                    scope.launch { identifyPlantFromBitmap(bitmap, selectedOrgan) }
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

suspend fun identifyPlantFromBitmap(bitmap: Bitmap, organ: String) {
    try {
        withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val byteArray = outputStream.toByteArray()

            val client = OkHttpClient.Builder().connectTimeout(45, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).writeTimeout(45, TimeUnit.SECONDS).build()
            val requestBody = byteArray.toRequestBody("image/jpeg".toMediaType())
            val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("images", "plant.jpg", requestBody).build()
            
            // URL avec le paramètre d'organe
            val url = "https://my-api.plantnet.org/v2/identify/$organ?api-key=$PLANTNET_API_KEY&include-related-images=false&no-reject=false&nb-results=5&lang=fr"
            val request = Request.Builder().url(url).post(multipartBody).build()

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
                    val commonName = if (commonNames != null && commonNames.length() > 0) commonNames.getString(0) else "Nom commun non disponible"
                    val score = result.getDouble("score")
                    val confidence = (score * 100).toInt()
                    resultText += "${i + 1}. $commonName\n   Nom scientifique: $scientificName\n   Confiance: $confidence%\n\n"
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
