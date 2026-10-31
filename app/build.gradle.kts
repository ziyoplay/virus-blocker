import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "uz.apkqoriqchi"
    compileSdk = 36

    defaultConfig {
        applicationId = "uz.apkqoriqchi"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.5"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "style"
    productFlavors {
        // Siz va do'stlaringiz uchun: hacker uslubidagi terminal
        create("hacker") {
            dimension = "style"
        }
        // Oila uchun: oddiy, sokin interfeys
        create("family") {
            dimension = "style"
            applicationIdSuffix = ".family"
            versionNameSuffix = "-family"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystoreProps.isNotEmpty()) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }

    // Unit testlar oddiy JVM'da: Android klasslari kerak bo'lmagan mantiq (sayt qoidalari, baza) tekshiriladi.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Tayyor APK'larni tushunarli nomlar bilan dist/ papkasiga ko'chirish: ./gradlew dist
tasks.register<Copy>("dist") {
    dependsOn("assembleHackerRelease", "assembleFamilyRelease")
    from(layout.buildDirectory.file("outputs/apk/hacker/release/app-hacker-release.apk")) {
        rename { "APK_Qoriqchi_hacker.apk" }
    }
    from(layout.buildDirectory.file("outputs/apk/family/release/app-family-release.apk")) {
        rename { "Telefon_Qoriqchi_oila.apk" }
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
}
