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

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import jakarta.transaction.TransactionScoped;

/**
 * @TransactionScoped bean: one instance per JTA transaction. The
 * per-instance UUID lets the test see two transactions produce two
 * different bean instances. The @PreDestroy counter proves the bean
 * is destroyed when the transaction completes.
 */
@TransactionScoped
public class PerTxBean implements Serializable {

    public static final AtomicInteger PRE_DESTROY_COUNT = new AtomicInteger();

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }

    @PreDestroy
    void onPreDestroy() {
        PRE_DESTROY_COUNT.incrementAndGet();
    }
}
