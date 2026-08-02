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
package org.os890.jawelte.tests.ejb.scenario29;

import jakarta.ejb.Stateless;

/**
 * Plain EJB session bean the classpath scan must discover, so both
 * test classes verify the cached scan still yields a usable bean and
 * not just a fast no-op.
 */
@Stateless
public class ScannedStateless {

    /** No-arg constructor for CDI. */
    public ScannedStateless() {
    }

    /** @return a constant, so the injected bean can be exercised */
    public String ping() {
        return "pong";
    }
}
