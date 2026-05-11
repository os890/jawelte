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
package org.os890.jawelte.tests.ejb.scenario14;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;

/**
 * {@code @Singleton @Startup} bean — the EJB contract demands eager
 * initialization. ejb-module silently ignores {@code @Startup}; the
 * bean follows CDI's lazy {@code @ApplicationScoped} contract, so
 * {@code @PostConstruct} fires only on the first {@code @Inject}
 * resolution, not at container bootstrap.
 */
@Singleton
@Startup
public class EagerSingleton {

    /** Counts {@code @PostConstruct} invocations; zero until the first injection touches the bean. */
    public static final AtomicInteger POST_CONSTRUCT_COUNT = new AtomicInteger();

    /** Required public no-arg constructor. */
    public EagerSingleton() {
    }

    /** Lifecycle callback — ejb-module does not trigger this at startup. */
    @PostConstruct
    void onPostConstruct() {
        POST_CONSTRUCT_COUNT.incrementAndGet();
    }

    /**
     * @return a literal "eager" marker
     */
    public String tag() {
        return "eager";
    }
}
