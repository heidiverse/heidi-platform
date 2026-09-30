// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.util.Map;

/** Durable boundary for the signing service's accepted client keys. */
public interface SigningClientStore {
    Map<String, byte[]> findAll();

    void put(String client, byte[] publicKey);

    void remove(String client);
}
