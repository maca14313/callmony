import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("org.robolectric:robolectric:4.14.1")
                implementation("androidx.test:core-ktx:1.6.1")
                implementation("androidx.test.ext:junit-ktx:1.2.1")
                implementation("androidx.compose.ui:ui-test-junit4:1.7.3")
                implementation("androidx.compose.ui:ui-test-manifest:1.7.3")
            }
        }
    }
}

android {
    namespace = "com.callmony"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.callmony"
        minSdk = 23
        targetSdk = 34
        // v1.6.34 — solid call cards (v1.6.33's "lighter" was read as
        // TRANSPARENT — the row is a real surface again, light metrics
        // kept), the owe tabs open DATE/AMOUNT/NAME filter tabs (not
        // call-direction rows), the calculator display is a real editable
        // field (native long-press copy/paste/cut + handles), the PIN
        // sheet drops the forgot link + the 4th dot paints before submit,
        // the quick item moves into the ADD-ITEM flow as horizontal tabs
        // (Just amount / Items / Quick / Recent), a whole-app perf pass,
        // and the NO-DATA-LOSS layer: auto-backup to Downloads/Callmony on
        // every close + one-tap restore offer on a fresh install.
        // v1.6.37 — the annotated calculator rebuilt as ink-on-paper (one
        // inline flow, one tiny tag badge per number, an anchored label
        // popover, a stacked multi-line ledger with a running total) and
        // the entry amount = typed base + items + calculator (100 + 200
        // = 300, for every input mode).
        // v1.6.39 — the canvas calculator goes FULL SCREEN over the editor
        // (one unified native-editable active line in both modes — typed
        // text never disappears on a mode switch; compact keypad that the
        // native back button closes; small Aa/123/Next-line/Save buttons
        // on the canvas; long-press unlocks a locked line; per-line copy
        // icons; per-line totals share the last total's long-press menu)
        // and the main call keypad's container is compact with BIGGER keys.
        // v1.6.40 — numbers written in TEXT mode never calculate (a quote
        // marker rides them through the save), the TOTAL sits AFTER the
        // last entry on the paper itself and opens the shared payments
        // modal with every individual line total (original · history ·
        // make payment that now visibly REDUCES: paid · remaining live),
        // locked lines show their FULL calculation, the ✕ remove and the
        // clear button beside Aa/123 ask through a custom confirm modal,
        // the lock is a noticeable pill, the caret lands at the right
        // place after a toggle, the toggle matches Save/Next-line height,
        // and Save KEEPS the editor open with autosave taking over.
        // v1.6.41 — the caret is ALWAYS visible (canvas open / toggle /
        // Next line — view focus in num mode, token-driven), the = key is
        // gone and ⌫ took ÷'s top-right place, text-mode writing renders in
        // its own colour, the number keypad covers the BOTTOM NAV like the
        // system IME, locked-line totals sit BELOW their lines with pay
        // chips and on-canvas payment history (notes included), payments
        // are capped at the remaining total, the delete-entry button is
        // gone, the compact header carries UNDO/REDO, and the canvas is
        // RTL-aware.
        versionCode = 53
        versionName = "1.6.42"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("composeApp/release-keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric fetches android-all jars from repo1.maven.org at test
                // runtime, which rate-limits this sandbox (HTTP 429). Use Google's
                // Maven Central mirror instead.
                test.systemProperty(
                    "robolectric.dependency.repo.url",
                    "https://maven-central.storage-download.googleapis.com/maven2",
                )
                // 4 GB machine: the forked test JVM gets a fixed heap so it can
                // never eat the whole box (Robolectric NATIVE graphics is hungry).
                test.maxHeapSize = "1280m"
            }
        }
    }
}
