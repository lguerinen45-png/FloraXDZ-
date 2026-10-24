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

data class PlantInfo(
    val scientificName: String,
    val commonName: String,
    val arabicName: String,
    val family: String,
    val description: String,
    val medicinalUses: String,
    val ecologicalUses: String,
    val distribution: String
)

val algerianPlants = listOf(
    PlantInfo("Pistacia atlantica", "Pistachier de l'Atlas", "بطم أطلسي", "Anacardiaceae", "Arbre rustique pouvant atteindre 7m de hauteur. Feuilles composées, coriaces. Fruits rouges devenant noirs à maturité.", "Résine utilisée contre les troubles digestifs. Feuilles en infusion pour les problèmes respiratoires. Fruits comestibles riches en antioxydants.", "Fixation des sols en zones arides. Refuge pour la faune sauvage. Lutte contre la désertification.", "Atlas tellien, Hauts plateaux, Aures"),
    PlantInfo("Pistacia terebinthus", "Pistachier térébinthe", "بطم terpentين", "Anacardiaceae", "Arbuste ou petit arbre de 3-5m. Feuilles imparipennées. Odeur résineuse caractéristique.", "Térébenthine utilisée en aromathérapie. Écorce astringente. Huile essentielle antiseptique.", "Maquis méditerranéen. Nourriture pour les oiseaux. Protection des sols.", "Régions côtières, Tell algérien"),
    PlantInfo("Ziziphus lotus", "Jujubier", "سدر", "Rhamnaceae", "Arbuste épineux de 2-4m. Petites feuilles ovales. Fruits charnus comestibles (jujubes).", "Fruits calmants et nutritifs. Feuilles en décoction contre l'insomnie. Écorce fébrifuge.", "Brise-vent naturel. Nourriture pour le bétail. Fixation des dunes.", "Sahara, Hauts plateaux, régions semi-arides"),
    PlantInfo("Olea europaea", "Olivier", "زيتون", "Oleaceae", "Arbre emblématique méditerranéen. Feuilles persistantes argentées. Fruits (olives) pour l'huile.", "Huile d'olive pour la peau et les brûlures. Feuilles hypotensives. Écorce fébrifuge.", "Culture millénaire. Lutte contre l'érosion. Biodiversité agricole.", "Kabylie, Tell, régions côtières"),
    PlantInfo("Stipa tenacissima", "Alfa", "حلفاء", "Poaceae", "Graminée vivace formant des touffes denses. Feuilles longues et fines. Racines profondes.", "Fibres pour l'artisanat (vannerie, cordes). Cendres pour le savon traditionnel.", "Fixation des sols steppiques. Pâturage pour les ovins. Lutte contre la désertification.", "Steppes, Hauts plateaux, régions semi-arides"),
    PlantInfo("Phoenix dactylifera", "Palmier dattier", "نخيل", "Arecaceae", "Palmier de 15-25m. Stipe unique. Feuilles pennées de 3-5m. Fruits (dattes) très nutritifs.", "Dattes énergétiques et riches en minéraux. Pollen stimulant. Noyaux en infusion digestive.", "Oasis sahariennes. Microclimat favorable. Écosystème oasien complet.", "Sahara, Biskra, El Oued, Adrar"),
    PlantInfo("Ceratonia siliqua", "Caroubier", "خروب", "Fabaceae", "Arbre de 10-15m. Feuilles composées persistantes. Gousses brunes comestibles (caroubes).", "Pulpe de caroube contre la diarrhée. Gomme pour l'industrie alimentaire. Feuilles astringentes.", "Fixation des sols calcaires. Nourriture pour le bétail. Ombrage en zones arides.", "Tell, Kabylie, régions méditerranéennes"),
    PlantInfo("Quercus ilex", "Chêne vert", "بلوط أخضر", "Fagaceae", "Arbre de 10-20m. Feuilles persistantes coriaces. Glands comestibles après traitement.", "Écorce tannique pour le tannage. Glands nutritifs. Feuilles en infusion antiseptique.", "Forêts méditerranéennes. Refuge faunique. Protection des sources.", "Kabylie, Tell, Atlas blidéen"),
    PlantInfo("Cupressus sempervirens", "Cyprès", "سرو", "Cupressaceae", "Arbre de 15-25m. Port colonnaire caractéristique. Feuilles en écailles. Cônes ligneux.", "Huile essentielle anticatarrhale. Bois imputrescible. Feuilles en infusion veinotonique.", "Brise-vent. Cimetières et jardins. Lutte contre l'érosion éolienne.", "Régions côtières, Tell, jardins"),
    PlantInfo("Pinus halepensis", "Pin d'Alep", "صنوبر حلبي", "Pinaceae", "Conifère de 15-20m. Écorce rougeâtre. Aiguilles groupées par 2. Cônes petits et symétriques.", "Résine (térébenthine) antiseptique. Aiguilles en infusion vitaminées. Écorce astringente.", "Reboisement. Fixation des sols. Habitat pour la faune forestière.", "Tell, Atlas, régions montagneuses")
)

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
var currentScreen by mutableStateOf("main")
var selectedPlant by mutableStateOf<PlantInfo?>(null)

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
            Text("", fontSize = 100.sp)
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

    val organs = listOf("auto" to " Auto", "leaf" to "🍃 Feuille", "flower" to " Fleur", "fruit" to "🍎 Fruit", "bark" to "🌳 Écorce", "stem" to "🌱 Tige")

    when (currentScreen) {
        "main" -> MainContent(context, scope, hasCameraPermission, cameraLauncher, galleryLauncher, permissionLauncher, organs)
        "history" -> HistoryScreen(context)
        "catalogue" -> CatalogueScreen()
        "plantDetail" -> PlantDetailScreen()
    }
}

