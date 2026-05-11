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
package org.os890.jawelte.tests.ejb.scenario13;

import jakarta.ejb.AccessTimeout;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;

/**
 * EJB {@code @Singleton} carrying {@code @Lock(READ)} and
 * {@code @AccessTimeout(5000)}. Neither annotation has any effect
 * under ejb-module — the lock and timeout are silently ignored;
 * only the {@code @Singleton} → {@code @ApplicationScoped} +
 * {@code @Transactional} mapping is applied.
 */
@Singleton
@Lock(LockType.READ)
@AccessTimeout(5000)
public class LockedSingleton {

    /** Required public no-arg constructor. */
    public LockedSingleton() {
    }

    /**
     * @return a literal "locked" marker
     */
    public String tag() {
        return "locked";
    }
}
