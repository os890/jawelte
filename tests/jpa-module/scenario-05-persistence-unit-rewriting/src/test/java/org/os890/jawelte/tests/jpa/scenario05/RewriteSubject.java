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
package org.os890.jawelte.tests.jpa.scenario05;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;

/**
 * CDI bean whose only injection point is a {@code @PersistenceUnit} field —
 * no {@code @Inject}. {@code JpaCdiExtension} rewrites the field to
 * {@code @Inject} during {@code ProcessAnnotatedType}, so the EMF is
 * populated by CDI even though the user wrote standard JPA annotations.
 */
@ApplicationScoped
public class RewriteSubject {

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    /** No-arg constructor required by CDI. */
    public RewriteSubject() {
    }

    /** Expose the rewritten field for assertion. */
    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }
}
