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

    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")
}

application {
    mainClass = "com.maddoxh.MainKt"
}

tasks.test {
    useJUnitPlatform()
}