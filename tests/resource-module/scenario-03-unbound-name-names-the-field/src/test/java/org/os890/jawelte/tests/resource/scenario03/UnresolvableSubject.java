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
package org.os890.jawelte.tests.resource.scenario03;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Run through {@code EngineTestKit} by {@link Scenario03Test}, never by
 * surefire directly - the name deliberately does not match the
 * test-class pattern, because this class is meant to fail.
 */
@EnableTestBeans
public class UnresolvableSubject {

    @Inject
    TypoRepository repository;

    /** The failure happens on the way in; the body is never reached. */
    @Test
    void usesTheRepository() {
        repository.declared();
    }
}
