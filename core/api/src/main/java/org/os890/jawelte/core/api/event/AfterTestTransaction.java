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
package org.os890.jawelte.core.api.event;

/**
 * CDI event fired after a test transaction completes. Defined here as
 * a contract; the standard implementation lives in the JPA-related
 * module. If no transaction-managing module is on the classpath, this
 * event is never fired.
 */
public class AfterTestTransaction {

    private final boolean committed;
    private final String testMethodName;

    /**
     * Construct an {@code AfterTestTransaction} event.
     *
     * @param committed      whether the transaction was committed
     *                       ({@code true}) or rolled back ({@code false})
     * @param testMethodName the simple name of the test method
     */
    public AfterTestTransaction(boolean committed, String testMethodName) {
        this.committed = committed;
        this.testMethodName = testMethodName;
    }

    /**
     * Whether the transaction was committed.
     *
     * @return {@code true} if committed, {@code false} if rolled back
     */
    public boolean isCommitted() {
        return committed;
    }

    /**
     * Get the simple name of the test method.
     *
     * @return the test method name
     */
    public String getTestMethodName() {
        return testMethodName;
    }
}
