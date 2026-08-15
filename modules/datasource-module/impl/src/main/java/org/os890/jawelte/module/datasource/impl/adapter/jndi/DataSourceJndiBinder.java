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
package org.os890.jawelte.module.datasource.impl.adapter.jndi;

import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jndi.api.port.JndiContextProvider;

/**
 * Binds and unbinds the data sources built for a test class under the
 * names their {@code @DataSourceDefinition} declared, so a plain
 * {@code new InitialContext().lookup("java:comp/env/jdbc/OrdersDS")}
 * resolves — the same lookup production code performs.
 *
 * <p><b>The naming tree is not owned here.</b> The writable root comes
 * from jndi-module's {@link JndiContextProvider} port, which installs an
 * in-process provider once per JVM. That is what lets jta-module's
 * transaction artifacts and these data sources coexist in one tree:
 * a module installing its own root would replace the tree and discard
 * whatever the other had already bound.
 *
 * <p><b>Absent naming provider is not an error.</b> When no provider
 * is on the classpath the port returns {@code null} and binding is
 * skipped. Injection still works — it goes through the synthetic CDI
 * beans, not through JNDI — so a test that never performs a lookup is
 * unaffected, and one that does gets the naming layer's own
 * "no initial context" error at the point it actually looks up.
 *
 * <p><b>Configurable</b> via {@code org.os890.jawelte.module.datasource.jndi.enabled}
 * (default {@code true}). Setting it to {@code false} keeps the data
 * sources injectable while leaving the naming tree untouched — for a
 * suite that runs inside a container whose JNDI must not be written
 * to.
 */
public abstract class DataSourceJndiBinder {

    /**
     * MicroProfile Config key toggling JNDI binding. Default
     * {@code true}; the underscore variant is honoured by the
     * platform's own dot-then-underscore fallback.
     */
    public static final String JNDI_ENABLED_KEY = "org.os890.jawelte.module.datasource.jndi.enabled";

    /** Suppress instantiation; the class is a static-method holder. */
    protected DataSourceJndiBinder() {
    }

    /**
     * Bind a data source under its declared name, creating the
     * intermediate sub-contexts the name implies.
     *
     * @param name       the {@code @DataSourceDefinition} name
     * @param dataSource the built data source
     * @return {@code true} when the entry was bound, {@code false}
     *         when binding is disabled or no naming provider exists
     */
    public static boolean bind(String name, DataSource dataSource) {
        Context root = writableRootIfEnabled();
        if (root == null) {
            return false;
        }
        try {
            createParentContexts(root, name);
            root.rebind(name, dataSource);
            return true;
        } catch (NamingException bindFailure) {
            throw new IllegalStateException(
                    "Failed to bind the DataSource declared by @DataSourceDefinition(name = \""
                            + name + "\") into JNDI",
                    bindFailure);
        }
    }

    /**
     * Remove a previously bound entry. Failures are swallowed: this
     * runs in {@code afterAll} cleanup, where a naming tree that has
     * already gone away must not mask the test's own outcome.
     *
     * @param name the {@code @DataSourceDefinition} name
     */
    public static void unbind(String name) {
        Context root = writableRootIfEnabled();
        if (root == null) {
            return;
        }
        try {
            root.unbind(name);
        } catch (NamingException alreadyGone) {
            // Nothing bound (binding was skipped) or the tree is gone.
            // Either way there is nothing left to clean up.
        }
    }

    private static Context writableRootIfEnabled() {
        if (!ConfigProvider.getConfig().getOptionalValue(JNDI_ENABLED_KEY, Boolean.class).orElse(Boolean.TRUE)) {
            return null;
        }
        JndiContextProvider provider = TestContext.loadService(JndiContextProvider.class);
        if (provider == null) {
            return null;
        }
        return provider.writableRoot();
    }

    /**
     * Create the sub-contexts a compound name implies, so that
     * {@code java:comp/env/jdbc/OrdersDS} can be bound at its leaf.
     * Segments that already exist are left alone.
     */
    private static void createParentContexts(Context root, String name) throws NamingException {
        String[] segments = name.split("/");
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++) {
            if (i > 0) {
                path.append('/');
            }
            path.append(segments[i]);
            try {
                root.createSubcontext(path.toString());
            } catch (NameAlreadyBoundException alreadyThere) {
                // A previous definition, or jta-module, already created
                // this level of the tree. That is the normal case for
                // the shared prefixes and nothing to react to.
            }
        }
    }
}
