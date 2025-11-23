plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.shadow)
    application
}

version = System.getenv("APP_VERSION") ?: "0.1.0-dev"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://jitpack.io/")
}

dependencies {
    implementation(libs.bundles.ktoml)
    implementation(libs.bundles.kord)
    implementation(libs.bundles.lavakord)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "$group.$name.MainKt"
}

tasks.shadowJar {
    minimizeJar = true
    enableAutoRelocation = true
    relocationPrefix = "$group.$name.libs"
}
