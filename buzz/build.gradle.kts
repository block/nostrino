import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL
import java.security.SecureRandom

plugins {
  id("java")
  id("kotlin")
  `java-library`
}

kotlin {
  jvmToolchain(11)
}

dependencies {
  api(project(":lib"))
  api(libs.okHttp)
  implementation(libs.moshi)
  implementation(libs.okIo)

  testImplementation(project(":lib-test"))
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.kotestProperty)
  testRuntimeOnly(libs.slf4jSimple)
  testRuntimeOnly(libs.junitEngine)

  apply(plugin = libs.plugins.dokka.get().pluginId)
}

tasks.withType<DokkaTask>().configureEach {
  dokkaSourceSets {
    named("main") {
      moduleName.set("Nostrino Buzz Client")
      includes.from("module.md")
      sourceLink {
        localDirectory.set(file("src/main/kotlin"))
        remoteUrl.set(URL("https://github.com/cashapp/nostrino/tree/master/buzz/src/main/kotlin"))
        remoteLineSuffix.set("#L")
      }
    }
  }
}

val startBuzzRelay by tasks.creating(Exec::class) {
  commandLine("docker", "compose", "-f", "buzz-relay.compose.yml", "up", "--detach", "--wait", "--renew-anon-volumes")
  environment(
    "BUZZ_TEST_RELAY_PRIVATE_KEY",
    ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) },
  )
}

tasks.withType<Test>().configureEach {
  dependsOn(startBuzzRelay)
}

configure<JavaPluginExtension> {
  withSourcesJar()
  withJavadocJar()
}

mavenPublishing {
  val publishingExtension = extensions.getByType(PublishingExtension::class.java)

  pomFromGradleProperties()
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()

  publishingExtension.publications.create<MavenPublication>("maven") {
    from(components["java"])
  }
}
