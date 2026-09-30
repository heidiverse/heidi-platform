// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.data.util;


import java.io.IOException;
import java.lang.reflect.Type;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.json.JsonMapper;

// See: https://github.com/spring-projects/spring-boot/issues/47789#issuecomment-3443848646
// You can probably remove this when updating to hibernate 7.3: https://hibernate.atlassian.net/browse/HHH-19890
public final class JacksonJsonFormatMapper extends AbstractJsonFormatMapper {

    public static final String SHORT_NAME = "jackson";

    private final JsonMapper jsonMapper;

    public JacksonJsonFormatMapper() {
        this(JsonMapper.shared());
    }

    public JacksonJsonFormatMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public <T> void writeToTarget(T value, JavaType<T> javaType, Object target, WrapperOptions options) throws IOException {
        this.jsonMapper.writerFor(this.jsonMapper.constructType(javaType.getJavaType())).writeValue((JsonGenerator)target, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T readFromSource(JavaType<T> javaType, Object source, WrapperOptions options) throws IOException {
        return (T)this.jsonMapper.readValue((JsonParser)source, this.jsonMapper.constructType(javaType.getJavaType()));
    }

    public boolean supportsSourceType(Class<?> sourceType) {
        return JsonParser.class.isAssignableFrom(sourceType);
    }

    public boolean supportsTargetType(Class<?> targetType) {
        return JsonGenerator.class.isAssignableFrom(targetType);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromString(CharSequence charSequence, Type type) {
        return (T) this.jsonMapper.readValue(charSequence.toString(), this.jsonMapper.constructType(type));
    }

    public <T> String toString(T value, Type type) {
        return this.jsonMapper.writerFor(this.jsonMapper.constructType(type)).writeValueAsString(value);
    }
}
