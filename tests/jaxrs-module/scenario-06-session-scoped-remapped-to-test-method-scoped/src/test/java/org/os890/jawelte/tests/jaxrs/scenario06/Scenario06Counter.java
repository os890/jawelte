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
package org.os890.jawelte.tests.jaxrs.scenario06;

import jakarta.enterprise.context.SessionScoped;

/**
 * {@code @SessionScoped} bean — but jaxrs-module's CDI Extension
 * rewrites its effective scope to {@code @TestMethodScoped} at
 * {@code ProcessAnnotatedType} time. The class doesn't implement
 * {@link java.io.Serializable} (which {@code @SessionScoped}
 * normally requires) because by the time CDI runs its passivation
 * validation the rewrite has already turned it into a
 * {@code @TestMethodScoped} bean, a non-passivating normal scope.
 *
 * <p>Used by scenario 06 to verify the remap end-to-end: two HTTP
 * requests within one test method observe accumulating state on
 * the SAME instance; the next test method observes a freshly
 * allocated instance (count starts at 0).
 */
@SessionScoped
public class Scenario06Counter {

    private int count;

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario06Counter() {
    }

    /**
     * Increment the counter and return the new value.
     *
     * @return the post-increment count (starts at 1 on the first
     *         call within a {@code @TestMethodScoped} lifetime)
     */
    public int increment() {
        return ++count;
    }
}
