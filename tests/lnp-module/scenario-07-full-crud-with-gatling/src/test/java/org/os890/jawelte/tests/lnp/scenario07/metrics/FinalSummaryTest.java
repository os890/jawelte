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
package org.os890.jawelte.tests.lnp.scenario07.metrics;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Sentinel test class scheduled to run last (via JUnit's class-order
 * SPI hook in junit-platform.properties) so it can flush the
 * aggregated metrics table from {@link PerformanceExtension} once
 * every CRUD subclass has finished. Without this hook the summary
 * never prints because no other extension fires after the final
 * scenario class' afterAll.
 */
@Order(Integer.MAX_VALUE)
public class FinalSummaryTest {

    /** Default constructor for JUnit. */
    public FinalSummaryTest() {
    }

    /** Renders the aggregated LNP performance table to System.out. */
    @Test
    public void printPerformanceSummary() {
        PerformanceExtension.printFinalSummary();
    }
}
