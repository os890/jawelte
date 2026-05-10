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
package org.os890.jawelte.tests.jta.scenario32;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 36 — calling
 * {@link UserTransaction#commit()} when no transaction is active
 * throws an {@link IllegalStateException} (the standard JTA
 * contract). Under jpa-module's RESOURCE_LOCAL the same call
 * raised {@code IllegalStateException} via the strategy's "no
 * active tx" guard; the JTA implementations expose the same
 * behaviour because their {@code TM.commit()} already throws
 * {@code IllegalStateException} when {@code Status.STATUS_NO_TRANSACTION}
 * is reported.
 */
@EnableTestBeans
public class Scenario32Test {

    @Inject
    private UserTransaction userTransaction;

    /** No-arg constructor for CDI. */
    public Scenario32Test() {
    }

    @Test
    public void commitWithoutBeginThrows() {
        assertThatThrownBy(userTransaction::commit)
                .as("UserTransaction.commit() outside an active tx must raise IllegalStateException")
                .isInstanceOf(IllegalStateException.class);
    }
}
