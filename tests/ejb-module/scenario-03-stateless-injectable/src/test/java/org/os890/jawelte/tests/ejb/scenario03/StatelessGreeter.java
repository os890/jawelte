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
package org.os890.jawelte.tests.ejb.scenario03;

import jakarta.ejb.Stateless;

/**
 * {@code @jakarta.ejb.Stateless} bean — ejb-module's default mapper
 * turns this into an {@code @Dependent} CDI bean, so every injection
 * point gets its own instance (per the EJB stateless contract, from
 * the consumer's perspective).
 */
@Stateless
public class StatelessGreeter {

    /**
     * Required public no-arg constructor.
     */
    public StatelessGreeter() {
    }

    /**
     * Returns a deterministic greeting for the given name.
     *
     * @param name the greeted entity; never {@code null}
     * @return {@code "Hi, " + name}
     */
    public String greet(String name) {
        return "Hi, " + name;
    }
}
