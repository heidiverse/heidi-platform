// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

public class ArrayElementDisclosure extends Disclosure {
    private final String salt;
    private final Object value;

    public ArrayElementDisclosure(String salt, Object value) {
        this.salt = salt;
        this.value = value;
    }

    @Override
    public String getSalt() {
        return salt;
    }

    @Override
    public String getKey() {
        return null;
    }

    @Override
    public Object getValue() {
        return value;
    }
}
