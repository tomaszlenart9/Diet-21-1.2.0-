// Kompletny kod aplikacji z wdrożonymi wszystkimi sugerowanymi ekranami i funkcjonalnościami

package com.dietbyai.dietapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.BillingClientStateListener
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.facebook.appevents.AppEventsConstants
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Support
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NoFood
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState



// 1. ViewModel for dietary preferences
class DietaryPreferencesViewModel : ViewModel() {
    var selectedAllergens by mutableStateOf(setOf<String>())
    var dietType by mutableStateOf("Wybierz typ diety")
    var excludedIngredients by mutableStateOf("")
    var preferredIngredients by mutableStateOf("")
    var difficultyLevel by mutableStateOf("Łatwe")
    var mealCount by mutableStateOf("3") // Domyślna liczba posiłków
    var age by mutableStateOf("")
    var height by mutableStateOf("")
    var weight by mutableStateOf("")
    var gender by mutableStateOf("Wybierz")
    var activityLevel by mutableStateOf("Wybierz")
    var dietGoal by mutableStateOf("Wybierz cel")

    private val sharedPreferencesKey = "DietAppPreferences"
    private val dietPlanKey = "LastDietPlan"
    private val lastGenerationKey = "LastDietGenerationTime"
    private val subscriptionCooldownMillis = 1 * 60 * 60 * 1000 // 1 godziny w milisekundach subkrybenci
    private val generationCooldownMillis = 24 * 60 * 60 * 1000 // 24 godziny

    // private val subscriptionCooldownMillis = 1 * 60 * 1000 // 1 minuta
    // private val generationCooldownMillis = 2 * 60 * 1000 // 2 minuta
    //private val generationCooldownMillis = 5 * 60 * 1000 // 5 minut w milisekundach blokowanie generowania (właczona na 1 minut na czas testów)

    fun getRemainingCooldownTime(context: Context, hasSubscription: Boolean): Long {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        val lastGenerationTime = sharedPreferences.getLong(lastGenerationKey, 0)
        val cooldownMillis = if (hasSubscription) subscriptionCooldownMillis else generationCooldownMillis
        val currentTime = System.currentTimeMillis()
        return (cooldownMillis - (currentTime - lastGenerationTime)).coerceAtLeast(0L)
    }

    fun calculateTDEE(
        weight: Float, // Waga w kg
        height: Float, // Wzrost w cm
        age: Int, // Wiek w latach
        gender: String, // "Mężczyzna" lub "Kobieta"
        activityLevel: String // Poziom aktywności
    ): Float {
        // Oblicz BMR na podstawie płci
        val bmr = if (gender == "Mężczyzna") {
            10 * weight + 6.25 * height - 5 * age + 5
        } else {
            10 * weight + 6.25 * height - 5 * age - 161
        }

        // Uwzględnij poziom aktywności
        val activityMultiplier = when (activityLevel) {
            "Siedzący tryb życia" -> 1.2f
            "Lekka aktywność" -> 1.375f
            "Umiarkowana aktywność" -> 1.55f
            "Duża aktywność" -> 1.725f
            "Bardzo duża aktywność" -> 1.9f
            else -> 1.0f // Domyślny mnożnik dla braku aktywności
        }

        return (bmr * activityMultiplier.toDouble()).toFloat()
    }


    fun restorePreferences(context: Context) {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        selectedAllergens = sharedPreferences.getString("selectedAllergens", null)?.let {
            Json.decodeFromString<List<String>>(it).toSet() // Odczyt jako lista, konwersja na Set
        } ?: setOf()
        dietType = sharedPreferences.getString("dietType", "Wybierz typ diety") ?: "Wybierz typ diety"
        excludedIngredients = sharedPreferences.getString("excludedIngredients", "") ?: ""
        preferredIngredients = sharedPreferences.getString("preferredIngredients", "") ?: ""
        difficultyLevel = sharedPreferences.getString("difficultyLevel", "Łatwe") ?: "Łatwe"
        mealCount = sharedPreferences.getString("mealCount", "3") ?: "3"
        age = sharedPreferences.getString("age", "") ?: ""
        height = sharedPreferences.getString("height", "") ?: ""
        weight = sharedPreferences.getString("weight", "") ?: ""
        gender = sharedPreferences.getString("gender", "Wybierz") ?: "Wybierz"
        activityLevel = sharedPreferences.getString("activityLevel", "Wybierz") ?: "Wybierz"
        dietGoal = sharedPreferences.getString("dietGoal", "Wybierz cel") ?: "Wybierz cel"
    }


    // Zapisuje dietę do SharedPreferences
    fun saveDietPlan(context: Context, dietPlan: List<DietDay>) {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val dietJson = Json.encodeToString(dietPlan)
        editor.putString(dietPlanKey, dietJson)
        editor.apply()
    }

    fun savePreferences(context: Context) {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("selectedAllergens", Json.encodeToString(selectedAllergens.toList()))
            putString("dietType", dietType)
            putString("excludedIngredients", excludedIngredients)
            putString("preferredIngredients", preferredIngredients)
            putString("difficultyLevel", difficultyLevel)
            putString("mealCount", mealCount)
            putString("age", age)
            putString("height", height)
            putString("weight", weight)
            putString("gender", gender)
            putString("activityLevel", activityLevel)
            putString("dietGoal", dietGoal)
            apply()
        }
    }

    fun canGenerateNewDiet(context: Context, hasSubscription: Boolean): Boolean {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        val lastGenerationTime = sharedPreferences.getLong(lastGenerationKey, 0)
        val cooldownMillis = if (hasSubscription) subscriptionCooldownMillis else generationCooldownMillis
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastGenerationTime) >= cooldownMillis
    }

    fun updateLastGenerationTime(context: Context) {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putLong(lastGenerationKey, System.currentTimeMillis())
            apply()
        }
    }

    // Odczytuje dietę z SharedPreferences
    fun loadDietPlan(context: Context): List<DietDay>? {
        return try {
            val sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
            val dietJson = sharedPreferences.getString(dietPlanKey, null)
            dietJson?.let {
                Json.decodeFromString(it)
            }
        } catch (e: Exception) {
            null // Zwróć null w przypadku błędu
        }
    }
}

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object Preferences : Screen("dietaryPreferences")
    data object Input : Screen("input")
    data object Summary : Screen("summary")
    data object DietPlan : Screen("dietPlan")
    data object Legal : Screen("legal") // Nowy ekran
    data object Subscription : Screen("subscription")
    data object Terms : Screen("Terms") // Nowy ekran regulaminu
    data object SuccessSub : Screen("SuccessSub") // Nowy ekran regulaminu
    data object DietDesc : Screen("DietDesc") // Opis diet
}

class MainActivity : ComponentActivity(), PurchasesUpdatedListener {
    private lateinit var navController: NavHostController
    private lateinit var billingClient: BillingClient
    private val subscriptionStatus = mutableStateOf("Sprawdzanie subskrypcji...")
    private val availableSubscriptions = mutableStateListOf<ProductDetails>()

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d("Billing", "onPurchasesUpdated: ${billingResult.responseCode}, ${billingResult.debugMessage}")
        purchases?.forEach { purchase ->
            Log.d("Billing", "Zakupiono: ${purchase.products.joinToString(", ")}")
        }
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.d("Billing", "Sukces zakupu 4")

            // Logowanie zakupionych produktów
            Log.d("Billing", "Kupione produkty: ${purchases.joinToString { it.products.joinToString() }}")

            /*
            // Logowanie zdarzenia do Facebook SDK
            val logger = AppEventsLogger.newLogger(this)
            val params = Bundle()
            params.putString("subscription_plan", "monthly_plan") // Zamień na rzeczywisty plan subskrypcji
            params.putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, 20.00) // Cena subskrypcji
            logger.logEvent("complete_subscription", params)

            navController.navigate(Screen.SuccessSub.route)
             */

