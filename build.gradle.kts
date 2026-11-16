buildscript {
    val kotlin_version: String by extra("2.2.20")
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.5.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlin_version")
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
}

tasks.register<Delete>("clean") {
    description = "Clean building directory"
    delete(rootProject.layout.buildDirectory)
}
