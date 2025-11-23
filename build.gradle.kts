plugins {
    kotlin("jvm") version "2.2.20"
}

group = "com.maddoxh"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}