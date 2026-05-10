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
package org.os890.jawelte.tests.jta.scenario29;

import jakarta.enterprise.context.ApplicationScoped;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/** {@code @ReadOnly}-only service — no {@code @Transactional}. */
@ApplicationScoped
public class ReadOnlyOnlyService {

    /** No-arg constructor required by CDI. */
    public ReadOnlyOnlyService() {
    }

    /** No tx active → ReadOnlyInterceptor proceeds as a no-op. */
    @ReadOnly
    public String computeWithoutTx(String input) {
        return "readonly:" + input;
    }
}
