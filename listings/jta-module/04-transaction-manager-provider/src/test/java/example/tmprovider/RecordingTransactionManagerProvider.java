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
package example.tmprovider;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Custom TransactionManagerProvider for Geronimo. Records every
 * create() call so the test can prove this provider — not the default
 * AutoSelect one — is the active impl. The default is registered with
 * @Priority(Integer.MAX_VALUE); this one carries @Priority(1) so it
 * wins the priority sort.
 *
 * <p>Construction goes through reflection because Geronimo's TM class
 * is on the test classpath but not compile-visible from a listing
 * that doesn't depend on jta-module/impl's internal types.
 */
@Priority(1)
public class RecordingTransactionManagerProvider implements TransactionManagerProvider {

    public static final AtomicInteger CREATE_COUNT = new AtomicInteger();

    private static volatile TransactionManager cachedTm;

    private static final Object LOCK = new Object();

    @Override
    public String name() {
        return "recording-geronimo";
    }

    @Override
    public boolean isAvailable() {
        return loadable("org.apache.geronimo.transaction.manager.GeronimoTransactionManager");
    }

    @Override
    public TransactionManager create() {
        CREATE_COUNT.incrementAndGet();
        TransactionManager local = cachedTm;
        if (local != null) {
            return local;
        }
        synchronized (LOCK) {
            if (cachedTm != null) {
                return cachedTm;
            }
            try {
                Class<?> tmClass = Class.forName(
                        "org.apache.geronimo.transaction.manager.GeronimoTransactionManager");
                cachedTm = (TransactionManager) tmClass
                        .getDeclaredConstructor(int.class)
                        .newInstance(60);
                return cachedTm;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to build Geronimo TM", e);
            }
        }
    }

    @Override
    public UserTransaction userTransaction() {
        TransactionManager tm = create();
        try {
            Class<?> utClass = Class.forName(
                    "org.apache.geronimo.transaction.GeronimoUserTransaction");
            return (UserTransaction) utClass
                    .getDeclaredConstructor(TransactionManager.class)
                    .newInstance(tm);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to build Geronimo UserTransaction", e);
        }
    }

    @Override
    public TransactionSynchronizationRegistry transactionSynchronizationRegistry() {
        // Geronimo's TM also implements TSR.
        return (TransactionSynchronizationRegistry) create();
    }

    @Override
    public void shutdown() {
        cachedTm = null;
    }

    private static boolean loadable(String fqcn) {
        try {
            Class.forName(fqcn);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
