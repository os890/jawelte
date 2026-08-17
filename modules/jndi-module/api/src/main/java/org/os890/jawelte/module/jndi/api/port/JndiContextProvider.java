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
package org.os890.jawelte.module.jndi.api.port;

import javax.naming.Context;

import org.os890.jawelte.core.api.port.TestContext;

/**
 * Port for the in-process JNDI naming tree: hands out the single
 * writable root context every module binds into.
 *
 * <p>Naming is shared infrastructure rather than one module's
 * concern. jta-module binds {@code TransactionManager} /
 * {@code UserTransaction} /
 * {@code TransactionSynchronizationRegistry} under the standard
 * Jakarta-EE names; any further module that publishes something by
 * name — declared data sources, say — binds into the same tree. They
 * need <em>the same</em> root, which is exactly why this is a port and
 * not a helper class copied per module.
 *
 * <p><b>Why a module of its own rather than a core port.</b> The core
 * defines what the <em>test framework</em> needs — a lifecycle, a test
 * context, prioritized SPI lookup, configuration. It never looks
 * anything up by name. JNDI is something individual integrations need,
 * so it belongs beside them: a module the integrations that care about
 * naming depend on, and that everybody else never pulls in. Putting it
 * in the core would have made every consumer carry a concept the
 * framework itself has no use for.
 *
 * <p><b>Why a shared root is mandatory, not merely tidy.</b>
 * Installing an in-process provider means installing a fresh
 * writable root as the naming system's global context. A second
 * module doing that independently would replace the root the first
 * one already bound into, and the earlier bindings would silently
 * vanish. One provider, resolved through
 * {@link TestContext#loadService(Class)}, gives one installation and
 * one root for the whole JVM.
 *
 * <p><b>No compile-time dependency on a naming provider.</b> The
 * implementation is expected to install whatever provider it finds
 * at runtime and to say so by returning {@code null} when there is
 * none — the project's test classpath supplies
 * {@code org.apache.xbean:xbean-naming}, a Jakarta-EE deployment
 * brings its container's own, and a plain unit test may have
 * neither. {@code null} is a normal answer, not a failure: consumers
 * disagree about whether missing naming is fatal, so callers that can
 * work without JNDI skip binding, and callers that cannot raise their
 * own error naming what they needed it for.
 *
 * <p>Resolved via {@code TestContext.loadService(JndiContextProvider.class)}.
 * jndi-module/impl ships the default implementation at
 * {@code @Priority(Integer.MAX_VALUE)}, so a consumer can substitute
 * a provider for a different naming implementation through
 * {@code META-INF/services}.
 */
public interface JndiContextProvider {

    /**
     * Return the writable root of the JNDI naming tree, installing an
     * in-process naming provider on first call if one is available
     * and not yet initialised.
     *
     * <p>Idempotent: repeated calls return the same root and never
     * re-install, so bindings made by an earlier caller survive.
     *
     * @return the writable root context, or {@code null} when no JNDI
     *         provider is available in this JVM
     */
    Context writableRoot();
}
