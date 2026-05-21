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
package example.txscoped;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
class PerTxBeanTest {

    @Inject
    PerTxBeanReader reader;

    @Test
    void eachJtaTransactionGetsItsOwnInstance() {
        PerTxBean.PRE_DESTROY_COUNT.set(0);

        String firstTxId = reader.readIdInsideJtaTx();
        String secondTxId = reader.readIdInsideJtaTx();

        assertThat(firstTxId).isNotBlank();
        assertThat(secondTxId).isNotBlank().isNotEqualTo(firstTxId);
        assertThat(PerTxBean.PRE_DESTROY_COUNT.get())
                .as("@PreDestroy fired once for the first tx and once for the second")
                .isEqualTo(2);
    }
}
