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
package org.os890.jawelte.module.jndi.impl.adapter;

import javax.naming.Context;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jndi.api.port.JndiContextProvider;

/**
 * Default {@link JndiContextProvider} shipped by jndi-module/impl:
 * installs Apache XBean's naming provider once per JVM and returns its
 * writable global context.
 *
 * <p>xbean-naming is a {@code provided} dependency and the installation
 * is written against xbean's own types in {@link XbeanNamingTree} — see
 * there for what "installing" means and why it happens only once. This
 * adapter <em>is</em> the xbean adapter: it cannot function without
 * xbean, so xbean is not treated as optional anywhere in the module, and
 * a naming implementation other than xbean is expected to ship its own
 * provider at a lower {@code @Priority} rather than to be accommodated
 * here. {@code provided} is the same shape jpa-module uses for
 * {@code xbean-finder-shaded}: compiled against, and not transitive, so
 * nothing that takes jndi-module/impl inherits a naming provider from
 * it.
 *
 * <p><b>The absence answer.</b> The port reports "no naming provider in
 * this JVM" by returning {@code null}, and there are two ways for a
 * classpath to be in that state. Leaving jndi-module/impl out is the
 * obvious one, and it needs nothing from this class:
 * {@link org.os890.jawelte.core.api.port.TestContext#loadService(Class)}
 * finds no provider and answers {@code null} on its own. The other is
 * leaving xbean-naming out while this module is still present, which is
 * the state a consumer is actually in — every module that binds pulls
 * jndi-module/impl in transitively at runtime scope — and it is how a
 * consumer's own degradation scenario is built, by omitting one test
 * dependency rather than by excluding a transitive one.
 *
 * <p>That second state is recognised by catching the linkage failure
 * from the first touch of an xbean type, rather than by predicting it
 * with a {@code Class.forName} probe: the module compiles against xbean,
 * so asking at runtime whether xbean is there would be the one piece of
 * reflection left in a class that has no other reason to reflect. All
 * xbean references live in {@link XbeanNamingTree}, so the failure has
 * exactly one place it can come from, and that class defers the
 * {@code java.naming.*} system properties until after its first xbean
 * touch has succeeded — so a JVM that cannot use those properties never
 * has them set. (jta-module's old bootstrap set them first and
 * discovered xbean's absence afterwards, leaving a JVM whose only naming
 * provider was its container's with {@code java.naming.factory.initial}
 * naming a class it could not load.)
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} so a consumer can register a
 * provider for a different naming implementation at a lower priority via
 * {@code META-INF/services}.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultJndiContextProvider implements JndiContextProvider {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DefaultJndiContextProvider() {
    }

    @Override
    public Context writableRoot() {
        try {
            return XbeanNamingTree.writableRoot();
        } catch (NoClassDefFoundError noNamingProvider) {
            // xbean-naming is not on this classpath. Whoever supplies
            // the InitialContextFactory instead (a Jakarta-EE container,
            // a consumer's own provider) owns its own root context;
            // null is the port's documented answer and the caller
            // decides whether it can carry on without naming.
            return null;
        }
    }
}
