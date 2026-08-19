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
package org.os890.jawelte.tests.cdi.scenario68;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @Any} must never split one auto-mock into two.
 *
 * <p>Every bean holds {@code @Any} implicitly, so it never narrows a
 * resolution: {@code @Inject Foo}, {@code @Inject @Any Foo} and
 * {@code @Inject @Default @Any Foo} are one request, and the container
 * satisfies all three from one bean. The auto-mock key used to disagree
 * — it kept {@code @Any} as if it distinguished — so those spellings
 * produced two and three keys, two and three synthetic beans, and the
 * deployment failed with {@code AmbiguousResolutionException} on
 * OpenWebBeans and {@code WELD-001409} on Weld.
 *
 * <p>This is the remainder of issue 155. That fix taught the reflective
 * walk over the test class to key an unqualified {@code @Inject} as
 * {@code @Default}; it did not address {@code @Any}, which reproduced
 * the identical failure as soon as anyone wrote the annotation out.
 *
 * <p>The last test is the one that keeps the fix honest. Collapsing
 * {@code @Any} must not slide into collapsing everything, so a real
 * qualifier still has to produce a separate mock.
 */
@EnableTestBeans
class Scenario68Test {

    @Inject
    private OrderService orderService;

    @Inject
    private AnyInjectingService anyInjectingService;

    @Inject
    @Any
    private PricingService anyOnTheTestClass;

    @Inject
    @Default
    @Any
    private PricingService defaultAndAnyOnTheTestClass;

    @Test
    void anyOnTheTestClassResolvesToTheBeansMock() {
        assertThat(anyOnTheTestClass)
                .as("@Any adds nothing to a request, so the test class's field and the bean's "
                        + "plain @Inject are one key and share one synthetic bean")
                .isSameAs(orderService.collaborator());
    }

    @Test
    void defaultAndAnyTogetherResolveToTheSameMock() {
        assertThat(defaultAndAnyOnTheTestClass)
                .as("writing both out is still the same request; dropping @Any leaves @Default, "
                        + "which is what the container reports for the bean's field")
                .isSameAs(orderService.collaborator());
    }

    @Test
    void anyOnABeanFieldResolvesToTheSameMock() {
        assertThat(anyInjectingService.collaborator())
                .as("the normalization is not a test-class special case - it holds on the bean "
                        + "side too, where the qualifiers come from ProcessInjectionPoint")
                .isSameAs(orderService.collaborator());
    }
}
