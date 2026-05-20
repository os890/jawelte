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
package example.priorityresolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.ServicePriorityResolver;

/**
 * Sorts providers by {@code @Priority} <em>descending</em> — the
 * inverse of the framework default. Selected as the active resolver
 * by setting the MP Config key
 * {@code org.os890.jawelte.core.api.port.ServicePriorityResolver}
 * to this class's FQCN; the listing's surefire configuration passes
 * the FQCN as a system property (system properties carry MP Config
 * ordinal 400, beating the ordinal-100
 * META-INF/microprofile-config.properties default that core/impl
 * ships).
 *
 * <p>Public no-arg constructor required: jawelte instantiates the
 * accessor reflectively via
 * {@code Class.getDeclaredConstructor().newInstance()}.
 */
public class ReversePriorityResolver implements ServicePriorityResolver {

    public ReversePriorityResolver() {
    }

    @Override
    public <T> List<T> sort(List<T> providers) {
        List<T> copy = new ArrayList<>(providers);
        copy.sort(Comparator.comparingInt(ReversePriorityResolver::priorityOf).reversed());
        return copy;
    }

    private static int priorityOf(Object provider) {
        Priority priority = provider.getClass().getAnnotation(Priority.class);
        return priority == null ? Integer.MIN_VALUE : priority.value();
    }
}
