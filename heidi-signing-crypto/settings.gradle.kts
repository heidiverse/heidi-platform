// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

rootProject.name = "heidi-signing-crypto"

include(":heidi-expression")

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}

	dependencyResolutionManagement {
		repositories {
			mavenCentral()
		}
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
