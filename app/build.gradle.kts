import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyStore
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.cert.X509Certificate
import java.util.Properties
import javax.naming.ldap.LdapName

val legacyAndroid = providers.gradleProperty("legacyAndroid").isPresent

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
// Only the file path is passed through the environment; credentials stay outside Git.
val signingFilePath = providers.environmentVariable("CLOCK_RELEASE_SIGNING_FILE").orNull

fun protectedExternalFile(path: String): File {
    val supplied = File(path)
    require(supplied.isAbsolute) { "Release signing paths must be absolute." }
    val file = supplied.canonicalFile
    require(file.isFile) { "Release signing file is missing." }
    require(!file.toPath().startsWith(rootDir.canonicalFile.toPath())) {
        "Release signing files must be outside the project."
    }
    var parent: File? = file.parentFile
    while (parent != null) {
        val gitMarker = File(parent, ".git")
        val gitRepository = gitMarker.isFile || File(gitMarker, "HEAD").isFile ||
            (File(parent, "HEAD").isFile && File(parent, "objects").isDirectory)
        require(!gitRepository) { "Release signing files must be outside Git repositories." }
        parent = parent.parentFile
    }
    val forbidden = setOf(
        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE
    )
    require(Files.getPosixFilePermissions(file.toPath()).none { it in forbidden } &&
        Files.getPosixFilePermissions(file.parentFile.toPath()).none { it in forbidden }) {
        "Release signing files need mode 600 and their directory mode 700 (POSIX)."
    }
    return file
}

val releaseCredentials = Properties()
if (signingFilePath != null) {
    require(!gradle.startParameter.isConfigurationCacheRequested) {
        "Disable the configuration cache when supplying release credentials."
    }
    protectedExternalFile(signingFilePath).inputStream().use { releaseCredentials.load(it) }
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword", "certificateSha256").forEach {
        require(!releaseCredentials.getProperty(it).isNullOrBlank()) { "A required release signing property is missing." }
    }
}
val releaseStoreFile = if (signingFilePath != null)
    protectedExternalFile(releaseCredentials.getProperty("storeFile")) else null

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Reject missing, unexpected or debug release signing credentials."
    notCompatibleWithConfigurationCache("Release credentials must never be serialized into the configuration cache.")
    doLast {
        check(releaseStoreFile != null) {
            "Release signing is not configured. Set CLOCK_RELEASE_SIGNING_FILE to an external protected properties file. Debug fallback is forbidden."
        }
        // Recheck permissions at execution time. Never print properties or exception details.
        protectedExternalFile(signingFilePath!!)
        protectedExternalFile(releaseStoreFile.absolutePath)
        val storePass = releaseCredentials.getProperty("storePassword").toCharArray()
        val keyPass = releaseCredentials.getProperty("keyPassword").toCharArray()
        try {
            val store = KeyStore.getInstance("PKCS12")
            releaseStoreFile.inputStream().use { store.load(it, storePass) }
            val alias = releaseCredentials.getProperty("keyAlias")
            check(store.isKeyEntry(alias))
            check(store.getKey(alias, keyPass) is java.security.PrivateKey)
            val cert = store.getCertificate(alias) as X509Certificate
            cert.checkValidity()
            val dn = LdapName(cert.subjectX500Principal.name).rdns
            check(dn.count { it.type.equals("CN", true) } == 1 &&
                dn.single { it.type.equals("CN", true) }.value.toString() == "OKBIT")
            check(dn.count { it.type.equals("O", true) } == 1 &&
                dn.single { it.type.equals("O", true) }.value.toString() == "OKBIT")
            check((cert.publicKey as? RSAPublicKey)?.modulus?.bitLength() == 3072)
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(fingerprint != "9479090df679ca2828de1ff9b11547603e87d14d3496dc8a9287902e75310e94")
            val expected = releaseCredentials.getProperty("certificateSha256").replace(":", "").lowercase()
            check(expected.matches(Regex("[0-9a-f]{64}")) && fingerprint == expected)
        } catch (_: Exception) {
            throw GradleException("Release signing rejected: check PKCS12 credentials, CN/O=OKBIT, RSA 3072, certificate validity and pinned SHA-256. Debug keys are forbidden.")
        } finally {
            storePass.fill('\u0000')
            keyPass.fill('\u0000')
        }
    }
}
// Guard both public entry points and the tasks that actually produce signed packages.
tasks.configureEach {
    if (name.matches(Regex("^(validateSigning|package|sign|bundle|assemble)(Legacy|Modern)Release(Bundle)?$"))) {
        dependsOn(validateReleaseSigning)
    }
}

android {
    namespace = "ru.okbit.ledclock"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "ru.okbit.ledclock"
        minSdk = 14
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.3"
    }
    flavorDimensions += "compatibility"
    productFlavors {
        create("legacy") {
            dimension = "compatibility"
            minSdk = 14
        }
        create("modern") {
            dimension = "compatibility"
            minSdk = 23
        }
    }
    signingConfigs {
        create("release") {
            storeType = "PKCS12"
            storeFile = releaseStoreFile
            storePassword = releaseCredentials.getProperty("storePassword")
            keyAlias = releaseCredentials.getProperty("keyAlias")
            keyPassword = releaseCredentials.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            // No debug fallback, including when release credentials are absent.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            if (legacyAndroid) it.filter.includeTestsMatching("ru.okbit.ledclock.ClockLegacyIntegrationTest")
            else it.filter.excludeTestsMatching("ru.okbit.ledclock.ClockLegacyIntegrationTest")
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            val testHome = layout.buildDirectory.dir("test-home").get().asFile
            it.systemProperty("user.home", testHome.absolutePath)
            providers.gradleProperty("artifactDir").orNull?.let { dir -> it.systemProperty("clock.artifactDir", dir) }
            it.doFirst { testHome.mkdirs() }
        }
    }
    bundle { language { enableSplit = false } }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:${if (legacyAndroid) "4.11.1" else "4.14.1"}")
}
