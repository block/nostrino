import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.ServerSocket
import java.net.URL

plugins {
  id("java")
  id("kotlin")
  `java-library`
  id("com.bmuschko.docker-remote-api") version "9.3.0"
}

kotlin {
  jvmToolchain(11)
}

dependencies {
  implementation(project(":lib"))
  implementation(libs.kotestProperty)

  // ByteStrings
  implementation(libs.okIo)

  // JSON
  implementation(libs.moshi)

  // Curves
  implementation(libs.acinqSecp256k1JniJvm)
  implementation(libs.acinqSecp256k1Jvm)

  // Basic test libraries:

  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.kotestProperty)
  testImplementation(libs.turbine)
  testRuntimeOnly(libs.slf4jSimple)
  testRuntimeOnly(libs.junitEngine)

  apply(plugin = libs.plugins.dokka.get().pluginId)
}

tasks.withType<DokkaTask>().configureEach {
  dokkaSourceSets {
    named("main") {
      moduleName.set("Nostrino Nostr SDK Testing Lib")

      // Includes custom documentation
      includes.from("module.md")

      // Points source links to GitHub
      sourceLink {
        localDirectory.set(file("src/main/kotlin"))
        remoteUrl.set(URL("https://github.com/cashapp/nostrino/tree/master/lib-test/src/main/kotlin"))
        remoteLineSuffix.set("#L")
      }
    }
  }
}

val createDockerfile by tasks.creating(Dockerfile::class) {
  from("scsibug/nostr-rs-relay:latest")
}

val buildImage by tasks.creating(DockerBuildImage::class) {
  dependsOn(createDockerfile)
  images.add("scsibug/nostr-rs-relay:latest")
}

val createContainer by tasks.creating(DockerCreateContainer::class) {
  onlyIf { !relayIsRunning() }
  dependsOn(buildImage)
  targetImageId(buildImage.imageId)
  containerName.set("nostr-relay")
  hostConfig.portBindings.set(listOf("7707:8080"))
  hostConfig.autoRemove.set(true)
}

val startContainer by tasks.creating(DockerStartContainer::class) {
  onlyIf { !relayIsRunning() }
  dependsOn(createContainer)
  targetContainerId(createContainer.containerId)
}

tasks.withType<Test>().configureEach {
  dependsOn(startContainer)
}

fun relayIsRunning() =
  try {
    val s = ServerSocket(7707)
    s.close()
    false
  } catch (_: java.io.IOException) {
    true
  }

// Publishing

configure<JavaPluginExtension> {
  withSourcesJar()
  withJavadocJar()
}

mavenPublishing {
  val publishingExtension = extensions.getByType(PublishingExtension::class.java)

  pomFromGradleProperties()
  publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.DEFAULT, true)
  signAllPublications()

  publishingExtension.publications.create<MavenPublication>("maven") {
    from(components["java"])
  }
}
