buildscript {
    val kotlin_version: String by extra("2.2.20")
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.3.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlin_version")
    }
}

plugins {
    id("com.android.application") version "9.3.2" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
