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
package org.os890.jawelte.tests.ejb.scenario25;

import jakarta.ejb.Singleton;

/**
 * {@code @Singleton} that the test-scope additional mapper claims
 * with an EMPTY annotation list. That claims the class — the default
 * mapper does NOT run as a fallback — so ejb-module adds nothing.
 * The class therefore keeps the EJB baseline supplied by the
 * stereotype declaration ({@code @ApplicationScoped} +
 * {@code @Transactional} from the stereotype's member annotations),
 * not the {@code @ApplicationScoped} the default mapper would have
 * added explicitly.
 */
@Singleton
public class ClaimedByEmptyList {

    /** Required public no-arg constructor. */
    public ClaimedByEmptyList() {
    }
}
