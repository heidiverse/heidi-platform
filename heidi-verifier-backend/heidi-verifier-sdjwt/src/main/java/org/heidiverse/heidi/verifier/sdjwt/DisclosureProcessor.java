// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt;

import org.heidiverse.heidi.verifier.sdjwt.model.Disclosure;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;
import org.heidiverse.heidi.verifier.sdjwt.util.SdJwtUtil;

import tools.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.*;

public class DisclosureProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DisclosureProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Set<String> reservedClaimNames;

    public DisclosureProcessor(Set<String> reservedClaimNames) {
        this.reservedClaimNames = reservedClaimNames;
    }

    /**
     * Recursively recreates all disclosed properties.
     *
     * @param jwtPayload Generic JSON map describing the JWT's claim set.
     * @param disclosures List of encoded JSON objects describing the disclosed properties.
     * @param hasher Instance of a MessageDigest object to be used for computing any digests.
     * @throws InvalidSdJwtException If any intermediate verification step failed.
     */
    public void processDigests(
            Map<String, Object> jwtPayload, List<String> disclosures, MessageDigest hasher)
            throws InvalidSdJwtException {

        // digests contained in the SD-JWT's payload
        final Set<String> disclosable = HashSet.newHashSet(disclosures.size());

        // digests computed based on the disclosed properties
        final Map<String, Disclosure> disclosed = HashMap.newHashMap(disclosures.size());

        for (final var encoded : disclosures) {
            final String digest = SdJwtUtil.computeDigest(hasher, encoded);

            // Deserialize the base64url-encoded string to a disclosure object
            final Disclosure disclosure = Disclosure.decode(encoded, objectMapper);

            disclosed.put(digest, disclosure);
        }

        handleObjectNode(jwtPayload, disclosed, disclosable);

        logger.debug(
                "SD-JWT disclosed {} of {} disclosable properties",
                disclosed.size(),
                disclosable.size());

        if (!disclosable.containsAll(disclosed.keySet())) {
            // Reject if a disclosure was not referenced by a digest value
            throw new InvalidSdJwtException(
                    "Not all disclosures are referenced in the JWT's payload.");
        }
    }

    /**
     * Performs DFS to recreate any disclosed properties.
     *
     * <p>Current JSON node is an object.
     *
     * @param curr JSON object node.
     * @param disclosed List of pre-computed digests of disclosed properties.
     * @param disclosable Set of encountered digests. A digest may only be encountered at most once.
     * @throws InvalidSdJwtException If any intermediate verification step failed.
     */
    @SuppressWarnings("unchecked")
    private void handleObjectNode(
            Map<String, Object> curr, Map<String, Disclosure> disclosed, Set<String> disclosable)
            throws InvalidSdJwtException {
        Map<String, Object> updates = null;
        for (final var claim : curr.entrySet()) {
            if (SdJwtUtil.SD_CLAIM.equals(claim.getKey())) {
                updates = handleSdClaim(disclosed, disclosable, claim);
            } else if (claim.getValue() instanceof Map<?, ?> m
                    && m.keySet().stream().allMatch(String.class::isInstance)) {
                handleObjectNode((Map<String, Object>) m, disclosed, disclosable);
            } else if (claim.getValue() instanceof List<?>) {
                handleArrayNode((List<Object>) claim.getValue(), disclosed, disclosable);
            }
        }

        curr.remove(SdJwtUtil.SD_CLAIM);
        if (updates != null) {
            curr.putAll(updates);
            handleObjectNode(curr, disclosed, disclosable);
        }
    }

    /**
     * Attempts to recreate disclosable object properties based on the list of disclosed properties.
     *
     * @param disclosed List of pre-computed digests of disclosed properties.
     * @param disclosable Set of encountered digests. A digest may be encountered at most once.
     * @param claim JSON object node containing a list of salted digests of disclosable properties.
     * @return A map describing the disclosed properties to be inserted at the level of this '_sd'
     *     claim.
     * @throws InvalidSdJwtException If any of the listed digests were encountered earlier or the
     *     '_sd' claim was incorrectly formatted.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> handleSdClaim(
            Map<String, Disclosure> disclosed,
            Set<String> disclosable,
            Map.Entry<String, Object> claim)
            throws InvalidSdJwtException {
        final Map<String, Object> updates = new HashMap<>();
        final boolean isValidSdClaim =
                claim.getValue() instanceof List<?> l
                        && l.stream().allMatch(String.class::isInstance);
        if (isValidSdClaim) {
            for (final var digest : (List<String>) claim.getValue()) {
                final var update = handleDigest(disclosed, disclosable, digest);
                if (update != null) {
                    updates.put(update.getKey(), update.getValue());
                }
            }
        } else {
            throw new InvalidSdJwtException(
                    "Invalid claim: value of '_sd' claim must be an array of strings");
        }

        return updates;
    }

    /**
     * Performs DFS to recreate any disclosed properties.
     *
     * <p>Current JSON node is an array.
     *
     * @param curr JSON array node.
     * @param disclosed List of pre-computed digests of disclosed properties.
     * @param disclosable Set of encountered digests. A digest may only be encountered at most once.
     * @throws InvalidSdJwtException If any intermediate verification step failed.
     */
    @SuppressWarnings("unchecked")
    private void handleArrayNode(
            List<Object> curr, Map<String, Disclosure> disclosed, Set<String> disclosable)
            throws InvalidSdJwtException {
        // contains indices of undisclosed array elements
        final List<Integer> undisclosed = new ArrayList<>();
        for (int i = 0; i < curr.size(); i++) {
            var entry = curr.get(i);
            if (entry instanceof Map<?, ?> m && m.containsKey(SdJwtUtil.ARRAY_ELEM_CLAIM)) {
                boolean isDisclosed = handleEllipsisObject(curr, disclosed, disclosable, m, i);
                if (!isDisclosed) {
                    undisclosed.add(i);
                }
            } else if (entry instanceof Map<?, ?> m
                    && m.keySet().stream().allMatch(String.class::isInstance)) {
                handleObjectNode((Map<String, Object>) m, disclosed, disclosable);
            } else if (entry instanceof List<?> l
                    && l.stream().allMatch(String.class::isInstance)) {
                handleArrayNode((List<Object>) l, disclosed, disclosable);
            }
        }

        // Remove any undisclosed ellipsis objects
        undisclosed.sort(Collections.reverseOrder());
        for (int i : undisclosed) {
            curr.remove(i);
        }
    }

    /**
     * Attempts to recreate a disclosable array element based on the list of disclosed properties.
     *
     * @param curr JSON array node containing the property.
     * @param disclosed List of pre-computed digests of disclosed properties.
     * @param disclosable Set of encountered digests. A digest may only be encountered at most once.
     * @param ellipsisObject JSON object node containing a salted digest of the disclosable value.
     * @param i Index of the property within the array.
     * @return True if the element was disclosed.
     * @throws InvalidSdJwtException If the listed digest was encountered earlier or the ellipsis
     *     object is incorrectly formatted.
     */
    private boolean handleEllipsisObject(
            List<Object> curr,
            Map<String, Disclosure> disclosed,
            Set<String> disclosable,
            Map<?, ?> ellipsisObject,
            int i)
            throws InvalidSdJwtException {
        var isDisclosed = false;
        if (ellipsisObject.keySet().size() == 1
                && ellipsisObject.get(SdJwtUtil.ARRAY_ELEM_CLAIM) instanceof String digest) {
            final var update = handleDigest(disclosed, disclosable, digest);
            if (update != null) {
                curr.set(i, update.getValue());
                isDisclosed = true;
            }
        } else {
            throw new InvalidSdJwtException(
                    "Invalid array element disclosure: must be an object with one key, that key"
                            + " being ... and referring to a string.");
        }
        return isDisclosed;
    }

    /**
     * Derives the disclosed (key, value) pair for a given digest value. key is null for array value
     * disclosures.
     */
    private Pair<String, Object> handleDigest(
            final Map<String, Disclosure> disclosed,
            final Set<String> disclosable,
            final String digest)
            throws InvalidSdJwtException {
        Pair<String, Object> update = null;

        // A digest value may only be encountered once (directly or recursively)
        if (disclosable.contains(digest)) {
            throw new InvalidSdJwtException("Duplicate digest detected: " + digest);
        }
        disclosable.add(digest);

        if (disclosed.containsKey(digest)) {
            final var disclosure = disclosed.get(digest);
            if (!reservedClaimNames.contains(disclosure.getKey())) {
                update = Pair.of(disclosure.getKey(), disclosure.getValue());
            } else {
                throw new InvalidSdJwtException(
                        String.format(
                                "Disclosure uses reserved claim name '%s'", disclosure.getKey()));
            }
        }
        return update;
    }
}
