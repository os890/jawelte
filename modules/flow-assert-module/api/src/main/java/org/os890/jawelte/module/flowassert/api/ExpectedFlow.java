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
package org.os890.jawelte.module.flowassert.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asserts the flow recorded by the annotated test method against an
 * expected sequence-diagram, right after the method returns and
 * before any other module tears its state down.
 *
 * <p>The expected file's extension decides the notation: {@code .mmd}
 * is compared as Mermaid, {@code .puml} as PlantUML, and any other
 * extension a registered
 * {@link org.os890.jawelte.module.flowassert.api.port.FlowDialect}
 * claims is compared by that dialect. The recorded side is rendered
 * in whichever notation the expected side is written in.
 *
 * <p>What is compared is the <em>combined</em> diagram of the test
 * method: one block per outermost call, in the order the calls
 * happened, sharing the participant lanes — the same rendering the
 * recorder writes as {@code use-case.mmd} for a labelled use-case.
 * A method that made a single outermost call therefore has a
 * single-block expected file.
 *
 * <p>Equivalent to calling
 * {@code FlowDiff.forRecordedFlows().expected(...).assertEquals()} at
 * the end of the method; use {@link FlowDiff} directly when the
 * assertion needs options, a single chain, or has to run in the
 * middle of a test.
 *
 * <pre>{@code
 * @Test
 * @ExpectedFlow                                   // flows/OrderServiceFlowTest/placesOrder.mmd
 * void placesOrder() {
 *     orderService.placeOrder("SKU-1", 2);
 * }
 *
 * @Test
 * @ExpectedFlow(value = "flows/checkout.puml", ignoring = "AuditService#log(*)")
 * void checksOut() {
 *     checkoutService.checkout("SKU-1", 2);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExpectedFlow {

    /**
     * The expected diagram as a classpath resource. Empty (the
     * default) resolves by convention:
     * {@code <base>/<TestClassSimpleName>/<methodName><extension>},
     * with the base directory taken from
     * {@value FlowAssertConfig#EXPECTED_BASE_DIRECTORY_KEY} and the
     * extension probed across every registered dialect, in priority
     * order. A convention lookup that finds nothing fails with the
     * list of resources it probed.
     *
     * @return the classpath resource, or {@code ""} for the convention
     */
    String value() default "";

    /**
     * Call patterns to leave out of the comparison, in the
     * {@code Participant#signature} form
     * {@link FlowDiff.Builder#ignoring(String...)} documents.
     *
     * @return the ignore patterns; never {@code null}
     */
    String[] ignoring() default {};
}
