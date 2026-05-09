import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

dependencies {
    // --- HTTP client for LLM API calls ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- JSON serialization for API request/response ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // --- IntelliJ Platform ---
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }

        description = """
            <h2>RepoLens AI</h2>
            <p>Understand code in context. Right-click any code selection to get AI-powered explanations 
            enriched with repository context — including dependency impact, side effects, and related components.</p>
            <ul>
                <li>Repository-aware code explanations</li>
                <li>Dependency impact analysis</li>
                <li>Side effect detection</li>
                <li>Related component discovery</li>
            </ul>
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "8.13"
    }
}
