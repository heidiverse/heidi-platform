// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

public class ThumbnailUtils {

    private static final int MAX_THUMBNAIL_SIZE_BYTES = 1024 * 1024; // 1 MB

    public static void validateThumbnail(String base64Thumbnail) {
        try {
            // Check for and remove the data URL prefix if present
            if (base64Thumbnail.startsWith("data:image/")) {
                int commaIndex = base64Thumbnail.indexOf(",");
                if (commaIndex > 0) {
                    base64Thumbnail = base64Thumbnail.substring(commaIndex + 1);
                } else {
                    throw new IllegalArgumentException("Invalid base64 thumbnail format");
                }
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Thumbnail);

            // Check size limit
            if (imageBytes.length > MAX_THUMBNAIL_SIZE_BYTES) {
                throw new IllegalArgumentException(
                        String.format(
                                "Thumbnail exceeds the maximum size of %.2f MB",
                                MAX_THUMBNAIL_SIZE_BYTES / (1024.0 * 1024.0)));
            }

            // Verify the image format (e.g., JPEG/PNG)
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (image == null) {
                throw new IllegalArgumentException(
                        "Invalid image format: Supported formats are JPEG and PNG");
            }
        } catch (IllegalArgumentException | IOException e) {
            throw new IllegalArgumentException("Invalid thumbnail: " + e.getMessage(), e);
        }
    }
}
