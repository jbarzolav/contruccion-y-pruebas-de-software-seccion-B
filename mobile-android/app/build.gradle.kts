plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ejemplo.publicarproducto"
    compileSdk = 35
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.ejemplo.publicarproducto"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // AUD-33 - URL base del backend (Spring) y del backend de administración
        // (Django). Se pueden sobreescribir por variante de compilación sin
        // tocar el código fuente. Los valores son los de siempre.
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:9000/\"")
        buildConfigField("String", "ADMIN_BASE_URL", "\"http://10.0.2.2:8001/\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ---------------------------------------------------------------
        // Grupo C - Pruebas de integración contra el BACKEND REAL.
        //
        // No hay ninguna credencial escrita en el código: la URL y la
        // cuenta de pruebas llegan por variables de entorno y se pasan al
        // runner de instrumentación. Si no se configuran, esas pruebas
        // quedan OMITIDAS (no fallan) y el resto de la suite no cambia.
        //
        //   $env:PULGATEC_URL_REAL      = "http://10.0.2.2:9000/"
        //   $env:PULGATEC_CORREO_REAL   = "<cuenta de pruebas>"
        //   $env:PULGATEC_PASSWORD_REAL = "<contraseña de pruebas>"
        //   .\gradlew.bat integracionRealDebugAndroidTest -PpermiteEscrituraReal=true
        // ---------------------------------------------------------------
        testInstrumentationRunnerArguments["urlBaseReal"] =
            (project.findProperty("urlBaseReal") as String?) ?: System.getenv("PULGATEC_URL_REAL") ?: ""
        testInstrumentationRunnerArguments["correoReal"] = System.getenv("PULGATEC_CORREO_REAL") ?: ""
        testInstrumentationRunnerArguments["passwordReal"] = System.getenv("PULGATEC_PASSWORD_REAL") ?: ""
        // Cuenta con rol VENDEDOR: es la que puede publicar productos.
        testInstrumentationRunnerArguments["correoVendedorReal"] =
            (project.findProperty("correoVendedorReal") as String?)
                ?: System.getenv("PULGATEC_CORREO_VENDEDOR_REAL") ?: ""
        testInstrumentationRunnerArguments["passwordVendedorReal"] =
            (project.findProperty("passwordVendedorReal") as String?)
                ?: System.getenv("PULGATEC_PASSWORD_VENDEDOR_REAL") ?: ""
        testInstrumentationRunnerArguments["permiteEscrituraReal"] =
            (project.findProperty("permiteEscrituraReal") as String?) ?: "false"
        testInstrumentationRunnerArguments["permitirHostRemoto"] =
            (project.findProperty("permitirHostRemoto") as String?) ?: "false"
        // Identificadores de datos existentes que necesitan algunas pruebas.
        listOf("idProductoReal", "idVendedorReal", "idPedidoReal").forEach { clave ->
            testInstrumentationRunnerArguments[clave] = (project.findProperty(clave) as String?) ?: ""
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // AUD-33 - en AGP 8+ BuildConfig no se genera por defecto; es necesario
        // para leer BASE_URL y ADMIN_BASE_URL desde el código Kotlin.
        buildConfig = true
    }

    sourceSets {
        // Los esquemas de Room se versionan y se usan en las pruebas de migración.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

/**
 * Room: se exporta el esquema de cada versión de la base local para poder
 * escribir pruebas de migración verificables. No se usa
 * `fallbackToDestructiveMigration()`: la información local no se borra al
 * actualizar la aplicación.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // R1 - `LifecycleStartEffect`: liga el sondeo de HU39 al ciclo de vida
    // STARTED para que no haya peticiones HTTP en segundo plano.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Íconos Material (incluye SmartToy / WorkspacePremium / Inventory2 de la barra de pestañas)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Retrofit + OkHttp + Gson (cliente REST)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Offline First: Room (BD local) + WorkManager (sincronización con red)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    // Pruebas
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Servidor HTTP de pruebas en la JVM: permite verificar los repositorios
    // de productos y favoritos (rutas, query, cuerpo y multipart) sin
    // emulador. Misma versión de OkHttp (4.12.0) que usa la aplicación.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Servidor HTTP de pruebas: permite verificar la sincronización contra
    // respuestas HTTP reales del cliente Retrofit (nunca datos simulados en
    // la aplicación).
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // WorkManager en modo prueba: permite verificar la programacion unica,
    // la restriccion de red y la ejecucion real del sincronizador.
    androidTestImplementation("androidx.work:work-testing:2.9.1")
    // Room en modo prueba: permite verificar la migracion de la base local
    // comprobando que la informacion del usuario NO se borra al actualizarse.
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}

/**
 * Grupo C - Pruebas de integración contra el backend REAL.
 *
 * Ejecuta la suite instrumentada en el dispositivo. Las pruebas del paquete
 * `com.ejemplo.publicarproducto.integracion` solo se ejecutan si se
 * configuraron `PULGATEC_URL_REAL`, `PULGATEC_CORREO_REAL` y
 * `PULGATEC_PASSWORD_REAL` (ver `defaultConfig`); si no, quedan omitidas con
 * un mensaje que explica cómo habilitarlas. Las que escriben datos exigen
 * además `-PpermiteEscrituraReal=true`.
 */
tasks.register("integracionRealDebugAndroidTest") {
    group = "verification"
    description = "Ejecuta la suite instrumentada, incluidas las pruebas de integración real (Grupo C)."
    dependsOn("connectedDebugAndroidTest")
}

