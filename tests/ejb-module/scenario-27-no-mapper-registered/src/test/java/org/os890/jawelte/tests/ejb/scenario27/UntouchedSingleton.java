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
package org.os890.jawelte.tests.ejb.scenario27;

import jakarta.ejb.Singleton;

/**
 * The mapper chain in this scenario is intentionally a no-op (the
 * test ships a terminal that returns {@code null} for every class,
 * suppressing the shipping default). Nonetheless the bean must be
 * discoverable through the stereotype declaration alone, with the
 * EJB baseline scope ({@code @ApplicationScoped}) inherited from
 * the stereotype.
 */
@Singleton
public class UntouchedSingleton {

    /** Required public no-arg constructor. */
    public UntouchedSingleton() {
    }
}
