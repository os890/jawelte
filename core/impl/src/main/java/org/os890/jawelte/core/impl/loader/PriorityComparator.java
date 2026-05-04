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
package org.os890.jawelte.core.impl.loader;

import java.util.Comparator;

import jakarta.annotation.Priority;

/**
 * Comparator that orders objects by their {@link Priority} annotation
 * value (lower value first). Instances without {@code @Priority} get
 * an effective priority of {@link Integer#MAX_VALUE} (sort last).
 *
 * <p>Used by {@link ServiceLoaderCache} to sort
 * {@code TestModuleLifecyclePort} implementations into the order the
 * delegating extension invokes them for {@code beforeAll} /
 * {@code beforeEach}; reverse iteration gives the LIFO order required
 * for {@code afterEach} / {@code afterAll}.
 *
 * @param <T> the element type
 */
public class PriorityComparator<T> implements Comparator<T> {

    /**
     * Default no-arg constructor.
     */
    public PriorityComparator() {
    }

    @Override
    public int compare(T left, T right) {
        return Integer.compare(priorityOf(left), priorityOf(right));
    }

    private static int priorityOf(Object instance) {
        Priority annotation = instance.getClass().getAnnotation(Priority.class);
        return annotation != null ? annotation.value() : Integer.MAX_VALUE;
    }
}
