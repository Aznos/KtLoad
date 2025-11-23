plugins {
    application
    kotlin("jvm") version "2.2.20"
}

group = "com.maddoxh"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")

    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
}

application {
    mainClass = "com.maddoxh.MainKt"
}

tasks.test {
    useJUnitPlatform()
}