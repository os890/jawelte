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
package org.os890.jawelte.tests.jndi.scenario03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;
import javax.naming.spi.NamingManager;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jndi.api.port.JndiContextProvider;

/**
 * The naming tree hands back the object that was bound.
 *
 * <p>xbean's convenience constructors leave {@code supportReferenceable}
 * on, and with it {@code WritableContext.addBinding} does not store the
 * value it was given: when that value is {@link javax.naming.Referenceable}
 * and yields a non-null {@link javax.naming.Reference}, the reference is
 * stored <em>instead of</em> the object, and every later lookup rebuilds
 * a fresh instance from it. The substitution happens at bind time, not at
 * lookup — the lookup is merely where the consequence becomes visible.
 *
 * <p>Consequences, all asserted below: two lookups of one name disagree,
 * a lookup never returns the instance that was bound, and anything
 * holding state behind its interface gets one copy of that state per
 * lookup. A pooled data source would be one pool per lookup, so a test
 * could observe a database the deployed application never writes to. An
 * EE container's tree behaves the other way round: what was deployed is
 * what resolves.
 *
 * <p>No CDI container is booted here — {@code TestContext.loadService}
 * falls back to reflective instantiation when none is running.
 */
class Scenario03Test {

    @Test
    void aReferenceableBindingIsReturnedAsTheObjectThatWasBound() throws NamingException {
        ReferenceablePayload bound = new ReferenceablePayload("same-instance");
        Context root = writableRoot();
        bind(root, "java:comp/scenario03-same", bound);

        assertThat(root.lookup("java:comp/scenario03-same"))
                .as("a Referenceable must not be replaced by a rebuild of itself")
                .isSameAs(bound);
    }

    @Test
    void twoLookupsOfAReferenceableBindingAgree() throws NamingException {
        ReferenceablePayload bound = new ReferenceablePayload("stable-across-lookups");
        Context root = writableRoot();
        bind(root, "java:comp/scenario03-stable", bound);

        Object first = root.lookup("java:comp/scenario03-stable");
        Object second = root.lookup("java:comp/scenario03-stable");

        assertThat(first)
                .as("per-lookup reconstruction would mean per-lookup state — one "
                        + "connection pool per caller, for a pooled data source")
                .isSameAs(second);
    }

    @Test
    void theSameHoldsThroughAPlainInitialContext() throws NamingException {
        ReferenceablePayload bound = new ReferenceablePayload("through-initial-context");
        bind(writableRoot(), "java:comp/scenario03-initial", bound);

        assertThat(new InitialContext().lookup("java:comp/scenario03-initial"))
                .as("the lookup an application actually performs must agree too")
                .isSameAs(bound);
    }

    @Test
    void aPlainBindingIsReturnedAsTheObjectThatWasBound() throws NamingException {
        Object bound = new Object();
        Context root = writableRoot();
        bind(root, "java:comp/scenario03-plain", bound);

        assertThat(root.lookup("java:comp/scenario03-plain"))
                .as("stating the whole rule, not only the interesting half")
                .isSameAs(bound);
    }

    /**
     * Guards the guard: the payload's reference really is reconstructible,
     * so the assertions above are not passing merely because
     * {@code NamingManager} had no factory to call and handed the original
     * back for want of an alternative.
     */
    @Test
    void theReconstructionPathIsGenuinelyAvailable() throws Exception {
        ReferenceablePayload original = new ReferenceablePayload("reconstructible");

        Object rebuilt = NamingManager.getObjectInstance(
                original.getReference(), null, null, new Hashtable<>());

        assertThat(rebuilt)
                .as("if this were not a working reference, the identity assertions "
                        + "above would prove nothing")
                .isInstanceOf(ReferenceablePayload.class)
                .isNotSameAs(original);
        assertThat(((ReferenceablePayload) rebuilt).id()).isEqualTo(original.id());
    }

    private static Context writableRoot() {
        return TestContext.loadService(JndiContextProvider.class).writableRoot();
    }

    /**
     * Bind under {@code java:comp}, creating that sub-context if this is
     * the first test method to need it — the root outlives an individual
     * method, so whichever runs second finds it already there.
     */
    private static void bind(Context root, String name, Object value) throws NamingException {
        try {
            root.createSubcontext("java:comp");
        } catch (NameAlreadyBoundException alreadyThere) {
            // Another test method got here first. Nothing to do.
        }
        root.rebind(name, value);
    }
}
