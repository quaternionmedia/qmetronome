import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

/** Fallback versionName for local/debug builds, so the in-app version footer (Settings -> the
 * very bottom) shows something real instead of a hardcoded placeholder - only `alpha-release.yml`
 * builds from a tag actually pass `-PversionName`. `git describe` against the last reachable tag
 * (e.g. "v0.0.23-6-geb34487-dirty" six commits past v0.0.23, with uncommitted changes) is far more
 * useful for telling one dev build apart from another than a static "1.0" ever was.
 *
 * Uses `providers.exec` (not a raw `ProcessBuilder`) - configuration-cache-compatible, unlike
 * starting a process directly at configuration time, which Gradle 9 rejects outright. */
fun gitVersionName(): String = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--always", "--dirty")
        workingDir = rootDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifBlank { null }
}.getOrNull() ?: "dev"

/** Fallback versionCode for local/debug builds - Android requires a positive int, so `git
 * describe`'s string output can't be reused here directly. Commit count is monotonically
 * increasing and always resolvable, unlike trying to parse a tag that might not exist yet. */
fun gitCommitCount(): Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull()
}.getOrNull() ?: 1

android {
    namespace = "media.quaternion.qmetronome"
    compileSdk = 35

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        val stream = keystorePropertiesFile.inputStream()
        keystoreProperties.load(stream)
        stream.close()
    }

    defaultConfig {
        applicationId = "media.quaternion.qmetronome"
        minSdk = 33
        targetSdk = 35
        // When building a release from CI the tag-derived values are injected via -PversionCode
        // and -PversionName; local and debug builds fall back to git-derived values (see
        // gitVersionName()/gitCommitCount() above) so the in-app version footer is still
        // meaningful instead of a static placeholder.
        versionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: gitCommitCount()
        versionName = project.findProperty("versionName")?.toString() ?: gitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Custom keep rules live in src/main/keepRules/*.keep (AGP auto-discovers and merges
            // these regardless of proguardFiles()) - that's where the Glyph SDK and Glance
            // ActionCallback/GlanceAppWidget rules already are, so there's no separate
            // proguard-rules.pro to maintain in parallel.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// The Glyph Matrix SDK is Nothing Technology Limited's closed binary, licensed under the Glyph SDK
// End User License Agreement in the vendor's Developer Kit. It is not kept in this repository: the
// build takes it from the Developer Kit at a pinned commit, into the ignored `app/libs/`, and
// refuses any file whose SHA-256 is not the one written here. A checkout with the file already in
// place builds without the network.
val glyphSdkFile = layout.projectDirectory.file("libs/glyph-matrix-sdk-2.0.aar").asFile
val glyphSdkUrl = "https://raw.githubusercontent.com/Nothing-Developer-Programme/" +
    "GlyphMatrix-Developer-Kit/999b1143a6bff0dd79db11d21b92c15c56a346b1/glyph-matrix-sdk-2.0.aar"
val glyphSdkSha256 = "be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29"

fun sha256Of(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

if (!glyphSdkFile.exists()) {
    check(!gradle.startParameter.isOffline) {
        "${glyphSdkFile.name} is absent and Gradle is offline. Run one build online, or put the " +
            "file from $glyphSdkUrl at $glyphSdkFile."
    }
    glyphSdkFile.parentFile.mkdirs()
    val partial = File(glyphSdkFile.path + ".part")
    val connection = URI(glyphSdkUrl).toURL().openConnection().apply {
        connectTimeout = 30_000
        readTimeout = 60_000
    }
    connection.getInputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
    val fetched = sha256Of(partial)
    if (fetched != glyphSdkSha256) {
        partial.delete()
        error("The Glyph Matrix SDK fetched from $glyphSdkUrl has SHA-256 $fetched, not $glyphSdkSha256.")
    }
    check(partial.renameTo(glyphSdkFile)) { "Could not move ${partial.name} to ${glyphSdkFile.name}." }
}
check(sha256Of(glyphSdkFile) == glyphSdkSha256) {
    "$glyphSdkFile has SHA-256 ${sha256Of(glyphSdkFile)}, not $glyphSdkSha256. Delete it and build again."
}

dependencies {
    implementation(files(glyphSdkFile))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.glance.appwidget)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

/** Screenshots captured by [io.github.takahirom.roborazzi] (see `tutorial/` package + `*ScreenshotTest`
 * files under `app/src/test/`) are the illustrations for `docs/user-guide/` - written straight
 * into a tracked repo location (not the gitignored `build/` default) so they render on GitHub
 * without a build step, and stay in sync automatically the moment the tests that produce them are
 * re-run (`./gradlew testDebugUnitTest -Proborazzi.test.record=true`). */
roborazzi {
    outputDir.set(file("../docs/images/generated/screenshots"))
}

/** Per-test start/pass/fail/skip events, streamed live instead of only summarized at the end -
 * without this, a slow or stalled individual test (Robolectric/Compose tests can occasionally
 * hang on a `waitForIdle()` that never settles) is invisible until the whole task times out or
 * finishes, with no way to tell which test was running. Applies to every `Test` task, including
 * `generateUserGuide` below (it reuses `testDebugUnitTest`'s own task type). */
tasks.withType<Test>().configureEach {
    testLogging {
        events("started", "passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
    /** A hard, project-wide ceiling on the whole task's wall-clock time. Not a per-test bound -
     * Gradle kills the whole test JVM, not just the offending method - but it's the mechanism
     * that actually matters: without SOME enforced upper bound, "is this test hung or just slow"
     * is undecidable from outside the process (the halting problem, for real), and the only
     * fallback is babysitting a run indefinitely and re-checking it, which never converges on an
     * answer either. 8 minutes is roughly 3x the suite's healthy full run (~2.5 min as of
     * 2026-07), so a trip here is a real signal. Combined with the `testLogging` above, the last
     * STARTED line before the kill identifies which test to investigate - required by
     * `CONTRIBUTING.md`'s testing section and governance/qm's cross-project testing-standards ADR;
     * do not remove without updating both. */
    timeout.set(Duration.ofMinutes(8))
}

/** Regenerates `docs/user-guide/` (a `README.md` index plus one page per topic) from
 * `TutorialTopics.all` - the one step of "tests spawn the rest" that isn't itself a Compose UI
 * test (see `GenerateUserGuideTest.kt`'s own kdoc for why it's a `Test` task rather than a plain
 * one: it reuses `testDebugUnitTest`'s own classpath wholesale rather than hand-assembling a
 * second one, and `dependsOn` it so every topic's screenshot is freshly (re)captured - by the
 * *whole* suite, not just this one filtered class - before the doc embedding them gets written. */
tasks.register<Test>("generateUserGuide") {
    group = "documentation"
    description = "Regenerates docs/user-guide/ from TutorialTopics.all + the screenshots testDebugUnitTest just captured."
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    dependsOn(unitTest)
    testClassesDirs = unitTest.get().testClassesDirs
    classpath = unitTest.get().classpath
    filter {
        includeTestsMatching("media.quaternion.qmetronome.tools.GenerateUserGuideTest")
    }
}
