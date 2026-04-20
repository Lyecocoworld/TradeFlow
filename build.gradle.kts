plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "com.github.lye"
version = "0.1"

// ── Version constants ──────────────────────────────────────
val lombokVersion    = "1.18.30"
val adventureVersion = "4.15.0"
val foliaVersion     = "1.21.8-R0.1-SNAPSHOT"
val bstatsVersion    = "3.0.2"
val mapdbVersion     = "3.1.0"
val vaultVersion     = "1.7.1"
val jettyVersion     = "11.0.19"
val servletVersion   = "5.0.0"
val caffeineVersion  = "3.1.8"

// ── Java toolchain ─────────────────────────────────────────
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    modularity.inferModulePath.set(false)
}

// ── Repositories ───────────────────────────────────────────
repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.tcoded.com/releases") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.groupez.dev/releases") }
    maven { url = uri("https://repo.groupez.dev/releases") }
}

// ── Custom shade configuration (extends implementation) ────
val shade by configurations.creating {
    extendsFrom(configurations.implementation.get())
}

configurations.all {
    resolutionStrategy {
        // intentional empty — placeholder for future rules
    }
    // Resolve capability conflict: folia-api and paper-api both provide spigot-api
    resolutionStrategy.capabilitiesResolution.withCapability("org.spigotmc:spigot-api") {
        val folia = candidates.find { it.id.toString().contains("folia-api") }
        if (folia != null) select(folia)
    }
}

// ── Dependencies ───────────────────────────────────────────
dependencies {
    // --- APIs serveur (NE PAS shade) ---
    paperweight.paperDevBundle(foliaVersion)
    compileOnly("net.kyori:adventure-text-minimessage:$adventureVersion")
    compileOnly("net.kyori:adventure-api:$adventureVersion")
    compileOnly("com.github.MilkBowl:VaultAPI:$vaultVersion")
    compileOnly("fr.maxlego08.menu:zmenu-api:1.1.1.2")

    // Jetty + Jakarta (servlet/filter)
    compileOnly("org.eclipse.jetty:jetty-server:$jettyVersion")
    compileOnly("org.eclipse.jetty:jetty-servlet:$jettyVersion")
    compileOnly("jakarta.servlet:jakarta.servlet-api:$servletVersion")

    // Lombok
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    // --- zMenu API (compile-only, plugin côté serveur) ---
    compileOnly("fr.maxlego08.menu:zmenu-api:1.1.1.2")

    // --- libs utilisées ---
    implementation("dev.triumphteam:triumph-gui:3.1.2")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.mapdb:mapdb:$mapdbVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.15.2")
    implementation("org.eclipse.collections:eclipse-collections-api:11.1.0")
    implementation("org.eclipse.collections:eclipse-collections:11.1.0")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.bstats:bstats-bukkit:$bstatsVersion")
    implementation("org.redisson:redisson:3.24.3")
    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    // --- contenu du fatjar ---
    shade("dev.triumphteam:triumph-gui:3.1.2")
    shade("com.zaxxer:HikariCP:6.2.1")
    shade("org.mapdb:mapdb:$mapdbVersion")
    shade("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    shade("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    shade("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    shade("com.fasterxml.jackson.module:jackson-module-parameter-names:2.15.2")
    shade("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.15.2")
    shade("org.eclipse.collections:eclipse-collections-api:11.1.0")
    shade("org.eclipse.collections:eclipse-collections:11.1.0")
    shade("mysql:mysql-connector-java:8.0.33")
    shade("org.bstats:bstats-bukkit:$bstatsVersion")
    shade("org.redisson:redisson:3.24.3")
    shade("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    // --- Testing ---
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
    testImplementation("dev.folia:folia-api:$foliaVersion")
    testImplementation("net.kyori:adventure-text-minimessage:$adventureVersion")
    testImplementation("net.kyori:adventure-api:$adventureVersion")
    testImplementation("com.github.MilkBowl:VaultAPI:$vaultVersion")
}

// ── Tasks ──────────────────────────────────────────────────
tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    enabled = true
}

tasks.shadowJar {
    dependsOn(tasks.compileJava)
    archiveClassifier.set("")
    configurations = listOf(shade)

    relocate("com.tcoded.folialib", "com.github.lye.tradeflow.libs.folialib")
    relocate("org.bstats", "com.github.lye.tradeflow.libs.bstats")
    relocate("com.github.benmanes.caffeine", "com.github.lye.tradeflow.libs.caffeine")
    relocate("org.mapdb", "com.github.lye.tradeflow.libs.mapdb")
    relocate("com.zaxxer.hikari", "com.github.lye.tradeflow.libs.hikari")
    relocate("com.fasterxml.jackson", "com.github.lye.tradeflow.libs.jackson")
    relocate("org.eclipse.collections", "com.github.lye.tradeflow.libs.eclipse")
    relocate("com.mysql", "com.github.lye.tradeflow.libs.mysql")
    relocate("dev.triumphteam.gui", "com.github.lye.tradeflow.libs.triumphgui")
    relocate("org.redisson", "com.github.lye.tradeflow.libs.redisson")

    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
