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
package org.os890.jawelte.module.jta.impl.adapter.provider;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.ServiceLoader;

import jakarta.annotation.Priority;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Default {@link TransactionManagerProvider} shipped by jta-module:
 * a thin wrapper that probes the classpath at first use and delegates
 * to whichever vendor-specific detail provider is loadable. Mirrors
 * the jpa-module pattern where the default
 * {@code DefaultResourceLocalTransactionStrategy} ships at the lowest
 * priority ({@code Integer.MAX_VALUE}) and users override by shipping
 * an alternative at a lower numeric {@code @Priority}.
 *
 * <p>The auto-select probe order is hard-coded:
 * <strong>Geronimo &gt; Atomikos &gt; Narayana</strong>. Narayana's CDI
 * integration is typically on the classpath anyway (the shipped test
 * matrix bundles {@code narayana-jta} under both profiles for the
 * {@code @Transactional} interceptor and {@code @TransactionScoped}
 * Context); without this preference order the wrapper would always
 * fall onto Narayana even when a Geronimo or Atomikos TM is also
 * present.
 *
 * <h2>Overriding</h2>
 *
 * <p>Consumers force a specific provider by shipping their own
 * {@code META-INF/services/org.os890.jawelte.module.jta.api.port.TransactionManagerProvider}
 * file that names a detail impl (e.g.
 * {@link GeronimoTransactionManagerProvider}). The detail impls carry
 * lower numeric {@code @Priority} values than this wrapper, so the
 * project-wide {@code ServicePriorityResolver} picks them whenever
 * they are explicitly registered. jta-module/impl itself ships
 * <strong>only this wrapper</strong> in its own
 * {@code META-INF/services} file.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — the default-fallback
 * priority. Loses to any concrete provider the user explicitly
 * registers.
 */
@Priority(Integer.MAX_VALUE)
public class AutoSelectTransactionManagerProvider implements TransactionManagerProvider {

    private static final Logger LOG =
            System.getLogger(AutoSelectTransactionManagerProvider.class.getName());

    /**
     * Hard-coded auto-select preference order. Each instance is
     * created up front because every detail impl is reflection-only
     * — its no-arg constructor never touches vendor classes, so
     * holding an instance of {@link GeronimoTransactionManagerProvider}
     * is safe even when Geronimo's runtime jar is absent.
     */
    private static final List<TransactionManagerProvider> CANDIDATES = List.of(
            new GeronimoTransactionManagerProvider(),
            new AtomikosTransactionManagerProvider(),
            new NarayanaTransactionManagerProvider());

    /**
     * JVM-static cache of the chosen delegate. {@link ServiceLoader}
     * returns a fresh wrapper per {@code TestContext.loadService(...)}
     * call (the project's {@code ServicePriorityResolver} pattern), so
     * pinning the delegate at JVM scope avoids re-probing on every
     * lookup and ensures every caller — the strategy's {@code begin()},
     * the {@code StandaloneJtaPlatform} indirection, the
     * {@code userTransaction()} consumer — sees the same delegate.
     */
    private static volatile TransactionManagerProvider chosen;

    private static final Object LOCK = new Object();

    /** No-arg constructor required by {@link ServiceLoader}. */
    public AutoSelectTransactionManagerProvider() {
    }

    @Override
    public boolean isAvailable() {
        TransactionManagerProvider local = chosen;
        if (local != null) {
            return true;
        }
        for (TransactionManagerProvider candidate : CANDIDATES) {
            if (candidate.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TransactionManager create() {
        return ensureChosen().create();
    }

    @Override
    public UserTransaction userTransaction() {
        return ensureChosen().userTransaction();
    }

    @Override
    public TransactionSynchronizationRegistry transactionSynchronizationRegistry() {
        return ensureChosen().transactionSynchronizationRegistry();
    }

    @Override
    public void shutdown() {
        TransactionManagerProvider local;
        synchronized (LOCK) {
            local = chosen;
        }
        if (local == null) {
            return;
        }
        local.shutdown();
    }

    @Override
    public String name() {
        TransactionManagerProvider local = chosen;
        if (local == null) {
            return "AutoSelect";
        }
        return "AutoSelect[" + local.name() + "]";
    }

    private static TransactionManagerProvider ensureChosen() {
        TransactionManagerProvider local = chosen;
        if (local != null) {
            return local;
        }
        synchronized (LOCK) {
            if (chosen != null) {
                return chosen;
            }
            for (TransactionManagerProvider candidate : CANDIDATES) {
                if (candidate.isAvailable()) {
                    chosen = candidate;
                    LOG.log(Level.INFO,
                            "AutoSelectTransactionManagerProvider picked '"
                                    + candidate.name() + "' from the classpath");
                    return candidate;
                }
            }
            throw new IllegalStateException(
                    "AutoSelectTransactionManagerProvider: no detail-impl available "
                            + "on the classpath. Add one of geronimo-transaction, "
                            + "transactions-jta (Atomikos), narayana-jta to the test "
                            + "classpath, or register a custom "
                            + "TransactionManagerProvider via META-INF/services.");
        }
    }
}
