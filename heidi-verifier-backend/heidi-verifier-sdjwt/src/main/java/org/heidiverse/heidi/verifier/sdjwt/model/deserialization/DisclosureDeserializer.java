// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model.deserialization;

import org.heidiverse.heidi.verifier.sdjwt.model.ArrayElementDisclosure;
import org.heidiverse.heidi.verifier.sdjwt.model.Disclosure;
import org.heidiverse.heidi.verifier.sdjwt.model.ObjectPropertyDisclosure;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdNodeBasedDeserializer;

public class DisclosureDeserializer extends StdNodeBasedDeserializer<Disclosure> {
    public DisclosureDeserializer(Class<Disclosure> targetType) {
        super(targetType);
    }

    public DisclosureDeserializer() {
        super(Disclosure.class);
    }

    @Override
    public Disclosure convert(JsonNode root, DeserializationContext ctxt) {
        if (root.isArray() && root.size() == 2 && root.get(0).isString()) {
            return new ArrayElementDisclosure(
                    root.get(0).asString(), ctxt.readTreeAsValue(root.get(1), Object.class));
        } else if (root.isArray()
                && root.size() == 3
                && root.get(0).isString()
                && root.get(1).isString()
                && !"...".equals(root.get(1).asString())
                && !"_sd".equals(root.get(1).asString())) {
            return new ObjectPropertyDisclosure(
                    root.get(0).asString(),
                    root.get(1).asString(),
                    ctxt.readTreeAsValue(root.get(2), Object.class));
        } else {
            throw new IllegalArgumentException("Disclosure must be a JSON array of length 2 or 3");
        }
    }
}
