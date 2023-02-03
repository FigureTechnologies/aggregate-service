plugins {
    java
    id("org.flywaydb.flyway") version "9.8.1"
}

dependencies {
    implementation(libs.postgres)
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

flyway {
    url = "jdbc:postgresql://localhost:5432/postgresdb"
    driver = "org.postgresql.Driver"
    user = "postgres"
    password = "password1"
    schemas = mutableListOf("public").toTypedArray()
    locations = mutableListOf("filesystem:sql").toTypedArray()
}
