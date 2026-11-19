import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.efa"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.efa"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "app.efa.EfaTestRunner"
    }
    // The release build is what LA4 speed is judged on. It is signed with the debug key here only so it can be installed for testing; real signing is the owner's.
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); signingConfig = signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.exifinterface:exifinterface:1.4.1")
    androidTestImplementation(kotlin("test"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")   // older Espresso fails on Android 16 (InputManager.getInstance)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.12.0")   // Room 2.8 schema bundles need it; an older pinned runtime broke the migration test
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(kotlin("test"))
}

// CT3: an update installs over the old app only if it is signed with the same key. This records the fingerprint of the debug key the first time and refuses a build signed with a different one,
// so a build on another machine cannot quietly force an uninstall and the loss of your data. Copy ~/.android/debug.keystore to the other machine to keep the key.
val verifySigning = tasks.register("verifySigning") {
    doLast {
        val ks = file(System.getProperty("user.home") + "/.android/debug.keystore")
        if (!ks.exists()) { logger.warn("verifySigning: there is no debug keystore yet, so there is nothing to compare."); return@doLast }
        var store: KeyStore? = null
        for (type in listOf("PKCS12", "JKS")) { try { val k = KeyStore.getInstance(type); ks.inputStream().use { k.load(it, "android".toCharArray()) }; store = k; break } catch (e: Exception) { } }
        val cert = store?.getCertificate("androiddebugkey") ?: run { logger.warn("verifySigning: the debug key could not be read."); return@doLast }
        val fp = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }
        val rec = file("signing-fingerprint.txt")
        if (!rec.exists()) { rec.writeText(fp + "\n"); logger.lifecycle("verifySigning: recorded the signing key $fp") }
        else if (rec.readText().trim() != fp) throw GradleException("The signing key changed (was ${rec.readText().trim()}, now $fp). An update would not install over the old Spark and your data would be lost. Copy the old debug.keystore back, or delete app/signing-fingerprint.txt if you mean to start a new key.")
    }
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(verifySigning) }
