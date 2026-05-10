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
package org.os890.jawelte.module.jpa.api.port;

/**
 * SPI seam {@code jpa-module} consults to decide whether to host
 * the CDI {@code @Transactional} interceptor binding and the
 * {@code @TransactionScoped} {@code Context} itself, or whether a
 * downstream module / vendor integration already provides them.
 *
 * <p>Resolved through
 * {@code TestContext.loadService(CdiTransactionalSupportProvider.class)}
 * — the project-wide single canonical entry point for prioritised SPI
 * lookups. {@code jpa-module/impl} ships a default impl that returns
 * {@code false} for both methods (jpa-module hosts both); a higher-
 * priority alternative shipped by another module (e.g.
 * {@code jta-module/impl}) can take over when the deployed runtime's
 * own CDI integration brings its own equivalents.
 *
 * <p>The methods are kept narrowly factual: each one reports whether
 * <em>the platform</em> already owns that one piece of CDI machinery.
 * The decision of <em>what to do</em> when the answer is {@code true}
 * (skip our interceptor binding, skip our context registration, veto
 * our interceptor class so it doesn't double-fire) lives in
 * {@code JpaCdiExtension}; the corresponding vendor-side CDI plumbing
 * (synthetic beans, vendor-veto observers, vendor-specific
 * pre-bootstrap) lives in the module that returns {@code true} from
 * the relevant probe.
 *
 * <p>Default implementations return {@code false} so a downstream impl
 * only has to override the methods it actually fulfils.
 *
 * <p>Architectural rule: {@code jpa-module} must not depend on
 * {@code jta-module}; this port is the inverted seam that lets
 * {@code jta-module} (and future {@code quarkus-arc-module}) tell
 * {@code jpa-module} to step aside without {@code jpa-module} ever
 * referencing JTA-specific class names.
 */
public interface CdiTransactionalSupportProvider {

    /**
     * Whether the deployed runtime already provides a CDI
     * {@code @Transactional} interceptor that competes with the one
     * {@code jpa-module/impl} otherwise registers. When {@code true},
     * {@code JpaCdiExtension} skips its own
     * {@code addInterceptorBinding(Transactional.class)} and the
     * downstream module is responsible for vetoing
     * {@code TransactionalInterceptor} so the two don't double-fire.
     *
     * @return {@code true} if the platform already provides
     *         {@code @Transactional} interception; {@code false}
     *         otherwise (the default — jpa-module hosts it)
     */
    default boolean platformProvidesTransactionalInterceptor() {
        return false;
    }

    /**
     * Whether the deployed runtime already provides a CDI
     * {@code @TransactionScoped} {@code Context}. When {@code true},
     * {@code JpaCdiExtension} skips its own
     * {@code AfterBeanDiscovery.addContext(...)} call so the two
     * contexts don't compete for {@code @TransactionScoped} bean
     * resolution.
     *
     * @return {@code true} if the platform already provides
     *         {@code @TransactionScoped}; {@code false} otherwise
     *         (the default — jpa-module hosts it)
     */
    default boolean platformProvidesTransactionScopedContext() {
        return false;
    }
}
