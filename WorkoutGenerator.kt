package com.dietbyai.dietapp

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import org.json.JSONObject
import io.ktor.client.request.setBody
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.google.gson.Gson
import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONException


@Composable
fun DropdownSelector(
    options: List<String>,
    selected: String,
    preferenceKey: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val preferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
    ) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Text(selected.ifEmpty { "Wybierz opcję" }, color = Color.Black)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.Black) },
                    onClick = {
                        preferences.edit().putString(preferenceKey, option).apply()
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DropdownMultiSelect(
    options: List<String>,
    selectedOptions: List<String>,
    preferenceKey: String, // 🆕 Dodajemy klucz dla zapisania ustawień
    onSelectionChange: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    var expanded by remember { mutableStateOf(false) }
    val selectedText = if (selectedOptions.isEmpty()) "Wybierz opcje" else selectedOptions.joinToString(", ")

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
        ) {
            Button(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(selectedText, color = Color.Black)
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    val isSelected = selectedOptions.contains(option)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(option)
                            }
                        },
                        onClick = {
                            val newSelection = if (isSelected) {
                                selectedOptions - option
                            } else {
                                selectedOptions + option
                            }
                            preferences.edit().putStringSet(preferenceKey, newSelection.toSet()).apply() // 🆕 Zapis listy
                            onSelectionChange(newSelection)
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun WorkoutScreen(navController: NavController, subscriptionStatus: MutableState<String>) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val preferences = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
    val storedJson = preferences.getString("saved_workout", null)
    Log.d("WorkoutPreferences", "Zapisany trening w SharedPreferences: $storedJson")

    var remainingTime by remember { mutableStateOf(getRemainingWorkoutCooldownTime(context)) }
    val canGenerateWorkout = remainingTime == 0L && subscriptionStatus.value == "Subskrypcja aktywna"

    /*
// 🔄 Uruchamiamy odliczanie co sekundę
    LaunchedEffect(remainingTime) {
        while (remainingTime > 0) {
            delay(1000)
            remainingTime = getRemainingWorkoutCooldownTime(context)
        }
    }
     */

    // ✅ Resetowanie stanu po opuszczeniu ekranu
    DisposableEffect(Unit) {
        onDispose {
            isLoading = false
            progress = 0f
        }
    }


// 🎯 Wczytywanie zapisanych wartości z SharedPreferences
    var goal by remember {
        mutableStateOf(preferences.getString("goal", null) ?: "")
    }
    var level by remember {
        mutableStateOf(preferences.getString("level", null) ?: "")
    }
    var location by remember {
        mutableStateOf(preferences.getString("location", null) ?: "")
    }
    var duration by remember {
        mutableStateOf(preferences.getString("duration", null) ?: "")
    }
    var trainingType by remember {
        mutableStateOf(preferences.getString("trainingType", null) ?: "")
    }

// 🎯 Obsługa list (partie mięśniowe, sprzęt) - zapisane jako Set<String>
    var muscleGroups by remember {
        mutableStateOf(preferences.getStringSet("muscleGroups", emptySet())?.toList() ?: listOf())
    }
    var equipment by remember {
        mutableStateOf(preferences.getStringSet("equipment", emptySet())?.toList() ?: listOf())
    }

    var intensity by remember {
        mutableStateOf(preferences.getString("intensity", null) ?: "")
    }
    var daysPerWeek by remember {
        mutableStateOf(preferences.getString("daysPerWeek", null) ?: "")
    }
    var trainingStyle by remember {
        mutableStateOf(preferences.getString("trainingStyle", null) ?: "")
    }
    var restTime by remember {
        mutableStateOf(preferences.getString("restTime", null) ?: "")
    }
    var recoveryMode by remember {
        mutableStateOf(preferences.getString("recoveryMode", null) ?: "")
    }
    var preferencesList by remember { mutableStateOf(emptyList<String>()) }

    val savedWorkoutPlan = preferences.getString("saved_workout", null)

    // ✅ Dostosowanie sprzętu do miejsca treningu
    val availableEquipment = if (location == "Dom") {
        listOf(
            "Brak sprzętu",
            "Hantle",
            "Taśmy oporowe",
            "Kettlebell",
            "Drążek",
            "Skakanka",
            "Rowerek stacjonarny",
            "Orbitrek",
            "Bieżnia"
        )
    } else {
        listOf(
            "Hantle",
            "Sztanga",
            "Maszyny",
            "Kettlebell",
            "Drążek",
            "Piłka fitness",
            "Skakanka",
            "Rowerek stacjonarny",
            "Orbitrek",
            "Bieżnia"
        )
    }



    if (isLoading) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Generowanie treningu...",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "To może potrwać kilka minut.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Prosimy o cierpliwość ❤",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(50.dp),
                strokeWidth = 6.dp
            )
            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },  // 🆕 Dynamiczna aktualizacja progresu
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${(progress * 100).toInt()}% ukończono",
                style = MaterialTheme.typography.bodyLarge
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                Icon(
                    painter = painterResource(id = R.drawable.logo_512x512_dietbyai), // Wstaw odpowiedni zasób loga
                    contentDescription = "Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tekst
                Text(
                    text = "Wyłączenie aplikacji lub naciśnięcie przycisku 'Panel główny' przerwie generowanie treningu.",
                    color = Color.Black, // Czarna czcionka
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { navController.navigate(Screen.Welcome.route) }) {
                Text("Panel główny")
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .background(Color.White) // Białe tło
                .fillMaxSize()
                .padding(16.dp),
        ) {
            item {
                Text(
                    "\uD83D\uDCAA Plan treningu",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center, // Wyśrodkowanie tekstu
                    modifier = Modifier.fillMaxWidth() // Rozciągnięcie na całą szerokość
                )
                Spacer(modifier = Modifier.height(8.dp))
            }


// 🎯 Wybór celu treningowego
            item {
                Text(
                    "✔ Wybierz swój cel treningowy:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }
            item {
                DropdownSelector(
                    listOf(
                        "Budowa masy mięśniowej",
                        "Redukcja tkanki tłuszczowej",
                        "Poprawa kondycji"
                    ),
                    goal,
                    "goal"
                ) {
                    goal = it
                    preferences.edit().putString("goal", it).apply()
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Poziom zaawansowania
            item {
                Text(
                    "✔ Poziom zaawansowania:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }

            item {
                DropdownSelector(
                    listOf("Początkujący", "Średniozaawansowany", "Zaawansowany"),
                    level,
                    "level"
                ) {
                    level = it
                    preferences.edit().putString("level", it).apply()
                }
            }

// 🔹 Opis poziomu zaawansowania
            val levelDescription = when (level) {
                "Początkujący" -> "Dla osób, które dopiero zaczynają swoją przygodę z treningiem. Skupia się na nauce techniki i budowie podstawowej siły."
                "Średniozaawansowany" -> "Dla osób z doświadczeniem 6-12 miesięcy, które chcą zwiększyć obciążenia i progresować w treningu."
                "Zaawansowany" -> "Dla osób trenujących regularnie od dłuższego czasu. Wymaga dużej objętości treningowej i stosowania zaawansowanych technik."
                else -> "Wybierz poziom zaawansowania, aby zobaczyć więcej informacji."
            }

            item {
                Text(
                    text = levelDescription,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }


            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Miejsce treningu
            item {
                Text(
                    "✔ Miejsce treningu:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }
            item {
                DropdownSelector(
                    listOf("Dom", "Siłownia"),
                    location,
                    "location"
                ) {
                    location = it
                    preferences.edit().putString("location", it).apply()
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Długość treningu
            item {
                Text(
                    "✔ Długość treningu:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }
            item {
                DropdownSelector(
                    listOf("20-30 minut", "30-45 minut", "45-60 minut", "60+ minut"),
                    duration,
                    "duration"
                ) {
                    duration = it
                    preferences.edit().putString("duration", it).apply()
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Typ treningu
            item {
                Text(
                    "✔ Typ treningu:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }

            item {
                DropdownSelector(
                    listOf("Siłowy", "Cardio", "HIIT", "Crossfit"),
                    trainingType,
                    "trainingType"
                ) {
                    trainingType = it
                    preferences.edit().putString("trainingType", it).apply()
                }
            }

// 🔹 Opis typu treningu
            val trainingTypeDescription = when (trainingType) {
                "Siłowy" -> "Trening skupiony na budowie siły i masy mięśniowej, często z użyciem ciężarów."
                "Cardio" -> "Ćwiczenia zwiększające wytrzymałość sercowo-naczyniową, np. bieganie, rower, skakanka."
                "HIIT" -> "Wysoko intensywny trening interwałowy, łączący krótkie okresy wysiłku i odpoczynku."
                "Crossfit" -> "Funkcjonalny trening o dużej intensywności, łączący elementy siłowe i kondycyjne."
                else -> "Wybierz typ treningu, aby zobaczyć więcej informacji."
            }

            item {
                Text(
                    text = trainingTypeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }


            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Partie mięśniowe
            item {
                Text(
                    "✔ Partie mięśniowe do treningu:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }
            item {
                DropdownMultiSelect(
                    listOf(
                        "Całe ciało",
                        "Klatka piersiowa",
                        "Plecy",
                        "Nogi",
                        "Ramiona",
                        "Barki",
                        "Brzuch"
                    ),
                    muscleGroups,
                    "muscleGroups"
                ) {
                    muscleGroups = it
                    preferences.edit().putStringSet("muscleGroups", it.toSet()).apply()
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Dostępny sprzęt
            item {
                Text(
                    "✔ Dostępny sprzęt:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }
            item {
                DropdownMultiSelect(
                    listOf(
                        "Sztanga",
                        "Hantle",
                        "Kettlebell",
                        "Maszyny",
                        "Gumy oporowe",
                        "Drążek",
                        "Orbitrek",
                        "Rower stacjonarny",
                        "Bieżnia"
                    ),
                    equipment,
                    "equipment"
                ) {
                    equipment = it
                    preferences.edit().putStringSet("equipment", it.toSet()).apply()
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Intensywność treningu
            item {
                Text(
                    "✔ Intensywność treningu:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }

            item {
                DropdownSelector(
                    listOf("Niska", "Średnia", "Wysoka"),
                    intensity,
                    "intensity"
                ) {
                    intensity = it
                    preferences.edit().putString("intensity", it).apply()
                }
            }

// 🔹 Opis intensywności treningu
            val intensityDescription = when (intensity) {
                "Niska" -> "Lekki trening o niskim tempie, idealny dla początkujących i osób w fazie regeneracji."
                "Średnia" -> "Zrównoważony wysiłek, odpowiedni dla większości osób, łączący pracę nad siłą i kondycją."
                "Wysoka" -> "Intensywny trening, często zawierający interwały i ćwiczenia o wysokiej dynamice."
                else -> "Wybierz intensywność treningu, aby zobaczyć więcej informacji."
            }

            item {
                Text(
                    text = intensityDescription,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }


            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Liczba dni treningowych w tygodniu
            item {
                Text(
                    "✔ Liczba dni treningowych w tygodniu:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }
            item {
                DropdownSelector(
                    listOf("2", "3", "4", "5", "6"),
                    daysPerWeek,
                    "daysPerWeek"
                ) {
                    daysPerWeek = it
                    preferences.edit().putString("daysPerWeek", it).apply()
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Styl treningowy
            item {
                Text(
                    "✔ Styl treningowy:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }

            item {
                DropdownSelector(
                    listOf("Trening FBW", "Split", "Push-Pull-Legs", "Bodyweight"),
                    trainingStyle,
                    "trainingStyle"
                ) {
                    trainingStyle = it
                    preferences.edit().putString("trainingStyle", it).apply()
                }
            }

// 🔹 Krótki opis stylu treningowego
            val trainingStyleDescription = when (trainingStyle) {
                "Trening FBW" -> "Trening całego ciała w każdej sesji. Idealny dla początkujących i tych, którzy chcą efektywnego treningu przy mniejszej liczbie dni."
                "Split" -> "Podział treningu na partie mięśniowe (np. klatka + triceps, plecy + biceps). Świetny dla średniozaawansowanych i zaawansowanych."
                "Push-Pull-Legs" -> "Trening podzielony na ruchy pchające, przyciągające i nogi. Dobry dla osób chcących optymalizować regenerację."
                "Bodyweight" -> "Trening z masą własnego ciała. Nie wymaga sprzętu, dobry dla osób ćwiczących w domu."
                else -> "Wybierz styl treningowy, aby zobaczyć więcej informacji."
            }

            item {
                Text(
                    text = trainingStyleDescription,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Czas odpoczynku między seriami
            item {
                Text(
                    "✔ Czas odpoczynku między seriami:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                DropdownSelector(
                    listOf("30 sekund", "60 sekund", "90 sekund", "120 sekund"),
                    restTime,
                    "restTime"
                ) {
                    restTime = it
                    preferences.edit().putString("restTime", it).apply()
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

// 🎯 Tryb regeneracji
            item {
                Text(
                    "✔ Tryb regeneracji:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp) // Dodaje dolny margines 8dp
                )
            }

            item {
                DropdownSelector(
                    listOf(
                        "Aktywna regeneracja",
                        "Pasywna regeneracja",
                        "Stretching",
                        "Mobilizacja"
                    ),
                    recoveryMode,
                    "recoveryMode"
                ) {
                    recoveryMode = it
                    preferences.edit().putString("recoveryMode", it).apply()
                }
            }

// 🔹 Opis trybu regeneracji
            val recoveryDescription = when (recoveryMode) {
                "Aktywna regeneracja" -> "Lekkie ćwiczenia (np. spacer, pływanie) pomagające w szybszej regeneracji mięśni."
                "Pasywna regeneracja" -> "Pełny odpoczynek bez aktywności fizycznej, pozwalający na regenerację organizmu."
                "Stretching" -> "Rozciąganie poprawiające elastyczność mięśni i redukujące napięcie po treningu."
                "Mobilizacja" -> "Ćwiczenia poprawiające zakres ruchu i elastyczność stawów, zapobiegające kontuzjom."
                else -> "Wybierz tryb regeneracji, aby zobaczyć więcej informacji."
            }

            item {
                Text(
                    text = recoveryDescription,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }



            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ✅ Funkcja sprawdzająca, czy formularz jest poprawnie wypełniony
                    val isFormValid = remember {
                        derivedStateOf {
                            goal.isNotEmpty() &&
                                    level.isNotEmpty() &&
                                    location.isNotEmpty() &&
                                    duration.isNotEmpty() &&
                                    trainingType.isNotEmpty() &&
                                    muscleGroups.isNotEmpty() &&
                                    equipment.isNotEmpty() &&
                                    intensity.isNotEmpty() &&
                                    daysPerWeek.isNotEmpty() &&
                                    trainingStyle.isNotEmpty() &&
                                    restTime.isNotEmpty() &&
                                    recoveryMode.isNotEmpty()
                        }
                    }

                    // ✅ Komunikaty o brakujących polach
                    var showErrors by remember { mutableStateOf(false) }

                    // 📝 Tekst informacyjny nad przyciskiem
                    if (remainingTime > 0) {
                        val minutes = (remainingTime / 1000) / 60
                        val seconds = (remainingTime / 1000) % 60

                        Text(
                            text = "⏳ Nowy plan treningowy możesz\nwygenerować za: ${minutes} min.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }else{

                        Text(
                            text = "⏳ Plany treningowe możesz\ngenerować co 30 min.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )

                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                if (isFormValid.value) {
                                    if (remainingTime == 0L) {
                                        isLoading = true
                                        progress = 0f
                                        showErrors = false

                                        updateLastWorkoutGenerationTime(context) // 🕒 Zapisujemy czas generowania

                                        generateWorkout(
                                            goal, level, location, duration, trainingType,
                                            muscleGroups, equipment, intensity, daysPerWeek,
                                            trainingStyle, preferencesList, restTime, recoveryMode,
                                            context, updateProgress = { newProgress -> progress = newProgress }
                                        ) { response ->
                                            if (!response.isNullOrBlank()) {
                                                preferences.edit().putString("saved_workout", response).apply()
                                            }
                                            isLoading = false
                                            navController.navigate("workout_result")
                                        }
                                    }
                                } else {
                                    showErrors = true // ❌ Pokazujemy komunikat o błędach
                                }
                            },
                            enabled = canGenerateWorkout,
                            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canGenerateWorkout) Color(0xFF007BFF) else Color.Gray,
                                contentColor = Color.White
                            )
                        ) {

                            LaunchedEffect(remainingTime) {
                                while (remainingTime > 0) {
                                    delay(10000)
                                    remainingTime = getRemainingWorkoutCooldownTime(context)
                                }
                            }
                            Text(
                                when {
                                    subscriptionStatus.value != "Subskrypcja aktywna" -> "⚡ Generuj plan treningowy"
                                    remainingTime > 0 -> "⏳ Poczekaj..."
                                    else -> "⚡ Generuj plan treningowy"
                                }
                            )
                        }
                    }

                    // 🔴 Komunikat o brakujących polach, jeśli formularz jest niekompletny
                    if (showErrors) {
                        Text(
                            text = "❌ Proszę uzupełnić wszystkie pola!",
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Przycisk dla użytkowników bez subskrypcji
                    if (subscriptionStatus.value != "Subskrypcja aktywna") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp), // Dodajemy padding dla estetyki
                            horizontalAlignment = Alignment.CenterHorizontally // Wyśrodkowanie w pionie
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Informacja o subskrypcji
                            Text(
                                "\uD83D\uDEAB Generowanie treningu dostępne\nwyłącznie dla subskrybentów",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center, // Wyśrodkowanie tekstu
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Przycisk subskrypcji
                            Button(
                                onClick = { navController.navigate(Screen.Subscription.route) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFD700), // Złoty kolor tła przycisku
                                    contentColor = Color.Black         // Czarny kolor tekstu dla kontrastu
                                ),
                                modifier = Modifier
                                   // .fillMaxWidth(0.8f) // Przycisk zajmuje 80% szerokości
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "🛒 Kup subskrypcję",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                          //  Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "\uD83D\uDD25 Skorzystaj z promocji",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item{

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = "Wstecz",
                        modifier = Modifier.clickable { navController.popBackStack() },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
                    )
                    Text(
                        text = "Panel główny",
                        modifier = Modifier.clickable { navController.navigate(Screen.Welcome.route) },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
                    )
                }
            }

        }
    }
}

@Composable
fun WorkoutResultScreen(navController: NavController) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)

    // 🛑 Pobranie zapisanej diety i sprawdzenie, czy JSON jest poprawny
    val workoutPlanJson = preferences.getString("saved_workout", null)
    Log.d("WorkoutLoad", "Załadowano plan: $workoutPlanJson") // 🛑 Sprawdzenie, co faktycznie ładuje ekran
    val workoutPlan = if (!workoutPlanJson.isNullOrBlank()) {
        parseWorkoutResponse(workoutPlanJson)
    } else {
        emptyList()
    }

    Log.d("WorkoutLoad", "Plan po parsowaniu: ${workoutPlan.size} dni") // 🛑 Sprawdzenie, czy lista nie jest pusta

    val completionPercentage = remember { mutableStateOf(0f) }

    // 🟢 Obliczamy progres w tle
    LaunchedEffect(workoutPlan) {
        completionPercentage.value = getWorkoutCompletionPercentage(context, workoutPlan)
    }

    // 🏆 Jeśli użytkownik ukończył 100% planu → przejście na ekran gratulacji
    LaunchedEffect(completionPercentage.value) {
        if (completionPercentage.value >= 1.0f) {
            navController.navigate("congratulations_screen")
        }
    }

    Scaffold(
        modifier = Modifier
            .background(Color.White) // Białe tło
            .fillMaxSize(),

        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { navController.navigate(Screen.Welcome.route) },
                    modifier = Modifier.width(200.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF))
                ) {
                    Text("Panel główny")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .background(Color.White) // Białe tło
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (workoutPlan.isEmpty()) {
                // 🟢 Jeśli brak planu, pokaż komunikat i przycisk do wygenerowania nowego
                Text(
                    text = "Nie masz jeszcze żadnego planu treningu.\nJeśli wystąpił błąd lub przerwało połączenie\nspórbuj wygnerować nowy plan treningowy",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { navController.navigate("workout") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text("⚡ Generuj nowy plan")
                }
            } else {
                // 🟢 Pasek postępu treningu
                Text(
                    text = "Postęp treningu: ${(completionPercentage.value * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LinearProgressIndicator(
                    progress = completionPercentage.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = Color(0xFF1B5E20) // Ciemnozielony pasek postępu
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            "\uD83D\uDCAA Plan treningu",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    workoutPlan.forEach { day ->
                        item {
                            val completed = remember { mutableStateOf(isWorkoutCompleted(context, day.day)) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = day.day,
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (completed.value) {
                                    Text(
                                        text = "✅ Ukończony",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color(0xFF1B5E20),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            removeWorkoutProgress(context, day.day)
                                            completed.value = false
                                            completionPercentage.value = getWorkoutCompletionPercentage(context, workoutPlan)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) {
                                        Text("↩ Cofnij ukończenie")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            saveWorkoutProgress(context, day.day)
                                            completed.value = true
                                            completionPercentage.value = getWorkoutCompletionPercentage(context, workoutPlan)
                                        }
                                    ) {
                                        Text("✅ Oznacz jako ukończony")
                                    }
                                }

// 🟢 Wyświetlanie ćwiczeń
                                day.exercises.forEach { exercise ->
                                    Card(
                                        modifier = Modifier
                                            .background(Color.White) // Białe tło
                                            .fillMaxWidth()
                                            .padding(4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = CardDefaults.elevatedCardElevation(2.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .background(Color.White) // Białe tło
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = exercise.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Text(
                                                text = "Serie: ${exercise.sets}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Text(
                                                text = exercise.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // 🟢 Opis "Jak wykonywać"
                                            val detailedText = exercise.detailedDescription
                                                ?: "Brak szczegółowego opisu"

                                            Text(
                                                text = "Jak wykonywać?",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Text(
                                                text = detailedText,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // 🟢 Timer odpoczynku
                                            if (exercise.restTime > 0){
                                                Text(
                                                    text = "Skończyłeś serie?",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                RestTimer(restTime = exercise.restTime) {
                                                    // Możesz dodać tu powiadomienie lub dźwięk
                                                }
                                            }

                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ✅ Generowanie planu treningowego
fun generateWorkout(
    goal: String,
    level: String,
    location: String,
    duration: String,
    trainingType: String,
    muscleGroups: List<String>,
    equipment: List<String>,
    intensity: String,
    daysPerWeek: String,
    trainingStyle: String,
    preferences: List<String>,
    restTime: String,
    recoveryMode: String,
    context: Context,
    updateProgress: (Float) -> Unit,  // 🆕 Dodajemy callback do aktualizacji progresu
    callback: (String) -> Unit
) {
    val client = HttpClient(CIO) {
        engine {
            requestTimeout = 120_000
        }
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {

            val startTime = System.currentTimeMillis() // ⏳ Start licznika czasu
            val estimatedTime = 30_000L // ⏳ Zakładany czas generacji (30 sekund)

            // 🔄 Aktualizacja paska postępu co 500ms
            launch {
                while (true) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    val progress = (elapsedTime.toFloat() / estimatedTime).coerceIn(0f, 1f)
                    updateProgress(progress)
                    if (progress >= 1f) break
                    delay(500) // 🔄 Odświeżanie co 500ms
                }
            }

            val requestData = mapOf(
                "model" to "gpt-4-turbo",
                "messages" to listOf(
                    mapOf("role" to "system", "content" to "Jesteś trenerem personalnym. Generuj plany treningowe w formacie JSON, bez ```json na początku."),
                    mapOf("role" to "user", "content" to """
    Stwórz plan treningowy dla użytkownika:
    - Cel: $goal
    - Poziom zaawansowania: $level
    - Miejsce treningu: $location
    - Czas treningu: $duration
    - Rodzaj treningu: $trainingType
    - Partie mięśniowe: ${muscleGroups.joinToString(", ")}
    - Dostępny sprzęt: ${equipment.joinToString(", ")}
    - Intensywność: $intensity
    - Liczba dni w tygodniu: $daysPerWeek
    - Styl treningu: $trainingStyle
    - Czas odpoczynku między seriami: $restTime
    - Tryb regeneracji: $recoveryMode

    **Wynik zwróć jako JSON dokładnie w tym formacie:** 
    {
        "days": [
            {
                "day": "Dzień 1",
                "exercises": [
                    {
                        "name": "Przysiady",
                        "sets": "4x10",
                        "description": "Ćwiczenie angażujące nogi i pośladki.",
                        "detailedDescription": "Przysiady wykonuje się stojąc w lekkim rozkroku...",
                        "restTime": 45
                    }
                ]
            }
        ]
    }
    Pisz w języku użytkownika przed wszystkim nazwy ćwiczeń.
    Nie zwracaj samej tablicy, zawsze zwracaj pełny obiekt JSON z kluczem "days".
""".trimIndent())

                ),
                "temperature" to 0.7
            )

            val requestBody = Gson().toJson(requestData)
            Log.d("WorkoutAPI", "Wysyłany JSON: $requestBody")

            val response: HttpResponse = client.post("https://dietbyai.com/proxy.php") {
                headers {
                    append("X-API-KEY", "my-secret-key-82756287bdygfoetnl1000000")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(requestBody)
            }


            val responseBody = response.bodyAsText()
            Log.d("WorkoutAPI", "Odpowiedź API: $responseBody")

            if (!responseBody.startsWith("{") && !responseBody.startsWith("[")) {
                withContext(Dispatchers.Main) {
                    callback("❌ Błąd API: Niepoprawna odpowiedź.")
                    updateProgress(0f) // Reset progresu
                }
                return@launch
            }


            val jsonResponse = JSONObject(responseBody)

            if (!jsonResponse.has("choices")) {
                withContext(Dispatchers.Main) {
                    callback("❌ Błąd API: Brak danych 'choices'. Odpowiedź: $responseBody")
                }
                return@launch
            }

            val rawContent = jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            // ✅ Usuwamy ```json na początku i ``` na końcu, jeśli są
            val cleanedJson = rawContent.replace("```json", "").replace("```", "").trim()

            // ✅ Sprawdzamy, czy JSON zaczyna się od '[' (czy jest listą)
            val workoutDays = if (cleanedJson.startsWith("[")) {
                Gson().fromJson(cleanedJson, Array<WorkoutDay>::class.java).toList()
            } else {
                Gson().fromJson(cleanedJson, WorkoutResponse::class.java).days
            }

            val updatedJson = Gson().toJson(workoutDays)


            withContext(Dispatchers.Main) {
                callback(updatedJson)
                updateProgress(1f) // 🆕 Postęp: 100% - zakończono
            }
        } catch (e: Exception) {
            Log.e("WorkoutAPI", "Błąd: ${e.message}")
            withContext(Dispatchers.Main) {
                callback("❌ Błąd: ${e.message}")
                updateProgress(0f) // Reset progresu
            }
        }
    }
}

fun parseWorkoutResponse(json: String): List<WorkoutDay> {
    Log.d("WorkoutParser", "Parsujemy JSON: $json") // 🛑 Sprawdzamy, co jest w JSON

    val workoutDays = mutableListOf<WorkoutDay>()

    try {
        if (json.trim().startsWith("[")) {
            // 🟢 JSON jest tablicą, konwertujemy na JSONArray
            val daysArray = JSONArray(json)

            for (i in 0 until daysArray.length()) {
                val dayObject = daysArray.getJSONObject(i)
                val exercisesArray = dayObject.optJSONArray("exercises") ?: JSONArray()
                val exercises = mutableListOf<Exercise>()

                for (j in 0 until exercisesArray.length()) {
                    val exerciseObject = exercisesArray.getJSONObject(j)

                    val exercise = Exercise(
                        name = exerciseObject.optString("name", "Brak nazwy"),
                        sets = exerciseObject.optString("sets", "0x0"),
                        description = exerciseObject.optString("description", "Brak opisu"),
                        detailedDescription = exerciseObject.optString("detailedDescription", "Brak opisu"),
                        restTime = exerciseObject.optInt("restTime", 60), // 🟢 Jeśli brak, ustaw 60s
                        difficultyLevel = exerciseObject.optString("difficultyLevel", "Brak poziomu"),
                        image = exerciseObject.optString("image", "")
                    )
                    exercises.add(exercise)
                }

                val workoutDay = WorkoutDay(dayObject.optString("day", "Brak dnia"), exercises)
                workoutDays.add(workoutDay)
            }
        } else if (json.trim().startsWith("{")) {
            // 🟢 JSON jest obiektem, pobieramy tablicę `days`
            val jsonObject = JSONObject(json)
            val daysArray = jsonObject.optJSONArray("days") ?: JSONArray()

            for (i in 0 until daysArray.length()) {
                val dayObject = daysArray.getJSONObject(i)
                val exercisesArray = dayObject.optJSONArray("exercises") ?: JSONArray()
                val exercises = mutableListOf<Exercise>()

                for (j in 0 until exercisesArray.length()) {
                    val exerciseObject = exercisesArray.getJSONObject(j)

                    val exercise = Exercise(
                        name = exerciseObject.optString("name", "Brak nazwy"),
                        sets = exerciseObject.optString("sets", "0x0"),
                        description = exerciseObject.optString("description", "Brak opisu"),
                        detailedDescription = exerciseObject.optString("detailedDescription", "Brak opisu"),
                        restTime = exerciseObject.optInt("restTime", 60),
                        difficultyLevel = exerciseObject.optString("difficultyLevel", "Brak poziomu"),
                        image = exerciseObject.optString("image", "")
                    )
                    exercises.add(exercise)
                }

                val workoutDay = WorkoutDay(dayObject.optString("day", "Brak dnia"), exercises)
                workoutDays.add(workoutDay)
            }
        } else {
            Log.e("WorkoutParser", "Niepoprawny format JSON")
        }
    } catch (e: JSONException) {
        Log.e("WorkoutParser", "Błąd parsowania JSON: ${e.message}")
    }

    Log.d("WorkoutParser", "Zwrocono listę dni: ${workoutDays.size}") // 🛑 Sprawdzenie, czy zwraca dni
    return workoutDays
}


fun saveWorkoutProgress(context: Context, day: String) {
    val preferences = context.getSharedPreferences("workout_progress", Context.MODE_PRIVATE)
    val editor = preferences.edit()
    editor.putBoolean(day, true) // Zapisujemy, że dany dzień treningowy został ukończony
    editor.apply()
}

fun isWorkoutCompleted(context: Context, day: String): Boolean {
    val preferences = context.getSharedPreferences("workout_progress", Context.MODE_PRIVATE)
    return preferences.getBoolean(day, false) // Pobiera status ukończenia treningu
}

fun removeWorkoutProgress(context: Context, day: String) {
    val preferences = context.getSharedPreferences("workout_progress", Context.MODE_PRIVATE)
    val editor = preferences.edit()
    editor.remove(day) // Usuwamy informację o ukończonym treningu
    editor.apply()
}

fun clearWorkoutProgress(context: Context) {
    val preferences = context.getSharedPreferences("workout_progress", Context.MODE_PRIVATE)
    val editor = preferences.edit()
    editor.clear() // Usuwa wszystkie zapisane ukończone treningi
    editor.apply()
}

fun getWorkoutCompletionPercentage(context: Context, workoutPlan: List<WorkoutDay>): Float {
    val completedDays = workoutPlan.count { isWorkoutCompleted(context, it.day) }
    return if (workoutPlan.isNotEmpty()) {
        completedDays.toFloat() / workoutPlan.size.toFloat()
    } else {
        0f
    }
}

@Composable
fun RestTimer(restTime: Int, onFinish: () -> Unit) {
    var timeLeft by rememberSaveable { mutableStateOf(restTime) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(key1 = isRunning, key2 = timeLeft) {
        if (isRunning) {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            isRunning = false
            val mediaPlayer = MediaPlayer.create(context, R.raw.alarm)
            mediaPlayer.start()
            onFinish()
        }
    }


    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isRunning) {
            Text(
                text = "⏳ Odpoczynek: ${timeLeft}s",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Blue,
                textAlign = TextAlign.Center
            )
        } else {
            Button(
                onClick = {
                    isRunning = true
                    timeLeft = restTime // 🟢 Resetowanie czasu przed startem
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007BFF), // Piękny odcień niebieskiego
                    contentColor = Color.White // Biały tekst dla lepszej czytelności
                )
            ) {
                Text("\uD83D\uDD52 Rozpocznij odpoczynek (${restTime}s)")
            }

        }
    }
}

@Composable
fun CongratulationsScreen(navController: NavController) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 🏆 Nagłówek gratulacyjny
        Text(
            text = "🎉 Gratulacje! 🎉",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B5E20),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 📢 Informacja o zakończeniu planu
        Text(
            text = "Ukończyłeś cały plan treningowy!\n Jesteś niesamowity! 💪🔥",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 📌 Wskazówka dotycząca zmiany planu
        Text(
            text = "Aby uniknąć stagnacji, dobrze jest zmieniać trening co kilka tygodni. Możesz kontynuować ten plan lub wygenerować nowy! 🚀",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 🔄 Opcja restartu planu (zerowanie ukończonych dni)
        Button(
            onClick = {
                clearWorkoutProgress(context) // Zerowanie ukończonych dni
                navController.navigate("workout_result") // Powrót do ekranu treningu
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF))
        ) {
            Text("🔄 Rozpocznij ten sam\n plan od nowa", textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ⚡ Opcja wygenerowania nowego treningu
        Button(
            onClick = { navController.navigate("workout") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
        ) {
            Text("⚡ Generuj nowy plan\nna kolejny tydzień", textAlign = TextAlign.Center)
        }


        Spacer(modifier = Modifier.height(12.dp))

        // 🏠 Powrót do menu głównego
        Button(
            onClick = { navController.navigate(Screen.Welcome.route) },
        ) {
            Text("Panel główny")
        }
    }
}

private val workoutCooldownMillis = 30 * 60 * 1000 // 30 minut
private const val LAST_WORKOUT_GENERATION_KEY = "LastWorkoutGenerationTime"

fun getRemainingWorkoutCooldownTime(context: Context): Long {
    val sharedPreferences = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
    val lastWorkoutTime = sharedPreferences.getLong(LAST_WORKOUT_GENERATION_KEY, 0)
    val currentTime = System.currentTimeMillis()
    return (workoutCooldownMillis - (currentTime - lastWorkoutTime)).coerceAtLeast(0L)
}

fun updateLastWorkoutGenerationTime(context: Context) {
    val sharedPreferences = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
    sharedPreferences.edit().putLong(LAST_WORKOUT_GENERATION_KEY, System.currentTimeMillis()).apply()
}

data class WorkoutResponse(
    val days: List<WorkoutDay>
)

data class WorkoutDay(
    val day: String,
    val exercises: List<Exercise>,
    var completed: Boolean = false // 🆕 Dodajemy pole oznaczające ukończenie treningu
)

data class Exercise(
    val name: String,
    val sets: String,
    val description: String,
    val detailedDescription: String,
    val restTime: Int,
    val difficultyLevel: String,  // 🆕 Pole, które nie ma wartości w JSON
    val image: String             // 🆕 Pole, które nie ma wartości w JSON
)