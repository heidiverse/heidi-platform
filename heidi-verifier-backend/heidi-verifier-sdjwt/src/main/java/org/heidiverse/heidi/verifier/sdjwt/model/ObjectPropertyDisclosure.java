// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

public class ObjectPropertyDisclosure extends Disclosure {
    private final String salt;
    private final String key;
    private final Object value;

    public ObjectPropertyDisclosure(String salt, String key, Object value) {
        this.salt = salt;
        this.key = key;
        this.value = value;
    }

    @Override
    public String getSalt() {
        return salt;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Object getValue() {
        return value;
    }
}
