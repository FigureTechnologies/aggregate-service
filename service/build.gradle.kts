
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization").version("1.8.21")
    application
    java
    idea
}

group = "tech.figure.aggregate"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

application {
    mainClass.set("tech.figure.aggregator.api.ApplicationKt")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven( url = "https://jitpack.io")
}

val javaTarget = JavaVersion.VERSION_17
java.sourceCompatibility = javaTarget
java.targetCompatibility = javaTarget

dependencies {
    implementation(projects.common)
    implementation(projects.repository)
    implementation(projects.proto)

    implementation(libs.commons.dbutils)
    implementation(libs.raven.db)
    implementation(libs.okhttp)
    implementation(libs.gson)

    implementation(libs.ktor.core)
    implementation(libs.ktor.netty)
    implementation(libs.ktor.jackson)
    implementation(libs.ktor.swagger)

    implementation(libs.logback.classic)

    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)

    implementation(libs.bundles.grpc)
    implementation(libs.bundles.protobuf)
    implementation(libs.bundles.kotlin.coroutines)
    implementation(libs.grpc.kotlin.stub)

    implementation(kotlin("stdlib"))
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "17"
    }
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

//tasks.withType<Jar> {
//    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//    manifest {
//        attributes["Main-Class"] = "tech.figure.aggregator.api.job.MainKt"
//    }
//    isZip64 = true
//    from(sourceSets.main.get().output)
//    dependsOn(configurations.runtimeClasspath)
//    from({
//        configurations.runtimeClasspath.get().map { if(it.isDirectory) it else zipTree(it)}
//    })
//
//
//    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
//}
