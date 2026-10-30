package com.floraxdz.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.delay
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
import kotlin.math.min

const val PLANTNET_API_KEY = "2b101jvIJGNlYa6n1Vp1TmrV5u"

data class PlantInfo(
    val scientificName: String,
    val commonName: String,
    val arabicName: String,
    val family: String,
    val description: String,
    val medicinalUses: String,
    val ecologicalUses: String,
    val distribution: String,
    val symptoms: List<String>,
    val conservationStatus: String
)

data class GBIFResult(
    val count: Int,
    val regions: List<String>
)

fun getConservationInfo(status: String): Pair<Color, String> {
    return when (status.uppercase()) {
        "LC" -> Color(0xFF4CAF50) to "Préoccupation mineure"
        "NT" -> Color(0xFF8BC34A) to "Quasi menacé"
        "VU" -> Color(0xFFFFC107) to "Vulnérable"
        "EN" -> Color(0xFFFF9800) to "En danger"
        "CR" -> Color(0xFFF44336) to "En danger critique"
        "EX" -> Color(0xFF9E9E9E) to "Éteint"
        "DD" -> Color(0xFF9E9E9E) to "Données insuffisantes"
        else -> Color.Gray to "Non évalué"
    }
}

val algerianPlants = listOf(
    PlantInfo("Pistacia atlantica", "Pistachier de l'Atlas", "بطم أطلسي", "Anacardiaceae", "Arbre rustique pouvant atteindre 7m. Feuilles composées, coriaces.", "Résine contre les troubles digestifs. Feuilles en infusion respiratoire.", "Fixation des sols en zones arides.", "Atlas tellien, Hauts plateaux, Aures", listOf("Digestion", "Respiration", "Inflammation"), "LC"),
    PlantInfo("Pistacia terebinthus", "Pistachier térébinthe", "بطم terpentين", "Anacardiaceae", "Arbuste de 3-5m. Odeur résineuse caractéristique.", "Térébenthine en aromathérapie. Écorce astringente.", "Maquis méditerranéen.", "Régions côtières, Tell algérien", listOf("Peau", "Cicatrisation", "Douleur"), "LC"),
    PlantInfo("Ziziphus lotus", "Jujubier", "سدر", "Rhamnaceae", "Arbuste épineux de 2-4m. Fruits charnus comestibles.", "Fruits calmants. Feuilles contre l'insomnie.", "Brise-vent naturel.", "Sahara, Hauts plateaux", listOf("Insomnie", "Stress", "Digestion"), "LC"),
    PlantInfo("Olea europaea", "Olivier", "زيتون", "Oleaceae", "Arbre emblématique méditerranéen.", "Huile pour la peau. Feuilles hypotensives.", "Culture millénaire.", "Kabylie, Tell, régions côtières", listOf("Tension", "Peau", "Cœur"), "LC"),
    PlantInfo("Stipa tenacissima", "Alfa", "حلفاء", "Poaceae", "Graminée vivace formant des touffes denses.", "Fibres pour l'artisanat.", "Fixation des sols steppiques.", "Steppes, Hauts plateaux", listOf("Artisanat"), "LC"),
    PlantInfo("Phoenix dactylifera", "Palmier dattier", "نخيل", "Arecaceae", "Palmier de 15-25m. Fruits (dattes) très nutritifs.", "Dattes énergétiques. Noyaux en infusion digestive.", "Écosystème oasien complet.", "Sahara, Biskra, El Oued, Adrar", listOf("Énergie", "Digestion", "Anémie"), "LC"),
    PlantInfo("Ceratonia siliqua", "Caroubier", "خروب", "Fabaceae", "Arbre de 10-15m. Gousses brunes comestibles.", "Pulpe contre la diarrhée.", "Ombrage en zones arides.", "Tell, Kabylie", listOf("Diarrhée", "Digestion", "Enfants"), "LC"),
    PlantInfo("Quercus ilex", "Chêne vert", "بلوط أخضر", "Fagaceae", "Arbre de 10-20m. Feuilles persistantes coriaces.", "Écorce tannique. Glands nutritifs.", "Forêts méditerranéennes.", "Kabylie, Tell, Atlas blidéen", listOf("Diarrhée", "Peau", "Gorge"), "LC"),
    PlantInfo("Cupressus sempervirens", "Cyprès", "سرو", "Cupressaceae", "Arbre de 15-25m. Port colonnaire caractéristique.", "Huile essentielle anticatarrhale.", "Brise-vent.", "Régions côtières, Tell", listOf("Toux", "Respiration", "Circulation"), "LC"),
    PlantInfo("Pinus halepensis", "Pin d'Alep", "صنوبر حلبي", "Pinaceae", "Conifère de 15-20m. Écorce rougeâtre.", "Résine antiseptique. Aiguilles en infusion vitaminées.", "Reboisement.", "Tell, Atlas, régions montagneuses", listOf("Respiration", "Vitamines", "Peau"), "LC"),
    PlantInfo("Rosmarinus officinalis", "Romarin", "إكليل الجبل", "Lamiaceae", "Arbuste aromatique de 1-2m. Feuilles en aiguilles.", "Stimulant digestif, tonique général, circulation.", "Plante mellifère.", "Tell, Atlas, zones montagneuses", listOf("Digestion", "Mémoire", "Circulation", "Fatigue"), "LC"),
    PlantInfo("Lavandula stoechas", "Lavande stéchade", "خزامى", "Lamiaceae", "Arbuste de 30-60cm. Fleurs violettes en épi.", "Antiseptique, cicatrisant, calmant.", "Protection des sols.", "Régions méditerranéennes, Kabylie", listOf("Brûlure", "Stress", "Sommeil", "Peau"), "LC"),
    PlantInfo("Tetraclinis articulata", "Thuya de Barbarie", "العارعر", "Cupressaceae", "Conifère endémique de 5-15m. Feuilles en écailles.", "Huile essentielle puissante (fongicide).", "Espèce endémique protégée.", "Atlas tellien", listOf("Champignons", "Peau", "Rhumatisme"), "VU"),
    PlantInfo("Juniperus phoenicea", "Genévrier de Phénicie", "العرعار", "Cupressaceae", "Arbuste ou arbre de 2-15m. Fruits (galbules) noirs.", "Baies diurétiques et antiseptiques urinaires.", "Fixation des dunes côtières.", "Littoral méditerranéen, steppes", listOf("Urinaire", "Digestion", "Rhumatisme"), "LC"),
    PlantInfo("Artemisia herba-alba", "Armoise blanche", "شيح", "Asteraceae", "Sous-arbuste grisâtre de 20-40cm. Odeur forte.", "Vermifuge, antidiabétique traditionnel, digestif.", "Plante pastorale importante.", "Hauts plateaux, steppes, Sud", listOf("Diabète", "Vers", "Digestion", "Foie"), "LC")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) {
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

@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    if (!isGPSEnabled && !isNetworkEnabled) return null
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
            if (isGPSEnabled) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener!!)
            else if (isNetworkEnabled) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, locationListener!!)
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
    val resultList = mutableListOf<JSONObject>()
    for (i in 0 until jsonArray.length()) resultList.add(jsonArray.getJSONObject(i))
    return resultList.reversed()
}

