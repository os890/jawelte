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
package org.os890.jawelte.module.cdi.api.port;

/**
 * Whitelist policy for {@code @EnableTestBeans(limitToTestBeans=true)}
 * mode. cdi-module's CDI Extension calls the active filter during
 * {@code ProcessAnnotatedType} for every encountered type; a
 * {@code false} result triggers
 * {@code ProcessAnnotatedType.veto()}, removing the type from CDI.
 *
 * <p>Discovered via {@code ServiceLoader} and selected by
 * {@link org.os890.jawelte.core.api.port.TestContext#loadService(Class)},
 * which routes the priority sort through the active
 * {@link org.os890.jawelte.core.api.port.ServicePriorityResolver}.
 * Lower {@code @Priority} value wins.
 *
 * <p>The default implementation lives in {@code cdi-module/impl}
 * ({@code DefaultWhitelistFilter}) and allows a type when it is on
 * the framework allowlist (java/jakarta/javax/OWB/Weld internals,
 * plus the framework root package — configurable via the MP Config
 * key {@code org.os890.jawelte.module.cdi.framework-allowlist.packages}),
 * <em>or</em> when the type is a {@code @TestBean} target on the
 * active test class.
 *
 * <p>The CDI Extension only invokes this port when
 * {@code limitToTestBeans=true}; in normal mode the port is never
 * consulted.
 */
public interface WhitelistFilter {

    /**
     * Whether the given type may remain a CDI bean in
     * {@code limitToTestBeans=true} mode.
     *
     * @param rawType the type the CDI Extension is considering
     *                preserving (otherwise it will be vetoed)
     * @return {@code true} to keep the type as a CDI bean;
     *         {@code false} to veto
     */
    boolean isAllowed(Class<?> rawType);
}
