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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Records the CDI call-flow of every test method of the annotated
 * class. Meta-annotated with {@link EnableTestBeans}, so it boots the
 * jawelte lifecycle on its own — exactly like
 * {@code @EnableWireMock} and {@code @EnableJaxRs}.
 *
 * <p>Its attributes are translated into the {@code cdi-flow.*}
 * MicroProfile Config keys the recorder reads while the container
 * boots. Without this annotation the recorder is switched off
 * ({@code cdi-flow.enabled=false}), so putting the recorder on the
 * classpath never instruments a test class that did not ask for it.
 *
 * <p>A {@code -Dcdi-flow.…} system property still outranks whatever
 * is derived here: the config source contributing these values sits
 * at ordinal 250, below the system-property source.
 *
 * <pre>{@code
 * @EnableFlowAssert(include = "com\\.acme\\.order\\..*")
 * class OrderServiceFlowTest {
 *     @Inject
 *     private OrderService orderService;
 *
 *     @Test
 *     @ExpectedFlow
 *     void placesOrder() {
 *         orderService.placeOrder("SKU-1", 2);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@EnableTestBeans
public @interface EnableFlowAssert {

    /**
     * Regular expression on the fully qualified bean class name;
     * only matching beans are recorded. Empty (the default) records
     * every bean the container can instrument, which on a test
     * classpath is usually more than a diagram wants to show.
     *
     * <p>Maps to {@code cdi-flow.include-pattern}.
     *
     * @return the include pattern, or {@code ""} for "no restriction"
     */
    String include() default "";

    /**
     * Regular expressions that veto a bean again, whatever selected
     * it. Cumulative with the project-wide default list configured
     * under {@value FlowAssertConfig#EXCLUDE_DEFAULTS_KEY} — the two
     * are joined into one alternation, so the framework's own beans
     * stay out of every recording without the test repeating them.
     *
     * <p>Maps to {@code cdi-flow.exclude-pattern}.
     *
     * @return additional exclude patterns; never {@code null}
     */
    String[] exclude() default {};

    /**
     * CDI stereotypes whose beans are recorded, as an alternative to
     * {@link #include()} — a bean qualifies as soon as one of the two
     * selects it. Stereotypes are resolved transitively by the
     * recorder, so a stacked stereotype counts.
     *
     * <p>Maps to {@code cdi-flow.include-stereotypes}.
     *
     * @return the stereotype annotations to select by; never {@code null}
     */
    Class<? extends Annotation>[] stereotypes() default {};

    /**
     * Whether consecutive identical calls — including their whole
     * sub-tree — are folded into one {@code loop N times} block.
     *
     * <p>Maps to {@code cdi-flow.fold-loops}.
     *
     * @return {@code true} to fold repeated calls (the default)
     */
    boolean foldLoops() default true;

    /**
     * Marks calls taking longer than this many milliseconds as a
     * hotspot. Negative (the default) switches hotspot detection off.
     * Hotspot markers are wall-clock dependent and therefore
     * <em>not</em> compared unless
     * {@link FlowDiff.Builder#comparingHotspots()} asks for it.
     *
     * <p>Maps to {@code cdi-flow.hotspot-threshold-ms}.
     *
     * @return the threshold in milliseconds, or a negative value for "off"
     */
    long hotspotThresholdMillis() default -1;

    /**
     * Whether the test class itself is recorded. Off by default: the
     * test instance is a CDI bean, so recording it would make the
     * test method the entry point of the flow and pull every
     * test-local helper and {@code @TestBean} mock into the diagram.
     * With it off, a flow starts at the first application bean the
     * test calls and the diagram reads {@code caller -> service}.
     *
     * @return {@code true} to record the test class as well
     */
    boolean recordTestClass() default false;

    /**
     * Directory the recorder writes its own diagram files to. Empty
     * (the default) keeps file output off entirely
     * ({@code cdi-flow.write-files=false}) — the flows are captured
     * in memory and rendered on demand by {@link FlowDiff}. Set it
     * while debugging to get the recorder's own files as well.
     *
     * <p>Maps to {@code cdi-flow.output-directory} plus
     * {@code cdi-flow.write-files=true}.
     *
     * @return the output directory, or {@code ""} for "write nothing"
     */
    String writeTo() default "";
}
