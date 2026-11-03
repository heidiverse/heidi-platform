// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    kotlin("multiplatform") version "2.4.20"
    id("com.vanniktech.maven.publish") version "0.37.0"
    id("ch.ubique.uniffi.plugin") version "1.3.3-rc1"
}
kotlin {
    jvmToolchain(21)
    jvm()
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}
uniffi {
    bindgenFromGitTag("https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings.git", "v1.3.3-rc1")
    generateFromLibrary()
}
cargo { packageDirectory = layout.projectDirectory }
mavenPublishing {
    coordinates("org.heidiverse", "heidi-pki-x509", "0.1.0")
}