@Composable
fun MainContent(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    hasCameraPermission: Boolean,
    cameraLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Void?, Bitmap?>,
    galleryLauncher: androidx.activity.compose.ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    permissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
    organs: List<Pair<String, String>>
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("FloraXDZ", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                Row {
                    TextButton(onClick = { currentScreen = "catalogue" }) { Text("📚 Catalogue") }
                    TextButton(onClick = { currentScreen = "history" }) { Text("📜 Historique") }
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
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Plante", modifier = Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Crop)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    isProcessing = true
                    identificationResult = "Analyse en cours..."
                    scope.launch { identifyPlantFromBitmap(context, bitmap, selectedOrgan) }
                }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing) {
                    if (isProcessing) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White); Spacer(modifier = Modifier.width(8.dp)) }
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

@Composable
fun HistoryScreen(context: Context) {
    val historyList = getHistory(context)
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Historique", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            TextButton(onClick = { currentScreen = "main" }) { Text("← Retour") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (historyList.isEmpty()) {
            Text("Aucune plante identifiée.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                historyList.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        val scientific = item.getString("scientific")
                        val plant = algerianPlants.find { it.scientificName == scientific }
                        if (plant != null) {
                            selectedPlant = plant
                            currentScreen = "plantDetail"
                        }
                    }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.getString("name"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(item.getString("scientific"), fontStyle = FontStyle.Italic, fontSize = 12.sp, color = Color.DarkGray)
                            Text("Confiance: ${item.getInt("confidence")}%", fontSize = 12.sp)
                            Text(item.getString("date"), fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogueScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Catalogue des plantes", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            TextButton(onClick = { currentScreen = "main" }) { Text("← Retour") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            algerianPlants.forEach { plant ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    selectedPlant = plant
                    currentScreen = "plantDetail"
                }) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(plant.commonName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(plant.scientificName, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = Color.DarkGray)
                        Text(plant.arabicName, fontSize = 14.sp, color = NaamaGreen)
                    }
                }
            }
        }
    }
}

@Composable
fun PlantDetailScreen() {
    val plant = selectedPlant ?: return
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fiche détaillée", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            TextButton(onClick = { currentScreen = "catalogue" }) { Text("← Retour") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(plant.commonName, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(plant.scientificName, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = Color.DarkGray)
                Text(plant.arabicName, fontSize = 18.sp, color = NaamaGreen)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Famille: ${plant.family}", fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📖 Description", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(plant.description, fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("💊 Usages médicinaux", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(plant.medicinalUses, fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🌍 Usages écologiques", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(plant.ecologicalUses, fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📍 Répartition en Algérie", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(plant.distribution, fontSize = 14.sp)
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
            
            // CORRECTION ICI : L'URL utilise "all" comme projet, et l'organe est passé en paramètre
            var url = "https://my-api.plantnet.org/v2/identify/all?api-key=$PLANTNET_API_KEY&include-related-images=false&no-reject=false&nb-results=5&lang=fr"
            if (organ != "auto") {
                url += "&organs=$organ"
            }
            
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
