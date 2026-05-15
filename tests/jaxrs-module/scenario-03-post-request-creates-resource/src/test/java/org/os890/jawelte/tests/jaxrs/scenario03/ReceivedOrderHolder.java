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
package org.os890.jawelte.tests.jaxrs.scenario03;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Shared {@code @ApplicationScoped} state used by scenario 03 to
 * verify that the POST body the embedded server saw reaches the
 * test thread. The resource bean writes via {@link #setBody(String)}
 * on a server worker thread; the test reads via {@link #getBody()}
 * on the JUnit thread. {@code volatile} for the cross-thread
 * publication.
 */
@ApplicationScoped
public class ReceivedOrderHolder {

    private volatile String body;

    /** Default no-arg constructor (CDI-discoverable). */
    public ReceivedOrderHolder() {
    }

    /**
     * Publish the POST body the resource just saw.
     *
     * @param body the request entity as read by the resource;
     *             may be {@code null}
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * Read the most recently published POST body.
     *
     * @return the last seen body; {@code null} if no POST has
     *         arrived yet
     */
    public String getBody() {
        return body;
    }
}
