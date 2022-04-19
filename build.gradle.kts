plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("org.openapi.generator") version "5.2.1"
    application
    idea
}

group = "io.provenance.tech.aggregate"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

val TENDERMINT_OPENAPI_YAML = "$rootDir/src/main/resources/tendermint-v0.34.12-rpc-openapi-FIXED.yaml"

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://s01.oss.sonatype.org/content/groups/staging/")
}

dependencies {
    implementation(projects.common)
    implementation(projects.l2Cache)

    implementation(libs.bundles.eventstream)
    implementation(libs.bundles.kotlin)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.kotlin.testcoroutines)
    implementation(libs.bundles.apache.commons)
    implementation(libs.bundles.scarlet)
    implementation(libs.datadog)
    implementation(libs.provenance.protos)
    implementation(libs.bundles.logback)
    implementation(libs.moshi.kotlin.codegen)
    kapt(libs.moshi.kotlin.codegen)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    implementation(libs.json)
    implementation(platform(libs.aws.bom))
    implementation(libs.bundles.aws)
    implementation(libs.localstack)
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

application {
    mainClass.set("io.provenance.aggregate.service.MainKt")
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

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "io.provenance.aggregate.service.MainKt"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })


    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}
