/*
 * Copyright 2026 os890
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.os890.jawelte.module.flowassert.impl.adapter.config;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Contributes the {@code cdi-flow.*} values derived from
 * {@link org.os890.jawelte.module.flowassert.api.EnableFlowAssert} to
 * MicroProfile Config, which is where the recorder reads them.
 *
 * <p>Ordinal 250: above the {@code microprofile-config.properties}
 * defaults of the modules (100), below the system-property source
 * (400). So an annotation outranks a properties file, and
 * {@code -Dcdi-flow.include-pattern=…} still outranks the annotation —
 * the same precedence every other jawelte setting follows.
 *
 * <p>Values are computed on demand rather than at construction: this
 * source is created once per {@code Config} instance, while the
 * annotation it reads belongs to whichever test class is being
 * bootstrapped right now.
 *
 * <p>{@link #getPropertyNames()} answers with the keys of the current
 * test class, which is a snapshot rather than a promise — nothing in
 * the lookup path depends on it, and a caller enumerating names
 * outside a bootstrap window sees the disabled state.
 */
public class FlowRecordingConfigSource implements ConfigSource {

    private static final int ORDINAL = 250;

    private static final String PREFIX = "cdi-flow.";

    /**
     * Guards against re-entering the lookup: deriving the values reads
     * jawelte's own configuration keys, which goes through this very
     * {@code Config}. Returning {@code null} while a derivation is in
     * progress on this thread keeps that a two-level lookup instead of
     * a cycle.
     */
    private static final ThreadLocal<Boolean> DERIVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public FlowRecordingConfigSource() {
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public String getName() {
        return FlowRecordingConfigSource.class.getSimpleName();
    }

    @Override
    public Set<String> getPropertyNames() {
        return settings().keySet();
    }

    @Override
    public String getValue(String propertyName) {
        if (propertyName == null || !propertyName.startsWith(PREFIX)) {
            return null;
        }
        return settings().get(propertyName);
    }

    private Map<String, String> settings() {
        if (Boolean.TRUE.equals(DERIVING.get())) {
            return Map.of();
        }
        DERIVING.set(Boolean.TRUE);
        try {
            return FlowRecordingSettings.current();
        } finally {
            DERIVING.set(Boolean.FALSE);
        }
    }
}
