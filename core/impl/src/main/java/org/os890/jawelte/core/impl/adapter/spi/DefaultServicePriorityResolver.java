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
package org.os890.jawelte.core.impl.adapter.spi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import org.os890.jawelte.core.api.port.ServicePriorityResolver;

/**
 * Default {@link ServicePriorityResolver}. Orders providers by
 * {@code jakarta.annotation.Priority} value ascending; providers
 * without {@code @Priority} get an effective priority of
 * {@link Integer#MAX_VALUE} (sort last); ties are broken by the
 * provider's full class name ascending so the order is stable,
 * deterministic, and independent of classpath enumeration order.
 *
 * <p>Annotated {@link ApplicationScoped} so the cdi-module's CDI
 * Extension can obtain it via {@code CDI.current().select(...).get()}
 * once the container is up. {@code core/impl}'s {@code beans.xml}
 * uses {@code bean-discovery-mode="annotated"} to pick this bean up
 * automatically.
 *
 * <p>Selected as the project-wide default by the bundled MP Config
 * entry that {@code core/impl} ships in
 * {@code META-INF/microprofile-config.properties} under the key whose
 * name equals {@link ServicePriorityResolver}'s own FQCN. Downstream
 * projects override by setting the same key in a higher-priority MP
 * Config source.
 */
@ApplicationScoped
public class DefaultServicePriorityResolver implements ServicePriorityResolver {

    /**
     * No-arg constructor required by both CDI and the reflective
     * fallback path in {@link org.os890.jawelte.core.api.port.TestContext#loadService(Class)}.
     */
    public DefaultServicePriorityResolver() {
    }

    @Override
    public <T> List<T> sort(List<T> providers) {
        List<T> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator
                .comparingInt(DefaultServicePriorityResolver::priorityOf)
                .thenComparing(provider -> provider.getClass().getName()));
        return sorted;
    }

    private static int priorityOf(Object provider) {
        Priority annotation = provider.getClass().getAnnotation(Priority.class);
        return annotation != null ? annotation.value() : Integer.MAX_VALUE;
    }
}
