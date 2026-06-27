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
package org.os890.jawelte.tests.core.scenario24;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.ServicePriorityResolver;

/**
 * Custom {@link ServicePriorityResolver} that orders providers by
 * {@code @Priority} value DESCENDING — the opposite of the default —
 * so the test can prove that lifecycle-port ordering follows the
 * installed resolver rather than a hard-coded ascending comparator.
 * Selected via MP Config (see microprofile-config.properties).
 */
public class TestScenarioReversePriorityResolver implements ServicePriorityResolver {

    public TestScenarioReversePriorityResolver() {
    }

    @Override
    public <T> List<T> sort(List<T> providers) {
        List<T> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(TestScenarioReversePriorityResolver::priorityOf).reversed());
        return sorted;
    }

    private static int priorityOf(Object provider) {
        Priority annotation = provider.getClass().getAnnotation(Priority.class);
        return annotation != null ? annotation.value() : Integer.MAX_VALUE;
    }
}
