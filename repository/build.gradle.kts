plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.kapt")
    idea
}

val javaTarget = JavaVersion.VERSION_17
java.sourceCompatibility = javaTarget
java.targetCompatibility = javaTarget

dependencies {
    implementation(projects.common)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.scarlet)
    implementation(libs.jackson.module)
    implementation(libs.bundles.logback)
    implementation(libs.bundles.eventstream)
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "17"
    }
}

tasks.compileTestKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "17"
    }
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        java {
            srcDirs(
                "$projectDir/src/main/kotlin",
                "$buildDir/generated/src/main/kotlin"
            )
        }
    }
    test {
        java {
            srcDir("$projectDir/src/test/kotlin")
        }
    }
}
