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
package org.os890.jawelte.tests.jndi.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jndi.api.port.JndiContextProvider;

/**
 * The property this module exists for.
 *
 * <p>Installing an in-process naming provider installs a <em>fresh</em>
 * writable root. Two modules each doing that independently would each
 * guard it with their own flag, and whichever ran second would replace
 * the root — silently discarding everything the first had bound. That
 * is not hypothetical: jta-module binds the transaction artifacts and
 * datasource-module binds the declared data sources, so before the
 * install was shared they would have taken turns erasing each other
 * depending on boot order.
 *
 * <p>Resolving the port twice stands in for those two modules. What has
 * to hold is that the second resolution sees the first one's binding.
 *
 * <p>No CDI container is booted here: {@code TestContext.loadService}
 * falls back to reflective instantiation when none is running, so the
 * naming tree is usable outside a container too. The CDI API types are
 * still required on the classpath, because that fallback is reached by
 * attempting {@code CDI.current()} first.
 */
class Scenario01Test {

    @Test
    void resolvingThePortTwiceYieldsTheSameRoot() {
        JndiContextProvider first = TestContext.loadService(JndiContextProvider.class);
        JndiContextProvider second = TestContext.loadService(JndiContextProvider.class);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.writableRoot())
                .as("two resolutions must share one root, or the second install "
                        + "would discard the first one's bindings")
                .isSameAs(second.writableRoot());
    }

    @Test
    void aBindingSurvivesASecondResolution() throws NamingException {
        Object payload = new Object();

        Context boundThrough = TestContext.loadService(JndiContextProvider.class).writableRoot();
        ensureSubcontext(boundThrough, "java:comp");
        boundThrough.rebind("java:comp/scenario01", payload);

        Context resolvedLater = TestContext.loadService(JndiContextProvider.class).writableRoot();

        assertThat(resolvedLater.lookup("java:comp/scenario01")).isSameAs(payload);
    }

    @Test
    void aBoundEntryIsVisibleThroughAPlainInitialContext() throws NamingException {
        Object payload = new Object();

        Context root = TestContext.loadService(JndiContextProvider.class).writableRoot();
        ensureSubcontext(root, "java:comp");
        root.rebind("java:comp/scenario01-visible", payload);

        assertThat(new InitialContext().lookup("java:comp/scenario01-visible"))
                .as("binding through the port must be reachable by the standard lookup "
                        + "any consumer performs")
                .isSameAs(payload);
    }

    /**
     * Create a sub-context unless it is already there.
     *
     * <p>The tolerance is the point rather than a convenience: the root
     * outlives an individual test method, so whichever method runs
     * second finds {@code java:comp} already created by the first. That
     * is the same situation two binding modules are in, and the same
     * thing both production binders do about it.
     */
    private static void ensureSubcontext(Context root, String name) throws NamingException {
        try {
            root.createSubcontext(name);
        } catch (NameAlreadyBoundException alreadyThere) {
            // Another test method — or, in production, another module —
            // got here first. Nothing to do.
        }
    }
}
