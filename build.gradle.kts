plugins {
    java
}

group = "lat.funkypunky"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")   // Paper API
    maven("https://repo.extendedclip.com/releases/")            // PlaceholderAPI
    maven("https://repo.glaremasters.me/repository/towny/")     // Towny
    maven("https://jitpack.io")                                 // Vault API
}

dependencies {
    // compileOnly = el servidor ya trae estas librerías; no se meten dentro del .jar
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("me.clip:placeholderapi:2.11.6") { isTransitive = false }
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.0.5") { isTransitive = false }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25) // Paper 26.x exige Java 25
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
