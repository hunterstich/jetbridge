import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

group = "com.hunterstich.ideavim.jetbridge"
version = "0.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.10")

    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        pluginVerifier()

        // TODO: Remove. Not actually dependent on ideavim but is handy for testing
        plugins("IdeaVIM:2.24.0")
    }
}

val publishToken: String by project

intellijPlatform {

    publishing {
        token.set(publishToken)
    }
    pluginConfiguration {
        ideaVersion {
            // Let the Gradle plugin set the since-build version. It defaults to the version of the IDE we're building against
            // specified as two components, `{branch}.{build}` (e.g., "241.15989"). There is no third component specified.
            // The until-build version defaults to `{branch}.*`, but we want to support _all_ future versions, so we set it
            // with a null provider (the provider is important).
            // By letting the Gradle plugin handle this, the Plugin DevKit IntelliJ plugin cannot help us with the "Usage of
            // IntelliJ API not available in older IDEs" inspection. However, since our since-build is the version we compile
            // against, we can never get an API that's newer - it would be an unresolved symbol.
            untilBuild.set(provider { null })
        }
    }

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceSets.all {
        languageSettings.apply {
            languageVersion = "2.0"
        }
    }
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    compileTestKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    wrapper {
        gradleVersion = gradleVersion
    }
}