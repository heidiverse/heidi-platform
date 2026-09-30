// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.springframework.stereotype.Component;

@Component
public class StatusListCodec {
    private static final Set<Integer> ALLOWED_BITS = Set.of(1, 2, 4, 8);

    public byte[] empty(int entries, int bits) {
        requireBits(bits);
        if (entries < 1) throw new IllegalArgumentException("Entry count must be positive");

        return new byte[Math.ceilDiv(Math.multiplyExact(entries, bits), Byte.SIZE)];
    }

    public int get(byte[] data, int bits, int index) {
        requireIndex(data, bits, index);
        var bitOffset = Math.multiplyExact(index, bits);
        var shift = bitOffset % Byte.SIZE;
        var mask = (1 << bits) - 1;
        return (data[bitOffset / Byte.SIZE] >>> shift) & mask;
    }

    public void set(byte[] data, int bits, int index, int status) {
        requireIndex(data, bits, index);
        var mask = (1 << bits) - 1;
        if (status < 0 || status > mask) {
            throw new IllegalArgumentException("Status does not fit in " + bits + " bits");
        }

        var bitOffset = Math.multiplyExact(index, bits);
        var byteIndex = bitOffset / Byte.SIZE;
        var shift = bitOffset % Byte.SIZE;
        var shiftedMask = mask << shift;
        data[byteIndex] = (byte) ((data[byteIndex] & ~shiftedMask) | (status << shift));
    }

    public String encode(byte[] data) {
        try {
            var output = new ByteArrayOutputStream();
            var deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try (var compressed = new DeflaterOutputStream(output, deflater)) {
                compressed.write(data);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not compress status list", exception);
        }
    }

    private void requireIndex(byte[] data, int bits, int index) {
        requireBits(bits);
        if (index < 0 || Math.multiplyExact(index + 1, bits) > data.length * Byte.SIZE) {
            throw new IndexOutOfBoundsException("Status list index is out of bounds: " + index);
        }
    }

    public static void requireBits(int bits) {
        if (!ALLOWED_BITS.contains(bits)) {
            throw new IllegalArgumentException("Bits must be 1, 2, 4, or 8");
        }
    }
}
