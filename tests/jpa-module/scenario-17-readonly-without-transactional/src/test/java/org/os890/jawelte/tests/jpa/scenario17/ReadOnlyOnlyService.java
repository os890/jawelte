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
package org.os890.jawelte.tests.jpa.scenario17;

import jakarta.enterprise.context.ApplicationScoped;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/**
 * CDI bean with a {@code @ReadOnly}-only method (no {@code @Transactional}).
 * The {@code ReadOnlyInterceptor} fires but is a documented no-op when no
 * transaction is active on the calling thread — the body runs as written and
 * its return value reaches the caller unchanged.
 */
@ApplicationScoped
public class ReadOnlyOnlyService {

    /** No-arg constructor required by CDI. */
    public ReadOnlyOnlyService() {
    }

    /** @ReadOnly without @Transactional — interceptor fires and proceeds (no-op). */
    @ReadOnly
    public String computeWithoutTx(String input) {
        return "readonly:" + input;
    }
}
