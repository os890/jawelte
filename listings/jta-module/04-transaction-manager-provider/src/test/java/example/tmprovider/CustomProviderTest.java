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

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Touching UserTransaction is enough to force jta-module's strategy
 * to materialise a TransactionManager — which routes through the
 * active TransactionManagerProvider. The custom provider's
 * @Priority(1) beats the default AutoSelect provider
 * (@Priority(Integer.MAX_VALUE)) on the priority sort, so the
 * CREATE_COUNT counter increments.
 */
@EnableTestBeans
class CustomProviderTest {

    @Inject
    UserTransaction userTransaction;

    @Test
    void customProviderProducedTheTransactionManager() throws Exception {
        userTransaction.begin();
        userTransaction.commit();

        assertThat(RecordingTransactionManagerProvider.CREATE_COUNT.get())
                .as("the custom provider's create() was invoked at least once during TM resolution")
                .isPositive();
    }
}
