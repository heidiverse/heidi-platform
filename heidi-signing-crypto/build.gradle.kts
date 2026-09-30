// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.vanniktech.publish)
	alias(libs.plugins.uniffi.plugin)
}

kotlin {
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}

	jvmToolchain(21)
	jvm()

	sourceSets {

		commonTest.dependencies {
			implementation(libs.kotlin.test)
		}

	}
}

uniffi {
	bindgenFromGitTag(
		"https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings.git",
		libs.versions.uniffi.bindgen.get()
	)
	generateFromLibrary()
}

cargo {
	packageDirectory = layout.projectDirectory.dir("heidi-signing")
}

mavenPublishing {
	coordinates(artifactId= property("ARTIFACT_ID").toString(), version= property("ARTIFACT_VERSION").toString())
	publishToMavenCentral(true)
}
