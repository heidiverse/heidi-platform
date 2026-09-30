// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionDataHasherTest {

    @Test
    void hashesOpaqueTransactionDataInOrder() {
        assertEquals(
                List.of(
                        "mSCOpRtYy5UXwD0WxISE10ZYeZLafaVl6qstEVbguTQ",
                        "j8HtZqLpcNbWS3CtbxjEmVqX2lvyjvSjgnihNrpBOUY"),
                TransactionDataHasher.hashAndBase64UrlEncode(
                        List.of("transaction-data-one", "transaction-data-two")));
    }
}
