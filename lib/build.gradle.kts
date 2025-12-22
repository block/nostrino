import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotestMultiplatform)
}

kotlin {
  jvmToolchain(11)

  jvm()
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  linuxX64()

  targets.all {
    compilations.all {
      kotlinOptions {
        freeCompilerArgs += listOf("-Xexpect-actual-classes")
      }
    }
  }

  sourceSets {

    commonMain {
      dependencies {
        implementation(libs.acinqSecp256k1)
        implementation(libs.kotlinxCoroutines)
        implementation(libs.okIo)
        implementation(libs.secureRandom)
      }
    }

    jvmMain {
      dependencies {
        api(libs.okHttp)
        implementation(libs.acinqSecp256k1Jvm)
        implementation(libs.acinqSecp256k1JniJvm)
        implementation(libs.moshi)
        implementation(libs.kotlinLoggingJvm)
        implementation(libs.guava)
        apply(plugin = libs.plugins.dokka.get().pluginId)
      }
    }
  }
}

// Publishing
mavenPublishing {
  configure(
    KotlinMultiplatform(
      javadocJar = JavadocJar.Dokka("dokkaHtml"),
    )
  )
  pomFromGradleProperties()
  publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.DEFAULT, true)
  signAllPublications()
}
