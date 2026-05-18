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
package org.os890.jawelte.tests.quarkus.scenario53;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
class Scenario53Test {

    @Inject
    @QualifierA
    List<String> a;

    @Inject
    @QualifierB
    List<String> b;

    @Inject
    BeanManager beanManager;

    @Test
    void distinctQualifierTypesOnSameJdkTypeProduceDistinctSyntheticBeans() {
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();

        Type listOfString = new TypeLiteral<List<String>>() { }.getType();

        Set<Bean<?>> beansForA = beanManager.getBeans(listOfString, new QualifierALiteral());
        Set<Bean<?>> beansForB = beanManager.getBeans(listOfString, new QualifierBLiteral());

        assertThat(beansForA).hasSize(1);
        assertThat(beansForB).hasSize(1);
        assertThat(beansForA.iterator().next())
                .isNotEqualTo(beansForB.iterator().next());
    }

    private static class QualifierALiteral
            extends AnnotationLiteral<QualifierA> implements QualifierA {

        private static final long serialVersionUID = 1L;
    }

    private static class QualifierBLiteral
            extends AnnotationLiteral<QualifierB> implements QualifierB {

        private static final long serialVersionUID = 1L;
    }
}
