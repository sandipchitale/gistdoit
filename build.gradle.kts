plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "sandipchitale"
version = "1.0.21"

dependencies {
    implementation("org.kohsuke:github-api:1.330")

    intellijPlatform {
        intellijIdeaUltimate("2026.1") {
            useInstaller = false
            useCache = true
        }

        // Optional dependency - used to obtain the GitHub token from the IDE's own
        // GitHub accounts (Settings | Version Control | GitHub) instead of an env var.
        bundledPlugin("org.jetbrains.plugins.github")

        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    patchPluginXml {
        sinceBuild.set("261")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(providers.gradleProperty("intellijPublishToken"))
    }
}
