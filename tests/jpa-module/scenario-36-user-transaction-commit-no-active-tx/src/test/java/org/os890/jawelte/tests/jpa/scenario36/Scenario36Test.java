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
package org.os890.jawelte.tests.jpa.scenario36;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code UserTransaction.commit()} called when no transaction is active raises
 * {@link IllegalStateException} (per {@code UserTransactionImpl} contract). The
 * same goes for {@code rollback()}.
 */
@EnableTestBeans
public class Scenario36Test {

    @Inject
    private UserTransaction userTransaction;

    /** No-arg constructor for CDI. */
    public Scenario36Test() {
    }

    /** commit() with no active tx → IllegalStateException. */
    @Test
    public void commitWithoutBeginRaisesIllegalState() {
        assertThatThrownBy(userTransaction::commit)
                .as("UserTransaction.commit() must reject when no transaction is active")
                .isInstanceOf(IllegalStateException.class);
    }

    /** rollback() with no active tx → IllegalStateException. */
    @Test
    public void rollbackWithoutBeginRaisesIllegalState() {
        assertThatThrownBy(userTransaction::rollback)
                .as("UserTransaction.rollback() must reject when no transaction is active")
                .isInstanceOf(IllegalStateException.class);
    }
}
