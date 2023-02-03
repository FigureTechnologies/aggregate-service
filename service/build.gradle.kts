
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization").version("1.6.21")
    application
    java
    idea
}

group = "tech.figure.aggregate"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

application {
    mainClass.set("tech.figure.aggregator.api.ApplicationKt")
}

repositories {
    mavenCentral()
    maven( url = "https://jitpack.io")
}

val javaTarget = JavaVersion.VERSION_11
java.sourceCompatibility = javaTarget
java.targetCompatibility = javaTarget

dependencies {
    implementation(projects.common)
    implementation(projects.repository)

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

    implementation(kotlin("stdlib"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "11"
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

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "tech.figure.aggregator.api.job.MainKt"
    }
    isZip64 = true
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if(it.isDirectory) it else zipTree(it)}
    })


    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}
