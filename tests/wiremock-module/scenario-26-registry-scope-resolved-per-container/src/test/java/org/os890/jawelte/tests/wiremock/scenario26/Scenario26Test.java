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
package org.os890.jawelte.tests.wiremock.scenario26;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.impl.adapter.extension.remap.WireMockRegistryScopeRemap;

/**
 * The registry {@code BeanScopeMapper}'s target scope must be resolved
 * per instance (a fresh provider is created per container) from the
 * active MP Config layer — NOT captured once in a static field and
 * frozen for the JVM. This guards against the "first container wins"
 * regression: a second container with a different
 * {@code …registry.default-scope} value must see its own value.
 *
 * <p>Two {@code WireMockRegistryScopeRemap} instances are built with
 * different config values (via a system property, which MP Config reads
 * live); each must report its own target scope. On the static-field
 * implementation the second instance would report the first's frozen
 * value. Uses {@code @RequestScoped} / {@code @ApplicationScoped} (always
 * on the classpath) purely as two distinct resolvable scope annotations.
 */
class Scenario26Test {

    @Test
    void targetScopeIsResolvedPerInstanceNotFrozenForTheJvm() {
        String key = WireMockRegistryScopeRemap.TARGET_SCOPE_KEY;
        try {
            System.setProperty(key, RequestScoped.class.getName());
            assertThat(new WireMockRegistryScopeRemap().targetScope())
                    .as("first instance reflects the first config value")
                    .isEqualTo(RequestScoped.class);

            System.setProperty(key, ApplicationScoped.class.getName());
            assertThat(new WireMockRegistryScopeRemap().targetScope())
                    .as("a later instance must reflect the CURRENT config value, "
                            + "not the value frozen by the first instance")
                    .isEqualTo(ApplicationScoped.class);
        } finally {
            System.clearProperty(key);
        }
    }
}