            Log.d("Billing", "Sukces zakupu")
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("Billing", "Zakup anulowany przez użytkownika.")
        } else {
            Log.e("Billing", "Błąd podczas zakupu: ${billingResult.debugMessage}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ręczna inicjalizacja SDK Facebooka
        FacebookSdk.setApplicationId(getString(R.string.facebook_app_id))
        FacebookSdk.setClientToken(getString(R.string.facebook_client_token))
        FacebookSdk.sdkInitialize(applicationContext)

        // Aktywacja AppEventsLogger SDK
        AppEventsLogger.activateApp(application)

        // Logowanie zdarzenia aktywacji aplikacji SDK
        val logger = AppEventsLogger.newLogger(this)
        logger.logEvent(AppEventsConstants.EVENT_NAME_ACTIVATED_APP)


        // Zakup subskrypcji
        billingClient = BillingClient.newBuilder(this)
            .setListener { billingResult, purchases ->
                Log.d("Billing", "Wynik zakupu: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    Log.d("Billing", "Sukces zakupu 2")
                    val hasSubscription = purchases.any { it.products.contains("monthly_plan") }
                    subscriptionStatus.value = if (hasSubscription) "Subskrypcja aktywna" else "Subskrypcja nieaktywna"

                    // Logowanie zdarzenia do Facebook SDK
                    val logger = AppEventsLogger.newLogger(this)
                    val params = Bundle()
                    // Dynamiczne dane subskrypcji
                    val subscriptionPlan = "monthly_plan" // Przykładowo
                    val subscriptionPrice = 20.00 // Dynamiczna cena
                    // Dodanie parametrów
                    params.putString("fb_subscription_plan", subscriptionPlan)
                    params.putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, subscriptionPrice)

                    // Logowanie standardowego zdarzenia "Subscribe"
                    logger.logEvent(AppEventsConstants.EVENT_NAME_SUBSCRIBE, params)

                    // Ekran sukcesu
                    //  navController.navigate(Screen.SuccessSub.route)

                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    subscriptionStatus.value = "Zakup anulowany przez użytkownika."
                } else {
                    subscriptionStatus.value = "Błąd zakupu: ${billingResult.debugMessage}"
                }
            }
            .enablePendingPurchases()
            .build()

        startBillingConnection()
        fetchAvailableSubscriptions { products ->
            availableSubscriptions.clear()
            availableSubscriptions.addAll(products)
        } //Pobieranie dostępnych subskrypcji

        // Funkcja `setContent` obsługuje wywołania `@Composable`
        setContent {
            navController = rememberNavController() // Zainicjalizuj navController
            val context = LocalContext.current
            val dietaryPreferencesViewModel = viewModel<DietaryPreferencesViewModel>()

            // Wywołanie funkcji do sprawdzania subskrypcji
            LaunchedEffect(Unit) {
                checkSubscriptionStatus(subscriptionStatus)
                dietaryPreferencesViewModel.restorePreferences(context)
                fetchAvailableSubscriptions { products ->
                    availableSubscriptions.clear()
                    availableSubscriptions.addAll(products)
                }
            }

            // Ustawienie aplikacji z nawigacją
            DietApp(
                navController = navController,
                dietaryPreferencesViewModel = dietaryPreferencesViewModel,
                subscriptionStatus = subscriptionStatus,
                availableSubscriptions = availableSubscriptions, // Lista subskrypcji
                onPurchaseClick = { productId -> initiatePurchase(productId) } // Przekazujemy referencję do metody
            )
        }
    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("Billing", "Połączenie z usługą Billing nawiązane")

                    // Pobierz dostępne subskrypcje
                    fetchAvailableSubscriptions { productDetailsList ->
                        if (productDetailsList.isNotEmpty()) {
                            availableSubscriptions.clear()
                            availableSubscriptions.addAll(productDetailsList)
                            Log.d("Billing", "Zaktualizowano listę subskrypcji")
                        } else {
                            Log.e("Billing", "Nie znaleziono dostępnych subskrypcji")
                        }
                    }

                    // Sprawdzanie aktywnych subskrypcji
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder()
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    ) { result, purchases ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d("Billing", "Sukces zakupu 3")
                            val hasSubscription = purchases.any { it.products.contains("monthly_plan") }
                            subscriptionStatus.value = if (hasSubscription) "Subskrypcja aktywna" else "Subskrypcja nieaktywna"
                        } else {
                            subscriptionStatus.value = "Błąd sprawdzania subskrypcji"
                        }
                    }
                } else {
                    Log.e("Billing", "Błąd połączenia z usługą Billing: ${billingResult.debugMessage}")
                    subscriptionStatus.value = "Błąd połączenia z usługą Billing"
                }
            }

            override fun onBillingServiceDisconnected() {
                subscriptionStatus.value = "Rozłączono z usługą Billing"
            }
        })
    }

    private fun checkSubscriptionStatus(subscriptionStatus: MutableState<String>) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            // Logowanie odpowiedzi z Google Play
            Log.d("BillingClient", "Response Code: ${billingResult.responseCode}")
            purchases.forEach { purchase ->
                Log.d("BillingClient", "Purchased product: ${purchase.products.joinToString()}")
            }

            // Obsługa wyniku
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("Billing", "Sukces zakupu 1")
                val activeSubscriptions = purchases.filter { purchase ->
                    purchase.products.contains("monthly_plan")
                }
                subscriptionStatus.value = if (activeSubscriptions.isEmpty()) {
                    Log.d("SubscriptionStatus", "Ustawiam status: Subskrypcja nieaktywna")
                    "Subskrypcja nieaktywna"
                } else {
                    Log.d("SubscriptionStatus", "Ustawiam status: Subskrypcja aktywna")
                    "Subskrypcja aktywna"
                }
            } else {
                subscriptionStatus.value = "Błąd sprawdzania subskrypcji"
            }
        }
    }

    private fun initiatePurchase(productId: String) {
        if (!billingClient.isReady) {
            Log.e("Billing", "Błąd: BillingClient nie jest gotowy do użycia")
            return
        }

        // Logowanie zdarzenia do Facebook SDK
        val logger = AppEventsLogger.newLogger(this)
        logger.logEvent("start_subscription")

        val productDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(productDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val offerToken = productDetails.subscriptionOfferDetails?.get(0)?.offerToken ?: return@queryProductDetailsAsync

                val purchaseParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .setOfferToken(offerToken) // Dodaj offerToken
                                .build()
                        )
                    )
                    .build()

                billingClient.launchBillingFlow(this, purchaseParams)
            } else {
                Log.e("Billing", "Nie udało się pobrać szczegółów produktu: ${billingResult.debugMessage}")
            }
        }
    }

    private fun fetchAvailableSubscriptions(onResult: (List<ProductDetails>) -> Unit) {
        if (!billingClient.isReady) {
            Log.e("Billing", "BillingClient nie jest gotowy. Nie można pobrać subskrypcji.")
            onResult(emptyList())
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("monthly_plan") // Identyfikator subskrypcji
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("Billing", "Pobrano ${productDetailsList.size} dostępnych subskrypcji")
                onResult(productDetailsList)
            } else {
                Log.e("Billing", "Błąd pobierania subskrypcji: ${billingResult.debugMessage}")
                onResult(emptyList()) // Przekaż pustą listę w przypadku błędu
            }
        }
    }
}


@Composable
fun DietApp(
    navController: NavHostController, // Dodaj navController jako parametr
    dietaryPreferencesViewModel: DietaryPreferencesViewModel,
    subscriptionStatus: MutableState<String>,
    availableSubscriptions: List<ProductDetails>, // Dodaj parametr
    onPurchaseClick: (String) -> Unit // Określamy typ lambdy
) {

    // val navController = rememberNavController()

    Scaffold { paddingValues ->

        NavHost(
            navController = navController,
            startDestination = Screen.Welcome.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    navController = navController,
                    dietaryPreferencesViewModel = dietaryPreferencesViewModel,
                    subscriptionStatus = subscriptionStatus // Przekazujemy parametr
                )
            }
            composable(Screen.Legal.route) {
                LegalScreen(navController)
            }
            composable(Screen.Terms.route) {
                TermsAndConditionsScreen(navController)
            }
            composable(Screen.Preferences.route) {
                DietaryPreferencesScreen(
                    navController,
                    dietaryPreferencesViewModel,
                    subscriptionStatus = subscriptionStatus // Przekazanie statusu subskrypcji
                )
            }
            composable(Screen.Input.route) {
                InputScreen(
                    navController,
                    dietaryPreferencesViewModel,
                    subscriptionStatus = subscriptionStatus // Przekazanie statusu subskrypcji
                )
            }
            composable(Screen.Summary.route) {
                SummaryScreen(
                    navController = navController,
                    dietaryPreferencesViewModel = dietaryPreferencesViewModel,
                    subscriptionStatus = subscriptionStatus // Przekazanie zmiennej
                )
            }
            composable(Screen.DietPlan.route + "?forceGenerate={forceGenerate}") { backStackEntry ->
                val forceGenerate = backStackEntry.arguments?.getString("forceGenerate")?.toBoolean() ?: false
                DietPlanScreen(
                    navController = navController,
                    dietaryPreferencesViewModel = dietaryPreferencesViewModel,
                    forceGenerate = forceGenerate,
                    subscriptionStatus = subscriptionStatus // Przekazanie parametru
                )
            }
            composable(Screen.Subscription.route) {
                SubscriptionScreen(
                    navController = navController,
                    subscriptionStatus = subscriptionStatus,
                    availableSubscriptions = availableSubscriptions,
                    onPurchaseClick = onPurchaseClick
                )
            }
            composable(Screen.SuccessSub.route) {
                SuccessScreen(
                    navController = navController,
                    dietaryPreferencesViewModel = dietaryPreferencesViewModel, // Przekazanie ViewModel
                    subscriptionStatus = subscriptionStatus // Przekazanie statusu subskrypcji
                )
            }
            composable(Screen.DietDesc.route) {
                DietDescription(
                    navController = navController,
                    subscriptionStatus = subscriptionStatus // Przekazanie statusu subskrypcji
                )
            }
            composable("workout") { WorkoutScreen(navController, subscriptionStatus) }
            composable("workout_result") { WorkoutResultScreen(navController) }
            // 🏆 Dodajemy ekran gratulacji do nawigacji!
            composable("congratulations_screen") { CongratulationsScreen(navController) }
        }
    }
}

fun createHttpClient(): HttpClient {
    return HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // 60 sekund
            connectTimeoutMillis = 60_000 // 30 sekund
            socketTimeoutMillis = 120_000 // 60 sekund
        }
    }
}

