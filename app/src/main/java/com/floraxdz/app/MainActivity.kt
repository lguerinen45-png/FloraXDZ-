package com.floraxdz.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.coroutines.resume

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

data class PlantLocation(
    val name: String,
    val scientificName: String,
    val latitude: Double,
    val longitude: Double,
    val date: String,
    val confidence: Int
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
var gpsStatusMessage by mutableStateOf("")

@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    
    if (!isGPSEnabled && !isNetworkEnabled) {
        withContext(Dispatchers.Main) {
            gpsStatusMessage = "GPS désactivé. Activez-le dans les paramètres."
        }
        return null
    }
    
    return suspendCancellableCoroutine { continuation ->
        var locationListener: LocationListener? = null
        
        val timeoutJob = kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(10000)
            if (continuation.isActive) {
                locationListener?.let { locationManager.removeUpdates(it) }
                continuation.resume(null)
            }
        }
        
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (continuation.isActive) {
                    timeoutJob.cancel()
                    locationManager.removeUpdates(this)
                    continuation.resume(Pair(location.latitude, location.longitude))
                }
            }
            
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        
        try {
            if (isGPSEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener!!)
            } else if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, locationListener!!)
            }
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }
}

fun saveToHistory(context: Context, plantName: String, scientificName: String, confidence: Int, latitude: Double? = null, longitude: Double? = null) {
    val prefs = context.getSharedPreferences("FloraXDZ_History", Context.MODE_PRIVATE)
    val historyJson = prefs.getString("history", "[]") ?: "[]"
    val jsonArray = JSONArray(historyJson)
    
    val newItem = JSONObject().apply {
        put("name", plantName)
        put("scientific", scientificName)
        put("confidence", confidence)
        put("date", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
        put("latitude", latitude ?: 0.0)
        put("longitude", longitude ?: 0.0)
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

fun getLocations(context: Context): List<PlantLocation> {
    val history = getHistory(context)
    return history.map { item ->
        PlantLocation(
            name = item.getString("name"),
            scientificName = item.getString("scientific"),
            latitude = item.optDouble("latitude", 0.0),
            longitude = item.optDouble("longitude", 0.0),
            date = item.getString("date"),
            confidence = item.getInt("confidence")
        )
    }.filter { it.latitude != 0.0 && it.longitude != 0.0 }
}

fun addSampleLocations(context: Context) {
    val samplePlants = listOf(
        Triple("Pistacia atlantica", 33.2735, 0.3087),
        Triple("Olea europaea", 36.7538, 3.0588),
        Triple("Phoenix dactylifera", 34.8481, 5.7281)
    )
    
    samplePlants.forEach { (scientific, lat, lng) ->
        val plant = algerianPlants.find { it.scientificName == scientific }
        if (plant != null) {
            saveToHistory(context, plant.commonName, plant.scientificName, 85, lat, lng)
        }
    }
}

@Composable
fun FloraXDZApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
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
            Text("FloraXDZ", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Application d'identification", fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Text("des plantes d'Algérie", fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.width(80.dp).height(4.dp).background(NaamaRed))
            Spacer(modifier = Modifier.height(24.dp))
            Text("Université de Naâma", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Conçu par", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            Text("Pr. GUERINE Lakhdar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Yellow, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Algérie 🇩🇿 • 2026", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
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

    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) { selectedBitmap = bitmap; selectedImageUri = null }
    }

    val galleryLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        if (uri != null) { selectedImageUri = uri; selectedBitmap = null }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (hasCameraPermission) cameraLauncher.launch(null)
    }

    val organs = listOf("auto" to " Auto", "leaf" to "🍃 Feuille", "flower" to "🌸 Fleur", "fruit" to "🍎 Fruit", "bark" to "🌳 Écorce", "stem" to "🌱 Tige")

    when (currentScreen) {
        "main" -> MainContent(context, scope, hasCameraPermission, hasLocationPermission, cameraLauncher, galleryLauncher, permissionLauncher, organs)
        "history" -> HistoryScreen(context)
        "catalogue" -> CatalogueScreen()
        "plantDetail" -> PlantDetailScreen()
        "map" -> MapScreen(context)
    }
}

@Composable
fun MainContent(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    hasCameraPermission: Boolean,
    hasLocationPermission: Boolean,
    cameraLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Void?, Bitmap?>,
    galleryLauncher: androidx.activity.compose.ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    permissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    organs: List<Pair<String, String>>
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("FloraXDZ", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Identifiez les plantes en Algérie", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { currentScreen = "catalogue" }) { Text("📚 Catalogue") }
                TextButton(onClick = { currentScreen = "history" }) { Text(" Historique") }
                TextButton(onClick = { currentScreen = "map" }) { Text("️ Carte") }
            }
            Spacer(modifier = Modifier.height(16.dp))

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
                Button(onClick = {
                    val permissions = mutableListOf(Manifest.permission.CAMERA)
                    if (!hasLocationPermission) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    permissionLauncher.launch(permissions.toTypedArray())
                }, modifier = Modifier.weight(1f)) {
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
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Pr. GUERINE Lakhdar • 2026", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                            val lat = item.optDouble("latitude", 0.0)
                            val lng = item.optDouble("longitude", 0.0)
                            if (lat != 0.0 && lng != 0.0) {
                                Text("📍 Position: $lat, $lng", fontSize = 10.sp, color = NaamaGreen)
                            } else {
                                Text("📍 Position non enregistrée", fontSize = 10.sp, color = Color.Gray)
                            }
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

@Composable
fun MapScreen(context: Context) {
    val locations = getLocations(context)
    var showAddSamples by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Carte des découvertes", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Row {
                if (locations.isEmpty()) {
                    Button(onClick = { showAddSamples = true }) {
                        Text("📍 Ajouter exemples")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = { currentScreen = "main" }) { Text("← Retour") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (showAddSamples) {
            AlertDialog(
                onDismissRequest = { showAddSamples = false },
                title = { Text("Ajouter des exemples") },
                text = { Text("Voulez-vous ajouter 3 plantes exemples (Naâma, Alger, Biskra) pour tester la carte ?") },
                confirmButton = {
                    Button(onClick = {
                        addSampleLocations(context)
                        showAddSamples = false
                    }) {
                        Text("Ajouter")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSamples = false }) {
                        Text("Annuler")
                    }
                }
            )
        }
        
        if (locations.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Aucune plante géolocalisée", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pour voir la carte fonctionner :", fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text("1. Cliquez sur 'Ajouter exemples' ci-dessus", fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text("2. OU identifiez une nouvelle plante avec internet + GPS activé", fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            Text("${locations.size} découverte(s) géolocalisée(s)", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Carte avec WebView - VERSION CORRIGÉE
            val mapHtml = generateMapHtml(locations)
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        // Utiliser loadData avec encodage base64 pour éviter les blocages CORS
                        val encodedHtml = Base64.encodeToString(mapHtml.toByteArray(Charsets.UTF_8), Base64.NO_PADDING)
                        loadData(encodedHtml, "text/html; charset=utf-8", "base64")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, NaamaGreen, RoundedCornerShape(12.dp))
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                locations.forEach { loc ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(loc.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(loc.scientificName, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = Color.DarkGray)
                            Text("📍 ${loc.latitude}, ${loc.longitude}", fontSize = 12.sp, color = NaamaGreen)
                            Text(loc.date, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

fun generateMapHtml(locations: List<PlantLocation>): String {
    val markers = locations.map { loc ->
        "L.marker([${loc.latitude}, ${loc.longitude}]).addTo(map).bindPopup('<b>${loc.name}</b><br><i>${loc.scientificName}</i><br>${loc.date}');"
    }.joinToString("\n")
    
    val centerLat = if (locations.isNotEmpty()) locations.map { it.latitude }.average() else 28.0
    val centerLng = if (locations.isNotEmpty()) locations.map { it.longitude }.average() else 3.0
    
    // HTML complet avec Leaflet CSS et JS intégrés depuis CDN
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" crossorigin=""/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" crossorigin=""></script>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html, body { width: 100%; height: 100%; overflow: hidden; }
        #map { width: 100%; height: 100%; }
        .leaflet-popup-content-wrapper { border-radius: 8px; }
        .leaflet-popup-content { margin: 12px; font-family: sans-serif; }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        // Initialisation de la carte
        var map = L.map('map', {
            center: [$centerLat, $centerLng],
            zoom: 6,
            zoomControl: true
        });
        
        // Ajout des tuiles OpenStreetMap
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors',
            maxZoom: 19
        }).addTo(map);
        
        // Ajout des marqueurs
        $markers
        
        // Forcer le recalcul de la taille après chargement
        setTimeout(function() {
            map.invalidateSize();
        }, 100);
    </script>
</body>
</html>
    """.trimIndent()
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
                
                val location = getCurrentLocation(context)
                
                if (bestName.isNotEmpty() && bestName != "Nom commun non disponible") {
                    saveToHistory(context, bestName, bestScientific, bestConfidence, location?.first, location?.second)
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
