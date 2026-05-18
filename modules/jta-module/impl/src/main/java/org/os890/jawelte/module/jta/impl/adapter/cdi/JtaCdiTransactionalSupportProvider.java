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
package org.os890.jawelte.module.jta.impl.adapter.cdi;

import java.util.ServiceLoader;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jpa.api.port.CdiTransactionalSupportProvider;

/**
 * jta-module's {@link CdiTransactionalSupportProvider}. Reports
 * {@code true} when a vendor JTA CDI integration is on the classpath
 * that ships its own {@code @Transactional} interceptor and
 * {@code @TransactionScoped} {@code Context} (Narayana today, Quarkus
 * later — its embedded Narayana classes match the same probe).
 *
 * <p>{@code @Priority(Integer.MAX_VALUE - 100)} — wins over
 * jpa-module/impl's default ({@code @Priority(Integer.MAX_VALUE)})
 * whenever {@code jta-module} is on the classpath, regardless of which
 * vendor TM impl is selected. When no vendor CDI integration is
 * present (lean Narayana {@code jta} jar, plain Geronimo, etc.) both
 * methods return {@code false} and jpa-module hosts the CDI machinery
 * itself, exactly as it would without jta-module on the classpath.
 *
 * <p>Probed via {@link Class#forName(String, boolean, ClassLoader)}
 * against the TCCL — no compile-time dependency on Narayana.
 */
@Priority(Integer.MAX_VALUE - 100)
public class JtaCdiTransactionalSupportProvider implements CdiTransactionalSupportProvider {

    /**
     * Marker class shipped by Narayana's CDI integration (the
     * {@code com.arjuna.ats.jta.cdi.*} package, present only in the
     * uber {@code narayana-jta} artifact). Quarkus embeds the same
     * classes, so the same probe lights up under
     * {@code quarkus-arc-module} once TICKET-015 lands.
     */
    private static final String NARAYANA_CDI_EXTENSION_CLASS =
            "com.arjuna.ats.jta.cdi.TransactionExtension";

    /**
     * Marker class for Quarkus ArC. Narayana's
     * {@code TransactionalInterceptorBase.getTransactional(...)} probes
     * for Weld at runtime and throws
     * {@code ARJUNA016151: Not supported for interception factory with
     * non-weld CDI implementation} when the interception target is
     * built by anything else. Under ArC we therefore report
     * {@code false} from both probes so jpa-module/impl hosts the
     * {@code @Transactional} interceptor and {@code @TransactionScoped}
     * context itself, regardless of whether Narayana's uber jar is on
     * the classpath.
     */
    private static final String ARC_MARKER_CLASS = "io.quarkus.arc.Arc";

    /** No-arg constructor required by {@link ServiceLoader}. */
    public JtaCdiTransactionalSupportProvider() {
    }

    @Override
    public boolean platformProvidesTransactionalInterceptor() {
        if (classExists(ARC_MARKER_CLASS)) {
            return false;
        }
        return classExists(NARAYANA_CDI_EXTENSION_CLASS);
    }

    @Override
    public boolean platformProvidesTransactionScopedContext() {
        if (classExists(ARC_MARKER_CLASS)) {
            return false;
        }
        return classExists(NARAYANA_CDI_EXTENSION_CLASS);
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException notPresent) {
            return false;
        }
    }
}
