// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StatusListCodecTest {
    private final StatusListCodec codec = new StatusListCodec();

    @Test
    void encodesDraftVector() {
        var data = new byte[] {(byte) 0xC9, 0x44, (byte) 0xF9};

        assertThat(codec.encode(data)).isEqualTo("eNo76fITAAPfAgc");
    }

    @Test
    void readsAndWritesPackedEntries() {
        var data = codec.empty(12, 2);

        codec.set(data, 2, 0, 1);
        codec.set(data, 2, 1, 2);
        codec.set(data, 2, 3, 3);

        assertThat(data[0] & 0xFF).isEqualTo(0xC9);
        assertThat(codec.get(data, 2, 0)).isEqualTo(1);
        assertThat(codec.get(data, 2, 1)).isEqualTo(2);
        assertThat(codec.get(data, 2, 3)).isEqualTo(3);
    }

    @Test
    void rejectsValuesThatDoNotFit() {
        var data = codec.empty(8, 1);

        assertThatThrownBy(() -> codec.set(data, 1, 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
