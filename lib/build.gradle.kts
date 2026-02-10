plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(libs.bundles.kotlin)
    testImplementation(libs.bundles.test)
}


kotlin { jvmToolchain(21) }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/venture-falcon/capsule")
            credentials {
                val user = properties["gpr.user"] ?: System.getenv("GPR_USER")
                val token = properties["gpr.token"] ?: System.getenv("GPR_TOKEN")
                username = requireNotNull(user.toString()) { "Found no username for GitHub packages access" }
                password = requireNotNull(token.toString()) { "Found no token for GitHub packages access" }
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            groupId = "io.nexure"
            artifactId = "capsule"
            version = project.findProperty("versionId")?.toString() ?: "0.0.0-SNAPSHOT"
            from(components["java"])
        }
    }
}


