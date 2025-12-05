rootProject.name = "nostrino"

plugins {
  id("com.gradle.develocity") version "3.18.2"
}

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/terms-of-service"
    termsOfUseAgree = "yes"
  }
}

include(":lib")
include(":lib-test")
