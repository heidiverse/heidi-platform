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
}


uniffi {
	bindgenFromGitTag(
		"https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings.git",
		"v1.2.1"
	)
	generateFromLibrary()
}

cargo {
	packageDirectory = layout.projectDirectory
}

mavenPublishing {
	coordinates(
		artifactId = "heidi-expression",
		version = property("EXPRESSION_ARTIFACT_VERSION").toString()
	)
	publishToMavenCentral(true)
}
