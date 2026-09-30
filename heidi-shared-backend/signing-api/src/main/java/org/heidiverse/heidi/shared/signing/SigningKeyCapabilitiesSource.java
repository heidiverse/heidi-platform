// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/** A provider whose capabilities are discovered dynamically rather than from its Java interfaces. */
public interface SigningKeyCapabilitiesSource {
    SigningKeyCapabilities capabilities();
}
