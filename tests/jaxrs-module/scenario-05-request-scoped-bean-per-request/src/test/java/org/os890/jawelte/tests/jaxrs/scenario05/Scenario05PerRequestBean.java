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
package org.os890.jawelte.tests.jaxrs.scenario05;

import jakarta.enterprise.context.RequestScoped;

/**
 * {@code @RequestScoped} bean that reports its own identity hash.
 * Used by scenario 05 to verify that two HTTP requests within the
 * same test method observe different instances — proving the
 * {@code CdiIntegrationFilter} activates and deactivates the
 * request context per HTTP request rather than sharing a single
 * context across the test method's worker-thread dispatches.
 */
@RequestScoped
public class Scenario05PerRequestBean {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario05PerRequestBean() {
    }

    /**
     * The JVM identity hash of {@code this} — the actual bean
     * instance (not the proxy). Two distinct request-scoped
     * instances guarantee two distinct values (in practice; hash
     * collisions are astronomically unlikely here).
     *
     * @return {@code System.identityHashCode(this)}
     */
    public int identity() {
        return System.identityHashCode(this);
    }
}
