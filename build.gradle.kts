import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
  alias(libs.plugins.mavenPublishGradlePlugin) apply false
  alias(libs.plugins.versionsGradlePlugin)
  alias(libs.plugins.versionCatalogUpdateGradlePlugin)
  alias(libs.plugins.dokka)
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

buildscript {
  repositories {
    mavenCentral()
  }
}

subprojects {
  buildscript {
    repositories {
      mavenCentral()
      gradlePluginPortal()
    }
  }

  repositories {
    mavenCentral()
  }

  apply(plugin = rootProject.project.libs.plugins.kotlinBinaryCompatibilityPlugin.get().pluginId)
  apply(plugin = rootProject.project.libs.plugins.mavenPublishGradlePlugin.get().pluginId)


  apply(plugin = "version-catalog")

  // Only apply if the project has the kotlin plugin added:
  plugins.withType<KotlinPluginWrapper> {
    val compileKotlin by tasks.getting(KotlinCompile::class) {
      kotlinOptions {
        jvmTarget = "11"
        allWarningsAsErrors = true
      }
    }
    val compileTestKotlin by tasks.getting(KotlinCompile::class) {
      kotlinOptions {
        jvmTarget = "11"
        allWarningsAsErrors = true
      }
    }

    dependencies {
      add("testImplementation", project.rootProject.libs.junitApi)
      add("testRuntimeOnly", project.rootProject.libs.junitEngine)
    }

    tasks.withType<GenerateModuleMetadata> {
      suppressedValidationErrors.add("enforced-platform")
    }
  }

  tasks.withType<Test> {
    dependsOn("apiCheck")
    useJUnitPlatform()
    testLogging {
      events("started", "passed", "skipped", "failed")
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
    }
  }

  apply(plugin = "com.github.ben-manes.versions")

  tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    revision = "release"
    resolutionStrategy {
      componentSelection {
        all {
          if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
            reject("Release candidate")
          }
        }
      }
    }
  }

}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isStable = stableKeyword || regex.matches(version)
  return isStable.not()
}

// this needs to be defined here for the versionCatalogUpdate
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
  revision = "release"
  resolutionStrategy {
    componentSelection {
      all {
        if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
          reject("Release candidate")
        }
      }
    }
  }
}

versionCatalogUpdate {
  /**
   * Use @pin and @keep in gradle/lib.versions.toml instead of defining here
   */
  sortByKey.set(true)
}
