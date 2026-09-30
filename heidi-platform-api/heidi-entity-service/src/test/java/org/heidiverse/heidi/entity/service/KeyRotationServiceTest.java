// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.junit.jupiter.api.Test;

class KeyRotationServiceTest {
    @Test
    void retriesFailedKeys() {
        var keys = mock(SigningKeyService.class);
        var identities = mock(IssuerService.class);
        var failed = key();
        var healthy = key();
        when(keys.keys()).thenReturn(List.of(failed, healthy));
        var rotated = mock(SigningKeyService.ProvisionedKey.class);
        when(keys.rotateDue(eq("tenant-a"), eq(failed.getId()), any()))
                .thenThrow(new IllegalStateException("Provider unavailable"))
                .thenReturn(Optional.of(rotated));
        when(keys.rotateDue(eq("tenant-a"), eq(healthy.getId()), any()))
                .thenReturn(Optional.of(rotated), Optional.empty());
        var scheduler = new KeyRotationService(keys, identities);

        scheduler.rotateDueKeys();
        scheduler.rotateDueKeys();

        verify(keys, times(2)).rotateDue(eq("tenant-a"), eq(failed.getId()), any());
        verify(keys, times(2)).rotateDue(eq("tenant-a"), eq(healthy.getId()), any());
        verify(identities, times(2)).reconcileSigningGrants();
    }

    private static SigningKeyEntity key() {
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setTenantId("tenant-a");
        return key;
    }
}
