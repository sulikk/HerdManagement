import java.util.Properties
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Wykrywa adres IPv4 komputera w sieci lokalnej, przez który wychodzi ruch do internetu.
 *
 * Gniazdo UDP „łączone” z adresem zewnętrznym nie wysyła żadnego pakietu — system jedynie
 * wybiera trasę, a tym samym interfejs domyślny. Dzięki temu pomijane są karty wirtualne
 * (Hyper-V, WSL, VirtualBox), które przy zwykłym przeglądaniu interfejsów bywają pierwsze.
 * Gdy trasy domyślnej brak, wybierany jest pierwszy aktywny adres prywatny.
 */
fun detectLanAddress(): String? {
    val viaRoute = runCatching {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 80)
            socket.localAddress
        }
    }.getOrNull()
    if (viaRoute is Inet4Address && viaRoute.isSiteLocalAddress) {
        return viaRoute.hostAddress
    }
    val virtual = Regex("(?i)virtual|vethernet|hyper-v|wsl|vmware|vbox|docker|loopback")
    return NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual && !virtual.containsMatchIn(it.displayName) }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress
}

/**
 * Adres backendu wkompilowywany w aplikację (`BuildConfig.BASE_URL`).
 *
 * Kolejność ustalania:
 * 1. klucz `herd.baseUrl` w `local.properties` (poza kontrolą wersji) — jawne nadpisanie,
 * 2. adres IP komputera w sieci lokalnej wykrywany przy każdym budowaniu — telefon w tej
 *    samej sieci Wi-Fi łączy się bez ręcznej konfiguracji,
 * 3. 10.0.2.2 — adres hosta widziany z emulatora, gdy komputer nie ma połączenia z siecią.
 */
val herdBaseUrl: String = run {
    val file = rootProject.file("local.properties")
    val configured = if (file.exists()) {
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("herd.baseUrl")
    } else {
        null
    }?.trim().orEmpty()
    val value = when {
        configured.isNotEmpty() && !configured.equals("auto", ignoreCase = true) -> configured
        else -> detectLanAddress()?.let { "http://$it:8000/" } ?: "http://10.0.2.2:8000/"
    }
    logger.lifecycle("HerdManagement: adres backendu = $value")
    // Retrofit wymaga adresu bazowego zakończonego ukośnikiem.
    if (value.endsWith("/")) value else "$value/"
}

android {
    namespace = "com.example.herdmanagementmobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.herdmanagementmobile"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "BASE_URL", "\"$herdBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core i Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager z integracją Hilt (synchronizacja offline)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Korutyny
    implementation(libs.kotlinx.coroutines.android)

    // DataStore (ustawienia aplikacji i stan synchronizacji)
    implementation(libs.androidx.datastore.preferences)

    // Komunikacja sieciowa (Retrofit, OkHttp, kotlinx.serialization)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // CameraX i ML Kit (skanowanie kolczyków)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // Testy
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
