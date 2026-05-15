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
package org.os890.jawelte.tests.jaxrs.scenario16;

import jakarta.enterprise.context.SessionScoped;

/**
 * {@code @SessionScoped} bean — rewritten to
 * {@code @TestMethodScoped} by {@code JaxRsCdiExtension}'s
 * unconditional remap. Test class does not carry
 * {@code @EnableJaxRs}, so this scenario proves the remap is
 * <em>global</em>: it fires on every CDI bootstrap that has
 * jaxrs-module's extension on the classpath, not just when a
 * resource is being served.
 */
@SessionScoped
public class Scenario16Counter {

    private int count;

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario16Counter() {
    }

    /**
     * Increment and return the new value.
     *
     * @return the post-increment count (starts at 1 on first call
     *         within a fresh {@code @TestMethodScoped} lifetime)
     */
    public int increment() {
        return ++count;
    }
}
