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
package org.os890.jawelte.tests.jta.scenario47;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/**
 * Inner CDI bean called by {@link OuterWriter}. Lives in a separate
 * bean so the {@code @Transactional} interceptor actually fires on
 * the boundary call — an intra-class invocation would bypass the
 * proxy and run as part of the caller's transaction.
 */
@ApplicationScoped
public class InnerReader {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public InnerReader() {
    }

    /**
     * Reads the current item count inside a {@code REQUIRES_NEW}
     * {@code @Transactional @ReadOnly} method. Does not modify
     * anything. The inner level runs in its own JTA transaction; the
     * outer JTA transaction is suspended for the duration of this
     * call and resumed afterwards.
     *
     * @return the row count visible to the inner transaction
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @ReadOnly
    public long readItemCount() {
        return entityManager.createQuery("SELECT COUNT(i) FROM Item i", Long.class)
                .getSingleResult();
    }
}
