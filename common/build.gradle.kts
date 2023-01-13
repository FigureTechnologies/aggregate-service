plugins {
    kotlin("jvm")
    kotlin("kapt")
    idea
}

group = "tech.figure.aggregate"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.kotlin)
    testImplementation(libs.kotlin.testcoroutines)
    testImplementation(libs.bundles.junit)

    implementation(libs.bundles.apache.commons)
    implementation(libs.bundles.scarlet)
    implementation(libs.datadog)
    implementation(libs.grpc.protobuf)
    implementation(libs.provenance.protos)
    implementation(libs.bundles.logback)
    implementation(libs.moshi.kotlin.codegen)
    implementation(libs.bundles.hoplite)
    implementation(libs.json)
    implementation(platform(libs.aws.bom))
    implementation(libs.bundles.aws)
    implementation(libs.localstack)
    implementation(libs.bundles.hdwallet)

    implementation(libs.commons.dbutils)
    implementation(libs.apache.commons)

    implementation("tech.figure.block:api-proto:0.1.10")

    implementation("org.postgresql:postgresql:42.5.1")

    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
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

kapt {
    correctErrorTypes = true
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "11"
    }
}

tasks.compileTestKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "11"
    }
}

tasks.test {
    useJUnitPlatform()
}
