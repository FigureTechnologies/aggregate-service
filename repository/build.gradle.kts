plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.kapt")
    idea
}

group = "tech.figure.aggregate"
version = "0.0.1-SNAPSHOT"
val javaTarget = JavaVersion.VERSION_17
java.sourceCompatibility = javaTarget
java.targetCompatibility = javaTarget

repositories {
    mavenCentral()
    maven { url = uri("https://s01.oss.sonatype.org/content/groups/staging/") }
}

dependencies {
    implementation(projects.common)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.scarlet)
    implementation(libs.jackson.module)
    implementation(libs.raven.db)
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
