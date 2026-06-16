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
package org.os890.jawelte.module.jpa.impl.adapter.cdi;

import java.util.ServiceLoader;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jpa.api.port.CdiTransactionalSupportProvider;

/**
 * Default {@link CdiTransactionalSupportProvider} shipped by
 * {@code jpa-module/impl}. Reports that no platform integration
 * provides {@code @Transactional} or {@code @TransactionScoped}, so
 * jpa-module hosts both itself.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — the project-wide
 * "lowest priority wins" pattern picks this when no module ships
 * a higher-priority alternative; {@code jta-module/impl} ships one
 * that probes for vendor JTA CDI integrations on the classpath and
 * wins when present.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultCdiTransactionalSupportProvider implements CdiTransactionalSupportProvider {

    /** No-arg constructor required by {@link ServiceLoader}. */
    public DefaultCdiTransactionalSupportProvider() {
    }
}
