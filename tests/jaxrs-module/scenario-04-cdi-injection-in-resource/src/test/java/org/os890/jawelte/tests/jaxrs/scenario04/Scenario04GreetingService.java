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
package org.os890.jawelte.tests.jaxrs.scenario04;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Trivial business service consumed by the resource in scenario 04
 * to verify that {@code @Inject} into a JAX-RS resource resolves to
 * a real CDI bean (not just a no-op reflective instantiation).
 */
@ApplicationScoped
public class Scenario04GreetingService {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario04GreetingService() {
    }

    /**
     * Compose a greeting around the supplied name.
     *
     * @param name the recipient
     * @return {@code "Hello, " + name}
     */
    public String greet(String name) {
        return "Hello, " + name;
    }
}