fun getHistoryAsCSV(context: Context): String {
    val history = getHistory(context)
    if (history.isEmpty()) return ""
    val csvContent = StringBuilder()
    csvContent.appendLine("Nom,Nom scientifique,Date,Confiance (%),Latitude,Longitude")
    history.forEach { item ->
        val name = item.getString("name").replace(",", ";")
        val scientific = item.getString("scientific").replace(",", ";")
        val date = item.getString("date")
        val confidence = item.getInt("confidence")
        val lat = item.optDouble("latitude", 0.0)
        val lng = item.optDouble("longitude", 0.0)
        csvContent.appendLine("$name,$scientific,$date,$confidence,$lat,$lng")
    }
    return csvContent.toString()
}

@Composable
fun FloraXDZApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(4000)
        showSplash = false
    }
    if (showSplash) AnimatedSplashScreen() else MainScreen()
}

// 🆕 SPLASH SCREEN ANIMÉ ET ÉLÉGANT
@Composable
fun AnimatedSplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    
    // Animations
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NaamaGreen,
                        NaamaGreenLight,
                        Color(0xFF81C784)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Cercle décoratif animé en arrière-plan
        Box(
            modifier = Modifier
                .size(300.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .scale(scale)
                .alpha(alpha)
        )
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo animé
            Text(
                "🌿",
                fontSize = 120.sp,
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Titre principal
            Text(
                "FloraXDZ",
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sous-titre
            Text(
                "Application d'identification",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Center
            )
            Text(
                "des plantes d'Algérie",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Ligne décorative
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                NaamaRed,
                                Color.Transparent
                            )
                        )
                    )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Université
            Text(
                "Université de Naâma",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Concepteur
            Text(
                "Conçu par",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Text(
                "Pr. GUERINE Lakhdar",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFEB3B),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Année et drapeau
            Text(
                "Algérie 🇩🇿 • 2026",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasLocationPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? -> if (bitmap != null) { selectedBitmap = bitmap; selectedImageUri = null } }
    val galleryLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? -> if (uri != null) { selectedImageUri = uri; selectedBitmap = null } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (hasCameraPermission) cameraLauncher.launch(null)
    }
    val organs = listOf("auto" to " Auto", "leaf" to "🍃 Feuille", "flower" to "🌸 Fleur", "fruit" to "🍎 Fruit", "bark" to "🌳 Écorce", "stem" to "🌱 Tige")
    
    when (currentScreen) {
        "main" -> MainContent(context, scope, hasCameraPermission, hasLocationPermission, cameraLauncher, galleryLauncher, permissionLauncher, organs)
        "history" -> HistoryScreen(context)
        "pharmacopoeia" -> PharmacopoeiaScreen()
        "plantDetail" -> PlantDetailScreen()
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
            Text("🤖 IA : PlantNet", fontSize = 12.sp, color = NaamaGreen, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { currentScreen = "pharmacopoeia" }) { Text("💊 Remèdes") }
                TextButton(onClick = { currentScreen = "history" }) { Text("📜 Historique") }
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
                                .background(if (isSelected) NaamaGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .border(width = 1.dp, color = if (isSelected) NaamaGreen else MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(8.dp))
                                .clickable { selectedOrgan = value }.padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = label, fontSize = 12.sp, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
                    scope.launch { identifyPlant(context, bitmap, selectedOrgan) }
                }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing) {
                    if (isProcessing) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White); Spacer(modifier = Modifier.width(8.dp)) }
                    Text(if (isProcessing) "Identification..." else "Identifier la plante")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            identificationResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Résultat de l'identification :", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(result, fontSize = 16.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val shareText = buildString {
                                    append(result)
                                    append("\n\n-- FloraXDZ (Pr. GUERINE Lakhdar - Université de Naâma)")
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Partager via")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NaamaGreen)
                        ) {
                            Text(" Partager le résultat")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Pr. GUERINE Lakhdar • 2026", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PharmacopoeiaScreen() {
    val allSymptoms = algerianPlants.flatMap { it.symptoms }.distinct().sorted()
    var selectedSymptom by remember { mutableStateOf<String?>(null) }
    val filteredPlants = if (selectedSymptom == null) emptyList() else algerianPlants.filter { selectedSymptom in it.symptoms }
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("💊 Pharmacopée", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            TextButton(onClick = { currentScreen = "main" }) { Text("← Retour") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sélectionnez un symptôme :", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        val symptomRows = allSymptoms.chunked(3)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            symptomRows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { symptom ->
                        val isSelected = selectedSymptom == symptom
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedSymptom = if (isSelected) null else symptom }
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) NaamaGreen else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = symptom,
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (selectedSymptom != null) {
                Text("Plantes pour : $selectedSymptom", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (filteredPlants.isEmpty()) {
                    Text("Aucune plante trouvée.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filteredPlants.forEach { plant ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            selectedPlant = plant
                            currentScreen = "plantDetail"
                        }) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(plant.commonName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(plant.scientificName, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(plant.arabicName, fontSize = 14.sp, color = NaamaGreen)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Usage: ${plant.medicinalUses}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                Text("Sélectionnez un symptôme ci-dessus.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        }
    }
}

@Composable
fun HistoryScreen(context: Context) {
    val historyList = getHistory(context)
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Historique", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Row {
                if (historyList.isNotEmpty()) {
                    TextButton(onClick = {
                        val csvText = getHistoryAsCSV(context)
                        if (csvText.isNotEmpty()) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csvText)
                                type = "text/csv"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Exporter l'historique via"))
                        }
                    }) {
                        Text(" Export CSV")
                    }
                }
                TextButton(onClick = { currentScreen = "main" }) { Text("← Retour") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (historyList.isEmpty()) {
            Text("Aucune plante identifiée.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                historyList.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.getString("name"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(item.getString("scientific"), fontStyle = FontStyle.Italic, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Confiance: ${item.getInt("confidence")}%", fontSize = 12.sp)
                            Text(item.getString("date"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlantDetailScreen() {
    val plant = selectedPlant ?: return
    val (statusColor, statusLabel) = getConservationInfo(plant.conservationStatus)
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fiche détaillée", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            TextButton(onClick = { currentScreen = "pharmacopoeia" }) { Text("← Retour") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(plant.commonName, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(plant.scientificName, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(plant.arabicName, fontSize = 18.sp, color = NaamaGreen)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Famille: ${plant.family}", fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡️ Statut UICN: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Box(
                        modifier = Modifier
                            .background(statusColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${plant.conservationStatus} - $statusLabel",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
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
                Text(" Usages écologiques", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(plant.ecologicalUses, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(" Répartition en Algérie", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(plant.distribution, fontSize = 14.sp)
            }
        }
    }
}

suspend fun identifyPlant(context: Context, bitmap: Bitmap, organ: String) {
    try {
        withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val byteArray = outputStream.toByteArray()
            val requestBody = byteArray.toRequestBody("image/jpeg".toMediaType())
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("images", "plant.jpg", requestBody)
                .build()
            var requestUrl = "https://my-api.plantnet.org/v2/identify/all?api-key=$PLANTNET_API_KEY&include-related-images=false&no-reject=false&nb-results=5&lang=fr"
            if (organ != "auto") {
                requestUrl += "&organs=$organ"
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(requestUrl).post(multipartBody).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val resultsArray = json.getJSONArray("results")
                var resultText = ""
                var bestScientific = ""
                var bestConfidence = 0
                val limit = min(resultsArray.length(), 3)
                for (i in 0 until limit) {
                    val resultObj = resultsArray.getJSONObject(i)
                    val species = resultObj.getJSONObject("species")
                    val scientificName = species.getString("scientificNameWithoutAuthor")
                    val commonNames = species.optJSONArray("commonNames")
                    val commonName = if (commonNames != null && commonNames.length() > 0) commonNames.getString(0) else "Nom commun non disponible"
                    val score = resultObj.getDouble("score")
                    val confidence = (score * 100).toInt()
                    if (i == 0) {
                        bestScientific = scientificName
                        bestConfidence = confidence
                    }
                    resultText += "🌟 ${i + 1}. $commonName\n"
                    resultText += "    Nom scientifique : $scientificName\n"
                    resultText += "   📊 Confiance : $confidence%\n\n"
                }
                if (bestScientific.isNotEmpty()) {
                    val location = getCurrentLocation(context)
                    val bestName = algerianPlants.find { it.scientificName == bestScientific }?.commonName ?: "Plante identifiée"
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
