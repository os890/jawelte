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
package org.os890.jawelte.module.resource.impl.adapter.lookup;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jndi.api.port.JndiContextProvider;
import org.os890.jawelte.module.resource.api.port.ResourceLookup;

/**
 * The shipped {@link ResourceLookup}: resolves names against the
 * writable root jndi-module hands out.
 *
 * <p>That is the same tree datasource-module binds its
 * {@code @DataSourceDefinition} entries into, and jndi-module installs
 * its root with {@code supportReferenceable} off — so a lookup returns
 * the object that was bound rather than a reconstruction of it, and
 * {@code @Resource(lookup = "java:app/jdbc/AppDS")} yields the very
 * same {@code DataSource} instance as
 * {@code @Inject @Named("java:app/jdbc/AppDS")}. An application can mix
 * both idioms and still be talking to one connection pool.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} makes this the fallback:
 * every custom {@link ResourceLookup} outranks it, which is the point —
 * a consumer resolving names from somewhere other than a naming tree
 * replaces it by shipping a lower number.
 *
 * <p><b>No naming provider is an error here</b>, unlike in
 * datasource-module where it merely skips binding. A
 * {@code @Resource} field cannot be filled without one, and leaving it
 * {@code null} is precisely the failure mode this module exists to
 * remove — a null that surfaces much later, somewhere else.
 */
@Priority(Integer.MAX_VALUE)
public class JndiResourceLookup implements ResourceLookup {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public JndiResourceLookup() {
    }

    @Override
    public Object lookup(String name, Class<?> targetType) {
        JndiContextProvider provider = TestContext.loadService(JndiContextProvider.class);
        Context root = provider == null ? null : provider.writableRoot();
        if (root == null) {
            throw new IllegalStateException(
                    "@Resource(\"" + name + "\") cannot be resolved: no JNDI naming provider is installed"
                            + " in this JVM. resource-module resolves names through jndi-module's"
                            + " JndiContextProvider, whose shipped adapter needs xbean-naming on the test"
                            + " classpath. Add it, or ship a ResourceLookup that resolves names some other"
                            + " way.");
        }
        try {
            return root.lookup(name);
        } catch (NameNotFoundException notBound) {
            // Reported by the caller, which knows the field the name
            // came from and can say something more useful than the
            // naming layer can.
            return null;
        } catch (NamingException lookupFailure) {
            throw new IllegalStateException(
                    "@Resource(\"" + name + "\") could not be looked up in the JNDI naming tree",
                    lookupFailure);
        }
    }
}