@Composable
fun ActionTile(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp), // Łagodne zaokrąglenia rogów
        modifier = Modifier
            .fillMaxWidth(if (fullWidth) 1f else 0.5f)
            .height(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) backgroundColor else Color.Gray,
            contentColor = Color.White
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun WelcomeScreen(
    navController: NavHostController,
    dietaryPreferencesViewModel: DietaryPreferencesViewModel,
    subscriptionStatus: MutableState<String>
) {

    // 🎨 Definicje kolorów przycisków
    val dietColor = Color(0xFF7762ac) // Zielony
    val dietSavedColor = Color(0xFF6850a5) // Ciemniejszy zielony
    val workoutColor = Color(0xFF2196F3) // Niebieski
    val workoutSavedColor = Color(0xFF1976D2) // Ciemniejszy niebieski
    val dietTypesColor = Color(0xFF7762ac) // Pomarańczowy
    val subscriptionColor = Color(0xFFffea00) // Złoty

    val context = LocalContext.current
    val preferences = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)

    val savedDietPlan by remember { mutableStateOf(dietaryPreferencesViewModel.loadDietPlan(context)) }
    val savedWorkoutPlan by remember { mutableStateOf(preferences.getString("saved_workout", null)) }

    // 🟢 Tło ekranu z gradientem
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEDE7F6), Color(0xFFFFF9C4))
                )
            )
            .verticalScroll(rememberScrollState()) // ✅ Przewijanie całego ekranu
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🔹 Logo aplikacji
            Image(
                painter = painterResource(id = R.drawable.logo_512x512_dietbyai),
                contentDescription = "Ikona aplikacji",
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 🔹 Nagłówek powitalny
            Text(
                "Witaj w DietByAI!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Zacznij swoją przygodę z AI i osiągnij swoją wymarzoną sylwetkę!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 🟢 Siatka kafelków z określoną wysokością
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp), // ✅ Mniejszy odstęp między kafelkami
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp) // ✅ Mniejsza wysokość siatki
            )
            {
                item {
                    ActionTile(
                        text = "Generator\ndiety AI",
                        icon = Icons.Default.RestaurantMenu,
                        backgroundColor = dietColor,
                        onClick = { navController.navigate(Screen.Preferences.route) }
                    )
                }
                item {
                    ActionTile(
                        text = "Twoja dieta",
                        icon = Icons.Default.Fastfood,
                        backgroundColor = dietSavedColor,
                        enabled = savedDietPlan != null,
                        onClick = {
                            if (savedDietPlan != null) {
                                navController.navigate(Screen.DietPlan.route)
                            } else {
                                navController.navigate(Screen.DietPlan.route + "?forceGenerate=true")
                            }
                        }
                    )
                }
                item {
                    ActionTile(
                        text = "Generator\ntreningu AI",
                        icon = Icons.Default.FitnessCenter,
                        backgroundColor = workoutColor,
                        onClick = { navController.navigate("workout") }
                    )
                }
                item {
                    ActionTile(
                        text = "Twój trening",
                        icon = Icons.Default.DirectionsRun,
                        backgroundColor = workoutSavedColor,
                        enabled = savedWorkoutPlan != null,
                        onClick = { navController.navigate("workout_result") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 🔹 Rodzaje diet (jeden szeroki kafelek)
            ActionTile(
                text = "Rodzaje diet",
                icon = Icons.Default.Info,
                backgroundColor = dietTypesColor,
                fullWidth = true,
                onClick = { navController.navigate(Screen.DietDesc.route) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 🟢 Sekcja subskrypcji
            Button(
                onClick = { navController.navigate(Screen.Subscription.route) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = subscriptionColor,
                    contentColor = Color.Black
                )
            ) {
                Text("🛒 Subskrypcja", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status subskrypcji
            Text(
                text = "Status subskrypcji:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subscriptionStatus.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 🟢 Linki prawne
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Informacje prawne",
                    modifier = Modifier.clickable { navController.navigate(Screen.Legal.route) },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Regulamin",
                    modifier = Modifier.clickable { navController.navigate(Screen.Terms.route) },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )

            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🟢 Stopka
            Text(
                text = "© 2025 DietByAI. Wszelkie prawa zastrzeżone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "dietbyai.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}



@Composable
fun DietaryPreferencesScreen(
    navController: NavHostController,
    dietaryPreferencesViewModel: DietaryPreferencesViewModel,
    subscriptionStatus: MutableState<String> // Dodano parametr
) {
    val allergenOptions = listOf("Orzechy", "Gluten", "Laktoza", "Soja", "Ryby")
    val dietTypes = listOf(
        "Bezglutenowa",
        "DASH",
        "Ketogeniczna",
        "Niskotłuszczowa",
        "Niskowęglowodanowa",
        "Paleo",
        "Standardowa",
        "Śródziemnomorska",
        "Wegańska",
        "Wegetariańska",
        "Wysokobiałkowa"
    )
    val difficultyLevels = listOf("Łatwe", "Średnie", "Trudne")
    var dietTypeError by remember { mutableStateOf<String?>(null) } // Nowa zmienna błędu

    val context = LocalContext.current // Pobranie kontekstu
    // Przewijana zawartość
    LazyColumn(
        modifier = Modifier
            .background(Color.White) // Białe tło
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Typ diety

        item {
            Text("\uD83E\uDD57 Preferencje żywieniowe", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            // 🔥 Wymuszenie wyboru darmowej diety po wygaśnięciu subskrypcji
            LaunchedEffect(subscriptionStatus.value) {
                if (subscriptionStatus.value != "Subskrypcja aktywna" &&
                    dietaryPreferencesViewModel.dietType in listOf(
                        "Bezglutenowa", "DASH", "Ketogeniczna", "Niskotłuszczowa", "Niskowęglowodanowa",
                        "Paleo", "Śródziemnomorska", "Wegańska", "Wegetariańska", "Wysokobiałkowa"
                    )
                ) {
                    dietaryPreferencesViewModel.dietType = "Standardowa"
                    dietaryPreferencesViewModel.savePreferences(context)
                }
            }

            DropdownMenuField(
                options = dietTypes,
                selectedOption = dietaryPreferencesViewModel.dietType,
                onOptionSelected = {
                    dietaryPreferencesViewModel.dietType = it
                    dietaryPreferencesViewModel.savePreferences(context)
                },
                label = "✔ Typ diety",
                subscriptionStatus = subscriptionStatus.value, // Przekazanie statusu subskrypcji
                restrictOptions = true, // Włącz ograniczenia
                premiumOptions = listOf(
                    "Bezglutenowa",
                    "DASH",
                    "Ketogeniczna",
                    "Niskotłuszczowa",
                    "Niskowęglowodanowa",
                    "Paleo",
                    "Śródziemnomorska",
                    "Wegańska",
                    "Wegetariańska",
                    "Wysokobiałkowa"
                )
            )

            // Wyświetlenie błędu, jeśli jest
            dietTypeError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Dodanie ikony przenoszącej do opisu diet
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                //  .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.navigate("DietDesc") } // Nawigacja do strony z opisem diet
                ) {
                    Icon(
                        imageVector = Icons.Default.Help, // Znak zapytania
                        contentDescription = "Opis diet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                //   Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Więcej o rodzajach diet",
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable { navController.navigate(Screen.DietDesc.route) },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Text(
                text = "✔ Wybierz alergeny, które chcesz wykluczyć z diety:",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        items(allergenOptions) { allergen ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = dietaryPreferencesViewModel.selectedAllergens.contains(allergen),
                    onCheckedChange = { isChecked ->
                        dietaryPreferencesViewModel.selectedAllergens = if (isChecked) {
                            dietaryPreferencesViewModel.selectedAllergens + allergen
                        } else {
                            dietaryPreferencesViewModel.selectedAllergens - allergen
                        }
                        dietaryPreferencesViewModel.savePreferences(context) // Zapisz zmiany
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = allergen,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(0.dp)
                )
            }
        }

        item {
            OutlinedTextField(
                value = dietaryPreferencesViewModel.excludedIngredients,
                onValueChange = {
                    if (subscriptionStatus.value == "Subskrypcja aktywna") {
                        dietaryPreferencesViewModel.excludedIngredients = it
                        dietaryPreferencesViewModel.savePreferences(context)
                    }
                },
                label = { Text("Wykluczone składniki (wpisz po przecinku)") },
                placeholder = { Text("Np. czosnek, cebula") },
                readOnly = subscriptionStatus.value != "Subskrypcja aktywna", // Pole tylko do odczytu bez subskrypcji
                modifier = Modifier.fillMaxWidth()
            )

            if (subscriptionStatus.value != "Subskrypcja aktywna") {
                Text(
                    text = "Opcja dostępna tylko dla subskrybentów.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            OutlinedTextField(
                value = dietaryPreferencesViewModel.preferredIngredients,
                onValueChange = {
                    if (subscriptionStatus.value == "Subskrypcja aktywna") {
                        dietaryPreferencesViewModel.preferredIngredients = it
                        dietaryPreferencesViewModel.savePreferences(context) // Zapisz dane przy zmianie
                    }
                },
                label = { Text("Preferowane składniki (wpisz po przecinku)") },
                placeholder = { Text("Np. kurczak, jabłka") },
                readOnly = subscriptionStatus.value != "Subskrypcja aktywna", // Pole tylko do odczytu bez subskrypcji
                modifier = Modifier.fillMaxWidth()
            )

            if (subscriptionStatus.value != "Subskrypcja aktywna") {
                Text(
                    text = "Opcja dostępna tylko dla subskrybentów.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            DropdownMenuField(
                options = difficultyLevels,
                selectedOption = dietaryPreferencesViewModel.difficultyLevel,
                onOptionSelected = {
                    dietaryPreferencesViewModel.difficultyLevel = it
                    dietaryPreferencesViewModel.savePreferences(context) // Zapisz dane przy zmianie
                },
                label = "✔ Poziom trudności przygotowania posiłków",
                subscriptionStatus = subscriptionStatus.value, // Dodano
                restrictOptions = false // Brak ograniczeń
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (dietaryPreferencesViewModel.dietType == "Wybierz typ diety") {
                    dietTypeError = "Musisz wybrać typ diety, aby kontynuować." // Ustaw błąd
                } else {
                    dietaryPreferencesViewModel.savePreferences(context) // Zapisz dane przy kliknięciu
                    navController.navigate(Screen.Input.route)
                }
            }) {
                Text("✨ Zapisz i kontynuuj")
            }

            // Przycisk dla użytkowników bez subskrypcji
            if (subscriptionStatus.value != "Subskrypcja aktywna") {

                Spacer(modifier = Modifier.height(16.dp))

                // Przycisk subskrypcji
                Text(
                    "Odblokuj wszystkie opcje już teraz!",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center, // Wyśrodkowanie tekstu
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate(Screen.Subscription.route) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700), // Złoty kolor tła przycisku
                        contentColor = Color.Black         // Czarny kolor tekstu dla kontrastu
                    ),
                    modifier = Modifier.padding(start = 8.dp) // Opcjonalne odsunięcie
                ) {
                    Text(
                        text = "🛒 Kup subskrypcję",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    "\uD83D\uDD25 Skorzystaj z promocji",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

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


@Composable
fun DropdownMenuField(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    label: String,
    subscriptionStatus: String, // Status subskrypcji
    restrictOptions: Boolean = false, // Ograniczenia na poziomie typu diety
    premiumOptions: List<String> = emptyList() // Lista dodatkowych opcji premium
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp)) // ✅ Tło na biało
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)) // ✅ Ramka
                .padding(4.dp) // ✅ Dodatkowe odsunięcie od krawędzi
        ) {
            Button(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White) // ✅ Białe tło
            ) {
                Text(selectedOption, color = Color.Black) // ✅ Czarny tekst
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color.White) // ✅ Białe tło rozwijanego menu
            ) {
                options.forEach { option ->
                    val isPro = restrictOptions && premiumOptions.contains(option)
                    val isEnabled = !isPro || subscriptionStatus == "Subskrypcja aktywna"

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    option,
                                    color = if (isEnabled) Color.Black else Color.Gray // ✅ Wyszarzenie tekstu
                                )
                                if (isPro && subscriptionStatus != "Subskrypcja aktywna") {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(pro)",
                                        color = Color.Red,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        onClick = {
                            if (isEnabled) {
                                onOptionSelected(option)
                            }
                            expanded = false
                        },
                        enabled = isEnabled // ✅ Opcje Pro są zablokowane
                    )
                }
            }
        }
    }
}

@Composable
fun InputScreen(
    navController: NavHostController,
    dietaryPreferencesViewModel: DietaryPreferencesViewModel,
    subscriptionStatus: MutableState<String> // Dodano
) {

    var ageError by remember { mutableStateOf<String?>(null) }
    var heightError by remember { mutableStateOf<String?>(null) }
    var weightError by remember { mutableStateOf<String?>(null) }
    var genderError by remember { mutableStateOf<String?>(null) }
    var activityLevelError by remember { mutableStateOf<String?>(null) }
    var dietGoalError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current // Pobranie kontekstu

    LazyColumn(
        modifier = Modifier
            .background(Color.White) // Białe tło
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text("\uD83D\uDCDD Twoje dane", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            // Wiek
            OutlinedTextField(
                value = dietaryPreferencesViewModel.age,
                onValueChange = {
                    dietaryPreferencesViewModel.age = it
                    dietaryPreferencesViewModel.savePreferences(context) // Zapisanie danych
                },
                label = { Text("Wiek (lata)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            // Wzrost
            OutlinedTextField(
                value = dietaryPreferencesViewModel.height,
                onValueChange = {
                    dietaryPreferencesViewModel.height = it
                    dietaryPreferencesViewModel.savePreferences(context) // Zapisanie danych
                },
                label = { Text("Wzrost (cm)") },
                isError = heightError != null,
                modifier = Modifier.fillMaxWidth()
            )
            heightError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            // Waga
            OutlinedTextField(
                value = dietaryPreferencesViewModel.weight,
                onValueChange = {
                    dietaryPreferencesViewModel.weight = it
                    dietaryPreferencesViewModel.savePreferences(context) // Zapisanie danych
                },
                label = { Text("Waga (kg)") },
                isError = weightError != null,
                modifier = Modifier.fillMaxWidth()
            )
            weightError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            // Płeć
            DropdownMenuField(
                options = listOf("Mężczyzna", "Kobieta"),
                selectedOption = dietaryPreferencesViewModel.gender,
                onOptionSelected = {
                    dietaryPreferencesViewModel.gender = it
                    dietaryPreferencesViewModel.savePreferences(context) // Zapisujemy wybór do SharedPreferences
                },
                label = "✔ Płeć",
                subscriptionStatus = subscriptionStatus.value, // Dodano
                restrictOptions = false // Włącz ograniczenia

            )
            genderError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            // Liczba posiłków
            DropdownMenuField(
                options = listOf("3", "4", "5", "6"),
                selectedOption = dietaryPreferencesViewModel.mealCount,
                onOptionSelected = {
                    dietaryPreferencesViewModel.mealCount = it
                    dietaryPreferencesViewModel.savePreferences(context) // Zapisujemy wybór do SharedPreferences
                },
                label = "✔ Liczba posiłków",
                subscriptionStatus = subscriptionStatus.value, // Dodano
                restrictOptions = true, // Włącz ograniczenia
                premiumOptions = listOf("4", "5", "6") // Dodatkowe opcje premium
            )
        }
        item {
            // Poziom aktywności
            DropdownMenuField(
                options = listOf(
                    "Siedzący tryb życia",
                    "Lekka aktywność",
                    "Umiarkowana aktywność",
                    "Duża aktywność",
                    "Bardzo duża aktywność"
                ),
                selectedOption = dietaryPreferencesViewModel.activityLevel,
                onOptionSelected = {
                    dietaryPreferencesViewModel.activityLevel = it
                    dietaryPreferencesViewModel.savePreferences(context) // Zapisujemy wybór do SharedPreferences
                },
                label = "✔ Poziom aktywności",
                subscriptionStatus = subscriptionStatus.value // Dodano
            )
            activityLevelError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
        item {

            // 🔥 Wymuszenie wyboru darmowego celu diety po wygaśnięciu subskrypcji
            LaunchedEffect(subscriptionStatus.value) {
                if (subscriptionStatus.value != "Subskrypcja aktywna" &&
                    dietaryPreferencesViewModel.dietGoal in listOf("Redukcja masy ciała", "Budowanie masy mięśniowej")
                ) {
                    dietaryPreferencesViewModel.dietGoal = "Utrzymanie wagi" // ✅ Zmiana na darmową opcję
                    dietaryPreferencesViewModel.savePreferences(context)
                }
            }

            // Cel diety
            DropdownMenuField(
                options = listOf("Redukcja masy ciała", "Budowanie masy mięśniowej", "Utrzymanie wagi"),
                selectedOption = dietaryPreferencesViewModel.dietGoal,
                onOptionSelected = {
                    dietaryPreferencesViewModel.dietGoal = it
                    dietaryPreferencesViewModel.savePreferences(context)
                },
                label = "✔ Cel diety",
                subscriptionStatus = subscriptionStatus.value, // Przekazanie statusu subskrypcji
                restrictOptions = true, // Włącz ograniczenia
                premiumOptions = listOf("Redukcja masy ciała", "Budowanie masy mięśniowej") // "Utrzymanie wagi" jest dostępne
            )
            dietGoalError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // Przycisk zatwierdzenia
            Button(onClick = {
                // Reset błędów i walidacja
                ageError = null
                heightError = null
                weightError = null
                genderError = null
                activityLevelError = null
                dietGoalError = null

                if (dietaryPreferencesViewModel.age.isEmpty() || dietaryPreferencesViewModel.age.toIntOrNull() == null || dietaryPreferencesViewModel.age.toInt() <= 0) {
                    ageError = "Podaj prawidłowy wiek"
                }
                if (dietaryPreferencesViewModel.height.isEmpty() || dietaryPreferencesViewModel.height.toIntOrNull() == null || dietaryPreferencesViewModel.height.toInt() <= 0) {
                    heightError = "Podaj prawidłowy wzrost"
                }
                if (dietaryPreferencesViewModel.weight.isEmpty() || dietaryPreferencesViewModel.weight.toIntOrNull() == null || dietaryPreferencesViewModel.weight.toInt() <= 0) {
                    weightError = "Podaj prawidłową wagę"
                }
                if (dietaryPreferencesViewModel.gender == "Wybierz") {
                    genderError = "Wybierz płeć"
                }
                if (dietaryPreferencesViewModel.activityLevel == "Wybierz") {
                    activityLevelError = "Wybierz poziom aktywności"
                }
                if (dietaryPreferencesViewModel.dietGoal == "Wybierz cel") {
                    dietGoalError = "Wybierz cel diety"
                }

                if (ageError == null && heightError == null && weightError == null &&
                    genderError == null && activityLevelError == null && dietGoalError == null
                ) {
                    navController.navigate(Screen.Summary.route)
                }
            }) {
                Text("✨ Zapisz i kontynuuj")
            }
        }
        item {

            // Przycisk dla użytkowników bez subskrypcji
            if (subscriptionStatus.value != "Subskrypcja aktywna") {

                Spacer(modifier = Modifier.height(16.dp))

                // Przycisk subskrypcji
                Text("Odblokuj wszystkie opcje już teraz.", color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate(Screen.Subscription.route) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700), // Złoty kolor tła przycisku
                        contentColor = Color.Black         // Czarny kolor tekstu dla kontrastu
                    ),
                    modifier = Modifier.padding(start = 8.dp) // Opcjonalne odsunięcie
                ) {
                    Text(
                        text = "🛒 Kup subskrypcję",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text("\uD83D\uDD25 Skorzystaj z promocji", color = Color.Red)
            }
        }
        item {
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

@Composable
fun SummaryScreen(
    navController: NavHostController,
    dietaryPreferencesViewModel: DietaryPreferencesViewModel,
    subscriptionStatus: MutableState<String>
) {
    val tdee = dietaryPreferencesViewModel.calculateTDEE(
        weight = dietaryPreferencesViewModel.weight.toFloat(),
        height = dietaryPreferencesViewModel.height.toFloat(),
        age = dietaryPreferencesViewModel.age.toInt(),
        gender = dietaryPreferencesViewModel.gender,
        activityLevel = dietaryPreferencesViewModel.activityLevel
    )

    val adjustedTDEE = when (dietaryPreferencesViewModel.dietGoal) {
        "Redukcja masy ciała" -> tdee - 500
        "Budowanie masy mięśniowej" -> tdee + 500
        else -> tdee
    }

    LazyColumn(
        modifier = Modifier
            .background(Color.White) // Białe tło
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Nagłówek ekranu
        item {
            Text(
                "✔ Podsumowanie",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Sekcja zapotrzebowania kalorycznego
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFFFFF) // Jasne tło
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("\uD83D\uDD25 Twoje zapotrzebowanie kaloryczne:", style = MaterialTheme.typography.titleMedium)
                    Text("${tdee.toInt()} kcal", style = MaterialTheme.typography.titleLarge, color = Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("\uD83C\uDFAF Zapotrzebowanie do osiągnięcia celu:", style = MaterialTheme.typography.titleMedium)
                    Text("${adjustedTDEE.toInt()} kcal", style = MaterialTheme.typography.titleLarge, color = Color.Blue)
                }
            }
        }

        // Sekcja preferencji diety
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFFFFF) // Jasne tło
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🍴 Preferencje diety",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp) // Dodanie dolnego marginesu
                    )
                    Text("Typ diety: ${dietaryPreferencesViewModel.dietType}", style = MaterialTheme.typography.bodyLarge)
                    Text("Cel diety: ${dietaryPreferencesViewModel.dietGoal}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Wykluczone alergeny: ${
                            if (dietaryPreferencesViewModel.selectedAllergens.isEmpty()) "Brak"
                            else dietaryPreferencesViewModel.selectedAllergens.joinToString(", ")
                        }",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Wykluczone składniki: ${
                            dietaryPreferencesViewModel.excludedIngredients.ifEmpty { "Brak" }
                        }",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Preferowane składniki: ${
                            dietaryPreferencesViewModel.preferredIngredients.ifEmpty { "Brak" }
                        }",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Sekcja ustawień
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFFFFF) // Jasne tło
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "⚙️ Ustawienia",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp) // Dodanie dolnego marginesu
                    )
                    Text(
                        "Płeć: ${dietaryPreferencesViewModel.gender}, ${dietaryPreferencesViewModel.age} lat" ,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Wzrost: ${dietaryPreferencesViewModel.height}" ,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Poziom trudności przygotowania posiłków: ${dietaryPreferencesViewModel.difficultyLevel}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Liczba posiłków: ${dietaryPreferencesViewModel.mealCount}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Informacja o subskrypcji
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    /*
                    .background(
                        if (subscriptionStatus.value == "Subskrypcja aktywna") Color(0xFFDFF5E9)
                        else Color(0xFFFFE6E6)
                    )

                     */
                    .padding(8.dp),
                contentAlignment = Alignment.Center // Wyśrodkowanie tekstu w pionie i poziomie
            ) {
                Text(
                    text = if (subscriptionStatus.value == "Subskrypcja aktywna") {
                        "⏳ Generowanie diety możliwe co 1 godzinę."
                    } else {
                        "Dietę możesz wygenerować 1 dziennie.\n✨ Nie czekaj na kolejny dzień. Odblokuj subskrypcję i uzyskaj dostęp do nowej diety już teraz!"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Przyciski generowania diety i ostrzeżenie
        item {
            Button(
                onClick = { navController.navigate(Screen.DietPlan.route + "?forceGenerate=true") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text(
                    "✨ Wygeneruj dietę na 7 dni",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Przycisk dla użytkowników bez subskrypcji
            if (subscriptionStatus.value != "Subskrypcja aktywna") {

                Spacer(modifier = Modifier.height(16.dp))

                // Przycisk subskrypcji
                Text("Twoja wymarzona sylwetka zaczyna się dziś – działaj teraz!", color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate(Screen.Subscription.route) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700), // Złoty kolor tła przycisku
                        contentColor = Color.Black         // Czarny kolor tekstu dla kontrastu
                    ),
                    modifier = Modifier.padding(start = 8.dp) // Opcjonalne odsunięcie
                ) {
                    Text(
                        text = "🛒 Kup subskrypcję",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text("\uD83D\uDD25 Skorzystaj z promocji", color = Color.Red)
            }

            Text(
                text = "Pamiętaj! Wygenerowanie diety spowoduje usunięcie poprzedniej.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                textAlign = TextAlign.Center
            )
        }

        // Nawigacja wstecz i panel główny
        item {
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


@Composable
fun DietPlanScreen(
    navController: NavHostController,
    dietaryPreferencesViewModel: DietaryPreferencesViewModel,
    forceGenerate: Boolean = false,
    subscriptionStatus: MutableState<String> // Dodano parametr
) {
    val context = LocalContext.current
    var remainingTime by remember { mutableStateOf(0L) }
    val client = remember { createHttpClient() }
    var dietPlan by remember { mutableStateOf<List<DietDay>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCooldown by remember { mutableStateOf(false) } // Dodano dla blokady czasowej
    var progress by remember { mutableFloatStateOf(0.00f) } // Start od 1% (0.01)

    LaunchedEffect(forceGenerate) {
        if (!forceGenerate) {
            val savedDietPlan = dietaryPreferencesViewModel.loadDietPlan(context)
            if (savedDietPlan != null) {
                dietPlan = savedDietPlan
                isLoading = false
                return@LaunchedEffect
            }
        }

        val hasSubscription = subscriptionStatus.value == "Subskrypcja aktywna"

        if (!dietaryPreferencesViewModel.canGenerateNewDiet(context, hasSubscription)) {
            isCooldown = true
            remainingTime = dietaryPreferencesViewModel.getRemainingCooldownTime(context, hasSubscription)
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null


        try {
            coroutineScope {
                val deferredPlans = (1..7).map { day ->
                    async {
                        fetchDietPlan(client, day, dietaryPreferencesViewModel)
                    }
                }

                val completedPlans = mutableListOf<DietDay>()
                deferredPlans.forEachIndexed { index, deferred ->
                    try {
                        val dietDay = deferred.await()
                        completedPlans.add(dietDay)
                        dietPlan = completedPlans.toList()
                    } catch (e: Exception) {
                        errorMessage = "Błąd generowania dnia ${index + 1}: ${e.message}"
                    } finally {
                        val simulatedDelay = (1000L..2000L).random()
                        for (step in 1..100) {
                            delay(simulatedDelay / 100) // Płynna aktualizacja co 1% kroku
                            progress = ((index.toFloat() + step / 100f) / 7f).coerceIn(0f, 1f)
                        }
                    }
                }

                if (completedPlans.isNotEmpty()) {
                    dietaryPreferencesViewModel.saveDietPlan(context, completedPlans)
                    dietaryPreferencesViewModel.updateLastGenerationTime(context) // Zapisz czas generowania

                    // Logowanie zdarzenia do Facebook SDK
                    val logger = AppEventsLogger.newLogger(context)
                    val params = Bundle().apply {
                        putString("diet_type", dietaryPreferencesViewModel.dietType)
                        putString("gender", dietaryPreferencesViewModel.gender)
                        putString("age", dietaryPreferencesViewModel.age)
                        putInt("days_generated", completedPlans.size)
                    }
                    logger.logEvent("DietGenerated", params)

                }
            }
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
            progress = 1f // Ustaw pełny postęp po zakończeniu
        }
    }

// Drugi LaunchedEffect - odświeżanie czasu blokady
    LaunchedEffect(isCooldown) {
        val hasSubscription = subscriptionStatus.value == "Subskrypcja aktywna"
        while (isCooldown) {
            remainingTime = dietaryPreferencesViewModel.getRemainingCooldownTime(context, hasSubscription)
            delay(1000L) // Odświeżaj co sekundę
            if (remainingTime <= 0L) {
                isCooldown = false
            }
        }
    }

    Column(
        modifier = Modifier
            .background(Color.White) // Białe tło
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .background(Color.White) // Białe tło
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("\uD83D\uDCC5 Plan diety na 7 dni", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            when {
                isCooldown -> {
                    val days = remainingTime / (24 * 60 * 60 * 1000)
                    val hours = (remainingTime % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
                    val minutes = (remainingTime % (60 * 60 * 1000)) / (60 * 1000)
                    val seconds = (remainingTime % (60 * 1000)) / 1000

                    val timeParts = buildList {
                        if (days > 0) add("${days}d")
                        if (hours > 0) add("${hours}h")
                        if (minutes > 0) add("${minutes}m")
                        if (seconds > 0) add("${seconds}s")
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFE6E6), shape = RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tekst informacyjny
                            Text(
                                text = "⏳ Generowanie nowej diety będzie dostępne za ${timeParts.joinToString(" ")}.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )

                            // Przycisk dla użytkowników bez subskrypcji
                            if (subscriptionStatus.value != "Subskrypcja aktywna") {

                                Text(
                                    text = "Nie czekaj na kolejny dzień. Odblokuj subskrypcję i uzyskaj dostęp do nowej diety już teraz!",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )

                                // Przycisk subskrypcji
                                Button(
                                    onClick = { navController.navigate(Screen.Subscription.route) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFD700), // Złoty kolor tła przycisku
                                        contentColor = Color.Black         // Czarny kolor tekstu dla kontrastu
                                    ),
                                    modifier = Modifier.padding(start = 8.dp) // Opcjonalne odsunięcie
                                ) {
                                    Text(
                                        text = "🛒 Kup subskrypcję",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "Generowanie diety...",
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
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
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
                                    Color.White,
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
                                text = "Wyłączenie aplikacji lub naciśnięcie przycisku 'Panel główny' przerwie generowanie diety.",
                                color = Color.Black, // Czarna czcionka
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )

                        }
                    }
                }
                errorMessage != null -> {
                    Text("Błąd: $errorMessage", color = MaterialTheme.colorScheme.error)
                }
                dietPlan.isNullOrEmpty() -> {
                    Text(
                        text = "Nie masz jeszcze wygenerowanej diety. Kliknij poniżej, aby ją stworzyć.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { navController.navigate(Screen.DietPlan.route + "?forceGenerate=true") }) {
                        Text("Wygeneruj nową dietę")
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(dietPlan!!) { dayPlan ->
                            DietDayCard(dayPlan)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.navigate(Screen.Welcome.route) }) {
            Text("Panel główny")
        }
    }
}


@Composable
fun LegalScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f) // Umożliwia zajęcie dostępnej przestrzeni przez listę
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            item {
                Text(
                    text = "Informacje prawne",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            item {
                Text(
                    text = "1. Regulamin użytkowania:\n" +
                            "- Aplikacja DietByAI dostarcza informacji żywieniowych na podstawie danych podanych przez użytkownika.\n" +
                            "- Użytkowanie aplikacji oznacza akceptację niniejszego regulaminu.\n" +
                            "- Informacje generowane przez aplikację mają charakter informacyjny i nie zastępują konsultacji z lekarzem ani dietetykiem.\n" +
                            "- Podstawa prawna: Art. 8 ust. 1 ustawy o świadczeniu usług drogą elektroniczną (Dz.U. 2002 nr 144 poz. 1204 z późn. zm.).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = "2. Polityka prywatności:\n" +
                            "- Dane użytkownika, takie jak wiek, waga, wzrost, preferencje żywieniowe, są zbierane wyłącznie w celu personalizacji wyników.\n" +
                            "- Dane te nie są udostępniane osobom trzecim, z wyjątkiem sytuacji wymaganych przepisami prawa.\n" +
                            "- Aplikacja działa zgodnie z przepisami Rozporządzenia Parlamentu Europejskiego i Rady (UE) 2016/679 (RODO).\n" +
                            "- Masz prawo do wglądu w swoje dane, ich poprawiania, a także usunięcia. W sprawach związanych z danymi osobowymi prosimy o kontakt na adres: info@dietbyai.com.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = "3. Wyłączenie odpowiedzialności:\n" +
                            "- Aplikacja nie ponosi odpowiedzialności za skutki zastosowania się do wygenerowanych planów diety, szczególnie w przypadku problemów zdrowotnych.\n" +
                            "- Użytkownik przyjmuje do wiadomości, że wszelkie zmiany w diecie powinny być skonsultowane z lekarzem lub dietetykiem.\n" +
                            "- Podstawa prawna: Art. 12 ustawy o świadczeniu usług drogą elektroniczną.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = "4. Reklamacje:\n" +
                            "- Wszelkie reklamacje dotyczące działania aplikacji należy zgłaszać na adres: info@dietbyai.com.\n" +
                            "- Reklamacje będą rozpatrywane w terminie 14 dni roboczych od daty otrzymania zgłoszenia.\n" +
                            "- Podstawa prawna: Art. 8 ust. 3 ustawy o świadczeniu usług drogą elektroniczną.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = "5. Postanowienia końcowe:\n" +
                            "- Aplikacja nie gwarantuje pełnej dokładności ani aktualności wygenerowanych danych. W przypadku pytań dotyczących zdrowia, skonsultuj się z odpowiednim specjalistą.\n" +
                            "- Wszelkie spory wynikające z użytkowania aplikacji podlegają jurysdykcji sądów polskich i są rozstrzygane zgodnie z przepisami prawa polskiego.\n" +
                            "- Podstawa prawna: Kodeks cywilny, art. 353(1) (Dz.U. 1964 Nr 16 poz. 93 z późn. zm.).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = "6. Zastrzeżenie praw autorskich:\n" +
                            "- Wszystkie materiały, teksty i elementy graficzne dostępne w aplikacji są chronione prawem autorskim.\n" +
                            "- Kopiowanie, rozpowszechnianie lub modyfikowanie materiałów bez zgody właściciela aplikacji jest zabronione.\n" +
                            "- Podstawa prawna: Ustawa o prawie autorskim i prawach pokrewnych z dnia 4 lutego 1994 r. (Dz.U. 1994 nr 24 poz. 83 z późn. zm.).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp)) // Dystans między listą a przyciskiem

        Button(
            onClick = { navController.navigate(Screen.Welcome.route) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Powrót")
        }
    }
}

@Composable
fun TermsAndConditionsScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            items(listOf(
                "Regulamin aplikacji DietByAI",
                "",
                "1. Postanowienia ogólne",
                "1.1. Niniejszy regulamin określa zasady korzystania z aplikacji mobilnej DietByAI (zwanej dalej \"Aplikacją\").",
                "1.2. Właścicielem i operatorem Aplikacji jest F.H. \"LENART\" Tomasz Lenart, z siedzibą w 95-015 Głowno, NIP: PL7331307428.",
                "1.3. Korzystanie z Aplikacji oznacza akceptację niniejszego regulaminu.",
                "",
                "2. Definicje",
                "2.1. Użytkownik – osoba fizyczna korzystająca z Aplikacji.",
                "2.2. Subskrypcja – usługa abonamentowa umożliwiająca dostęp do rozszerzonych funkcji Aplikacji.",
                "2.3. Dieta – indywidualnie przygotowany plan żywieniowy na podstawie danych wprowadzonych przez Użytkownika.",
                "2.4. Dane osobowe – informacje umożliwiające identyfikację Użytkownika, przetwarzane zgodnie z polityką prywatności.",
                "",
                "3. Korzystanie z aplikacji",
                "3.1. Aplikacja oferuje zarówno darmowe funkcje, jak i funkcje premium dostępne w ramach Subskrypcji.",
                "3.2. Użytkownik zobowiązuje się do wprowadzania prawdziwych i aktualnych danych.",
                "3.3. Właściciel nie ponosi odpowiedzialności za skutki zastosowania się do wygenerowanych planów żywieniowych, szczególnie w przypadku problemów zdrowotnych.",
                "",
                "4. Subskrypcje i płatności",
                "4.1. Subskrypcja umożliwia dostęp do dodatkowych funkcji, takich jak częstsze generowanie diet.",
                "4.2. Płatności za Subskrypcję realizowane są za pośrednictwem Google Play.",
                "4.3. Użytkownik ma prawo do anulowania Subskrypcji zgodnie z polityką Google Play.",
                "",
                "5. Polityka prywatności",
                "5.1. Dane osobowe Użytkownika przetwarzane są zgodnie z obowiązującymi przepisami, w tym RODO.",
                "5.2. Szczegóły dotyczące przetwarzania danych osobowych znajdują się w Polityce Prywatności dostępnej w Aplikacji.",
                "5.3. Użytkownik ma prawo do wglądu, poprawiania i usunięcia swoich danych.",
                "",
                "6. Reklamacje",
                "6.1. Reklamacje dotyczące działania Aplikacji można zgłaszać na adres: info@dietbyai.com.",
                "6.2. Reklamacje będą rozpatrywane w terminie 14 dni roboczych.",
                "",
                "7. Wyłączenie odpowiedzialności",
                "7.1. Właściciel nie ponosi odpowiedzialności za błędy w danych wprowadzonych przez Użytkownika.",
                "7.2. Aplikacja nie gwarantuje pełnej zgodności wygenerowanych planów z indywidualnymi potrzebami zdrowotnymi Użytkownika.",
                "",
                "8. Postanowienia końcowe",
                "8.1. Regulamin może być zmieniany, a zmiany wchodzą w życie po ich opublikowaniu w Aplikacji.",
                "8.2. Wszelkie spory wynikające z korzystania z Aplikacji będą rozstrzygane przez właściwe sądy polskie.",
                "",
                "9. Kontakt",
                "W razie pytań prosimy o kontakt: info@dietbyai.com."
            )) { term ->
                if (term.isNotBlank()) {
                    Text(
                        text = term,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate("welcome") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Powrót")
        }
    }
}


// Wyświetlanie diety
@Composable
fun DietDayCard(dayPlan: DietDay) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Subtelny cień
        colors = CardDefaults.cardColors(
            containerColor = Color.White // Jasne tło dla profesjonalnego wyglądu
        ),
        shape = RoundedCornerShape(12.dp) // Zaokrąglone rogi
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nagłówek z nazwą dnia
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = dayPlan.day,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Posiłek
            Text(
                text = dayPlan.meal,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Separator
            Divider(color = Color.LightGray, thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Składniki odżywcze
            dayPlan.nutrients.forEach { (name, value) ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionScreen(
    navController: NavHostController,
    subscriptionStatus: MutableState<String>,
    availableSubscriptions: List<ProductDetails>,
    onPurchaseClick: (String) -> Unit
) {
    val context = LocalContext.current

    // Funkcja pomocnicza do otwierania zarządzania subskrypcjami
    fun openSubscriptionManagement(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/account/subscriptions")
            putExtra("package", context.packageName)
        }
        context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFF5F5F5)) // Jasne tło
            .padding(16.dp)
    ) {
        // Nagłówek z ikoną i status subskrypcji
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFC107), // Złoty kolor ikony
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Status: ${subscriptionStatus.value}",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (subscriptionStatus.value != "Subskrypcja aktywna") {
                // Sekcja z informacjami dlaczego warto
                item {
                    Text(
                        text = "Dlaczego warto wybrać subskrypcję?",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BenefitItem(
                            icon = Icons.Default.CheckCircle,
                            text = "Spersonalizowane plany żywieniowe dostosowane do Twoich potrzeb gwarantujące szybkie efekty."
                        )
                        BenefitItem(
                            icon = Icons.Default.Update,
                            text = "Automatyczne aktualizacje i nowe przepisy co tydzień."
                        )
                        BenefitItem(
                            icon = Icons.Default.ShoppingCart,
                            text = "Gotowe listy zakupów dla Twojej diety."
                        )
                        BenefitItem(
                            icon = Icons.Default.FitnessCenter,
                            text = "Więcej posiłków, zaawansowane cele oraz typy diety."
                        )
                        BenefitItem(
                            icon = Icons.Default.AccessTime,
                            text = "Skrócone limity czasowe: generowanie diety co 1 godzinę."
                        )
                    }
                }

                // Sekcja z rabatem
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        //  .padding(8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFE082) // Jasnożółte tło
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Rabat na pierwszy miesiąc!",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\uD83D\uDD25 Promocja niedługo się kończy...",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Zaplanuj swoją idealną dietę i osiągnij wymarzone cele.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

// Informacja o możliwości anulowania subskrypcji
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp), // Padding wewnętrzny dla tła
                    contentAlignment = Alignment.Center // Wyśrodkowanie zawartości Box
                ) {
                    Text(
                        text = "Możesz anulować subskrypcję w dowolnym momencie!",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF4CAF50), // Zielony kolor
                        textAlign = TextAlign.Center, // Wyśrodkowanie tekstu w komponencie Text
                        modifier = Modifier.fillMaxWidth() // Zapewnia szerokość dla TextAlign.Center
                    )
                }

                if (subscriptionStatus.value == "Subskrypcja aktywna") {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Przycisk subskrypcji
                    Button(
                        onClick = { openSubscriptionManagement(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700), // Złoty kolor tła przycisku
                            contentColor = Color.Black         // Czarny kolor tekstu dla kontrastu
                        ),
                        modifier = Modifier.padding(start = 8.dp) // Opcjonalne odsunięcie
                    ) {
                        Text(
                            text = "🛒 Zarządzaj subskrypcją",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Dla tych co mają subksrypcję

            if (subscriptionStatus.value == "Subskrypcja aktywna") {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF4CAF50).copy(alpha = 0.1f), // Jasnozielone tło
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite, // Ikona serca
                                contentDescription = null,
                                tint = Color(0xFFFF5252), // Czerwony kolor ikony
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Dziękujemy za wsparcie!",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Twoja subskrypcja pomaga nam rozwijać aplikację i dostarczać nowe funkcje!",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFEB3B) // Żółte tło dla motywacji
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Twoja dieta to Twój sukces!",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF000000)
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Pamiętaj, małe kroki prowadzą do wielkich zmian.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800), // Pomarańczowa ikona
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                        }
                    }
                }
            }


            // Lista dostępnych subskrypcji
            if (subscriptionStatus.value != "Subskrypcja aktywna") {
                item {
                    Text(
                        text = "Dostępne subskrypcje:",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        //    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                }

                items(availableSubscriptions) { product ->
                    SubscriptionCard(product = product, onPurchaseClick = onPurchaseClick)
                }
            }

            item{
                Button(
                    onClick = { navController.navigate(Screen.Welcome.route) }
                    //  modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Panel główny")
                }
            }
        }

    }
}


@Composable
fun BenefitItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4CAF50), // Zielony kolor
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SubscriptionCard(product: ProductDetails, onPurchaseClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases
                        ?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "Brak ceny",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Button(
                onClick = {
                    if (product.productId.isNotBlank()) {
                        onPurchaseClick(product.productId)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Kup teraz")
            }
        }
    }
}


private val dietPlanCache = mutableMapOf<Int, String>()

suspend fun fetchDietPlan(
    client: HttpClient,
    day: Int,
    preferences: DietaryPreferencesViewModel,
    maxAttempts: Int = 3
): DietDay {
    // Sprawdzenie pamięci podręcznej
    if (dietPlanCache.containsKey(day)) {
        return Json.decodeFromString(dietPlanCache[day]!!)
    }

    val tdee = preferences.calculateTDEE(
        weight = preferences.weight.toFloat(),
        height = preferences.height.toFloat(),
        age = preferences.age.toInt(),
        gender = preferences.gender,
        activityLevel = preferences.activityLevel
    )

    val adjustedTDEE = when (preferences.dietGoal) {
        "Redukcja masy ciała" -> tdee - 500
        "Budowanie masy mięśniowej" -> tdee + 500
        else -> tdee
    }

    val requestBody = Json.encodeToString(
        ChatRequest(
            model = "gpt-4-turbo",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = """
                    Twoje zapotrzebowanie kaloryczne wynosi: $adjustedTDEE kcal.
                    Proszę wygenerować dietę zgodną z tym zapotrzebowaniem na dzień $day według wytycznych:
                    - Cel diety: ${preferences.dietGoal}.
                    - Typ diety: ${preferences.dietType}.
                    - Liczba posiłków: ${preferences.mealCount}.
                    - Poziom trudności przygotowania posiłków: ${preferences.difficultyLevel}.
                    - Wykluczone alergeny: ${preferences.selectedAllergens.joinToString(", ")}.
                    - Wykluczone składniki: ${preferences.excludedIngredients}.
                    - Preferowane składniki: ${preferences.preferredIngredients}.
 
                    [Format odpowiedzi:]

                    🥗 Posiłek 1: Nazwa
                    
                    ✔ Lista składników:
                    - składnik 1: (x gramów)
                    - składnik 2: (y gramów)

                    ✔ Instrukcja przygotowania:
                    Opis kroków przygotowania posiłku.

                    ✔ Makroskładniki:
                    Kalorie: ...
					Białko: ...
					Tłuszcze: ...
					Węglowodany: ...

                    🥗 Posiłek 2: ...
                    ...

					✅ Podusmowanie dnia:
					Kalorie: ...
					Białko: ...
					Tłuszcze: ...
					Węglowodany: ...

                    🌟 Porada dnia:
                    (Napisz krótką poradę lub motywację.)
                        
                    🛒 Lista zakupów na cały dzień:
                    - składnik 1: (x gramów)
                    - składnik 2: (y gramów)

                Upewnij się, że plan jest różnorodny, zbilansowany pod względem odżywczym oraz odpowiada zapotrzebowaniu kalorycznemu $adjustedTDEE kcal. 
                Proszę unikać błędów językowych i dbać o poprawność nazw składników. 
                **Ważne! Stosuj się ściśle do każdego elementu wskazanego formatu odpowiedzi i nie formatuj odpowiedzi w innym formacie Markdown**.
                    """.trimIndent()
                )
            )
        )
    )

    var attempt = 0
    while (attempt < maxAttempts) {
        try {
            val response: HttpResponse = client.post("https://dietbyai.com/proxy.php") {
                headers {
                    append("X-API-KEY", "my-secret-key-82756287bdygfoetnl1000000") // Twój klucz API do autoryzacji
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) // Typ treści JSON
                }
                setBody(requestBody)
            }

            // Przechowaj treść odpowiedzi w zmiennej
            val responseBodyText = response.bodyAsText()

// Logowanie odpowiedzi LOGI tylko w testach
            // android.util.Log.d("API Request", "Ciało zapytania: $requestBody")
            //   android.util.Log.d("API Request", "Nagłówki: Authorization: Bearer <ukryty klucz>, Content-Type: application/json")
            //   android.util.Log.d("API Response", "Odpowiedź z API (Dzień $day): $responseBodyText")

            if (response.status.isSuccess()) {
                val choices = Json.parseToJsonElement(responseBodyText)
                    .jsonObject["choices"]?.jsonArray
                val dietContent = choices?.get(0)?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content

                // Dodaj wynik do pamięci podręcznej
                dietPlanCache[day] = Json.encodeToString(
                    DietDay(
                        day = "Dzień $day",
                        meal = dietContent ?: "Brak treści",
                        nutrients = listOf()
                    )
                )

                return DietDay(
                    day = "Dzień $day",
                    meal = dietContent ?: "Brak treści",
                    nutrients = listOf()
                )
            } else {
                throw Exception("Błąd: ${response.status.description}")
            }
        } catch (e: Exception) {
            attempt++
            if (attempt == maxAttempts) {
                throw Exception("Próba wygenerowania planu diety dla dnia $day zakończyła się niepowodzeniem: ${e.message}. Spróbuj ponownie później.")
            }
        }
    }
    throw Exception("Nieznany błąd podczas próby wygenerowania planu diety.")
}


@Composable
fun DietPlanCard(day: Int, dietContent: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Dzień $day",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = dietContent,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun DietPlanList(dietPlans: List<Pair<Int, String>>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(dietPlans) { (day, content) ->
            DietPlanCard(
                day = day,
                dietContent = content
            )
        }
    }
}

@Composable
fun SuccessScreen(
    navController: NavHostController,
    dietaryPreferencesViewModel: DietaryPreferencesViewModel, // Przekazanie ViewModel
    subscriptionStatus: MutableState<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Subskrypcja została zakupiona pomyślnie!",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate(Screen.Welcome.route) }) {
            Text("Powrót do ekranu głównego")
        }
    }
}


@Composable
fun DietDescription(
    navController: NavHostController,
    subscriptionStatus: MutableState<String> // Dodano parametr
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .background(Color.White) // Białe tło
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            // horizontalAlignment = Alignment.Start,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "Rodzaje diet",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            val diets = listOf(
                Diet(
                    name = "Bezglutenowa",
                    description = "Dieta eliminująca gluten, białko obecne w pszenicy, jęczmieniu i życie.",
                    benefits = "Zdrowie jelit, zmniejszenie wzdęć, poprawa samopoczucia.",
                    icon = Icons.Default.NoFood, // Ikona "brak jedzenia"
                    color = Color(0xFFFFE0B2)
                ),
                Diet(
                    name = "DASH",
                    description = "Dieta wspomagająca obniżenie ciśnienia krwi i wspierająca zdrowie serca.",
                    benefits = "Obniża ciśnienie krwi, zmniejsza ryzyko cukrzycy.",
                    icon = Icons.Default.Favorite, // Ikona serca
                    color = Color(0xFFFFCDD2)
                ),
                Diet(
                    name = "Ketogeniczna",
                    description = "Dieta wysokotłuszczowa zmuszająca organizm do korzystania z tłuszczu jako źródła energii.",
                    benefits = "Skuteczna w redukcji wagi, poprawia wydolność umysłową.",
                    icon = Icons.Default.Fastfood, // Ikona jedzenia
                    color = Color(0xFFD1C4E9)
                ),
                Diet(
                    name = "Śródziemnomorska",
                    description = "Dieta bogata w zdrowe tłuszcze, owoce, warzywa i produkty pełnoziarniste.",
                    benefits = "Wspiera zdrowie serca, poprawia długość życia.",
                    icon = Icons.Default.LocalCafe, // Ikona filiżanki
                    color = Color(0xFFB3E5FC)
                ),
                Diet(
                    name = "Niskotłuszczowa",
                    description = "Dieta ograniczająca spożycie tłuszczów, promująca produkty pełnoziarniste i warzywa.",
                    benefits = "Obniża poziom cholesterolu, wspiera zdrowe odchudzanie.",
                    icon = Icons.Default.Eco, // Ikona listka
                    color = Color(0xFFC8E6C9)
                ),
                Diet(
                    name = "Niskowęglowodanowa",
                    description = "Dieta redukująca spożycie węglowodanów, zwiększająca ilość białek i tłuszczów.",
                    benefits = "Pomaga w odchudzaniu, poprawia kontrolę poziomu cukru.",
                    icon = Icons.Default.FitnessCenter, // Ikona hantli
                    color = Color(0xFFFFF9C4)
                ),
                Diet(
                    name = "Paleo",
                    description = "Dieta inspirowana sposobem odżywiania naszych przodków – wyklucza żywność przetworzoną.",
                    benefits = "Poprawia zdrowie jelit, wspiera naturalne odżywianie.",
                    icon = Icons.Default.Nature, // Ikona liścia natury
                    color = Color(0xFFFFCCBC)
                ),
                Diet(
                    name = "Standardowa",
                    description = "Zbilansowana dieta obejmująca różnorodne grupy produktów spożywczych w umiarkowanych ilościach.",
                    benefits = "Uniwersalna i łatwa do stosowania.",
                    icon = Icons.Default.RestaurantMenu, // Ikona menu
                    color = Color(0xFFB0BEC5)
                ),
                Diet(
                    name = "Wegańska",
                    description = "Dieta wykluczająca wszystkie produkty pochodzenia zwierzęcego, oparta na roślinach.",
                    benefits = "Korzystna dla środowiska, zmniejsza ryzyko chorób serca.",
                    icon = Icons.Default.Grass, // Ikona trawy
                    color = Color(0xFFA5D6A7)
                ),
                Diet(
                    name = "Wegetariańska",
                    description = "Dieta eliminująca mięso, ale zawierająca produkty mleczne i jajka.",
                    benefits = "Bogata w błonnik, łatwa do wdrożenia.",
                    icon = Icons.Default.LocalFlorist, // Ikona kwiatu
                    color = Color(0xFFEF9A9A)
                ),
                Diet(
                    name = "Wysokobiałkowa",
                    description = "Dieta zwiększająca spożycie białka, wspierająca regenerację mięśni i utratę tkanki tłuszczowej.",
                    benefits = "Pomaga w budowie mięśni, zmniejsza łaknienie.",
                    icon = Icons.Default.LocalFireDepartment, // Ikona ognia
                    color = Color(0xFFFFAB91)
                )
            )

            items(diets) { diet ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = diet.color),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = diet.icon,
                            contentDescription = "${diet.name} Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = diet.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diet.description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Korzyści: ${diet.benefits}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }


            if (subscriptionStatus.value != "Subskrypcja aktywna") {

                // Przycik zakupu subkrypcji

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Zdobądź dostęp do spersonalizowanych planów żywieniowych już dziś!",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        //     modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Przycisk subskrypcji
                    Button(
                        onClick = { navController.navigate(Screen.Subscription.route) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700), // Złoty kolor tła przycisku
                            contentColor = Color.Black         // Czarny kolor tekstu dla kontrastu
                        ),
                        modifier = Modifier.padding(start = 8.dp) // Opcjonalne odsunięcie
                    ) {
                        Text(
                            text = "🛒 Kup subskrypcję",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }


                // Sekcja z rabatem
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        //  .padding(8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFE082) // Jasnożółte tło
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Rabat na pierwszy miesiąc!",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\uD83D\uDD25 Promocja niedługo się kończy...",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Zaplanuj swoją idealną dietę i osiągnij wymarzone cele.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }


// Informacja o możliwości anulowania subskrypcji
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp), // Padding wewnętrzny dla tła
                        contentAlignment = Alignment.Center // Wyśrodkowanie zawartości Box
                    ) {
                        Text(
                            text = "Możesz anulować subskrypcję w dowolnym momencie!",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF4CAF50), // Zielony kolor
                            textAlign = TextAlign.Center, // Wyśrodkowanie tekstu w komponencie Text
                            modifier = Modifier.fillMaxWidth() // Zapewnia szerokość dla TextAlign.Center
                        )
                    }
                }

            } else {

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp), // Padding wewnętrzny dla tła
                        contentAlignment = Alignment.Center // Wyśrodkowanie zawartości Box
                    ) {
                        Text(
                            text = "Czy wybrałeś już swoją dietę? Jeśli tak, czas przejść do jej planowania!",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF4CAF50), // Zielony kolor
                            textAlign = TextAlign.Center, // Wyśrodkowanie tekstu w komponencie Text
                            modifier = Modifier.fillMaxWidth() // Zapewnia szerokość dla TextAlign.Center
                        )
                    }
                }

                item {
                    // Przycisk konfiguratora
                    Button(
                        onClick = { navController.navigate(Screen.Preferences.route) }
                    ) {
                        Text(
                            text = "✨ Konfigurator diety AI",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.popBackStack() }
            //   modifier = Modifier.fillMaxWidth()
        ) {
            Text("Powrót")
        }
    }
}

data class Diet(
    val name: String,
    val description: String,
    val benefits: String,
    val icon: ImageVector,
    val color: Color
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class DietDay(
    val day: String,
    val meal: String,
    val nutrients: List<Pair<String, String>> = emptyList()
)