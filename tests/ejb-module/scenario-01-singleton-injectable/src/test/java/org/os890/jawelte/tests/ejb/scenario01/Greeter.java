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
package org.os890.jawelte.tests.ejb.scenario01;

import jakarta.ejb.Singleton;

/**
 * Production-shape {@code @jakarta.ejb.Singleton} bean. ejb-module's
 * default mapper should turn this into an {@code @ApplicationScoped}
 * CDI bean (scope-module is not on this scenario's classpath) so it
 * resolves to a single shared instance for the whole CDI container.
 */
@Singleton
public class Greeter {

    /**
     * Required public no-arg constructor.
     */
    public Greeter() {
    }

    /**
     * Returns a deterministic greeting for the given name.
     *
     * @param name the greeted entity; never {@code null}
     * @return {@code "Hello, " + name}
     */
    public String greet(String name) {
        return "Hello, " + name;
    }
}
