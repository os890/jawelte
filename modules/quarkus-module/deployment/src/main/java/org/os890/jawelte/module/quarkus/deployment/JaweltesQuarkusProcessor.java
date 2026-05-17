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
package org.os890.jawelte.module.quarkus.deployment;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.os890.jawelte.module.quarkus.runtime.MockBeanCreator;

import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build-time processor for the jawelte Quarkus extension. Hosts the
 * {@code @BuildStep} methods that replace cdi-module's portable
 * {@code TestBeansCdiExtension} under {@code @QuarkusTest}: discovery of
 * {@code @EnableTestBeans} configuration, resolution of the
 * {@code @TestBean} alternative set, registration of synthetic mock
 * beans for unsatisfied injection points, and annotation
 * transformations that add {@code @Priority} plus a fallback
 * {@code @Singleton} to selected alternatives.
 *
 * <p>The first iteration covers the scenario-01 case: an
 * {@code @EnableTestBeans} test class with an {@code @Inject} field
 * typed by an interface that has no implementor in the Jandex index.
 * Each such interface gets a {@code SyntheticBeanBuildItem} backed by
 * {@link MockBeanCreator}; the runtime creator returns a fresh Mockito
 * mock per bean activation.
 */
public class JaweltesQuarkusProcessor {

    private static final String FEATURE = "jawelte-quarkus";
    private static final DotName INJECT = DotName.createSimple("jakarta.inject.Inject");
    private static final DotName ENABLE_TEST_BEANS =
            DotName.createSimple("org.os890.jawelte.core.api.EnableTestBeans");

    /** Default constructor used by the Quarkus build framework. */
    public JaweltesQuarkusProcessor() {
    }

    /**
     * Registers the extension's feature label so the build report
     * shows {@code jawelte-quarkus} as one of the active features.
     *
     * @return the feature build item identifying this extension
     */
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Scans every {@code @EnableTestBeans}-annotated class in the
     * Jandex index, walks its {@code @Inject} fields, and registers a
     * synthetic Mockito-backed bean for every field whose declared
     * type is an interface with no known implementor. Mirrors the
     * portion of cdi-module's {@code TestBeansCdiExtension} that
     * synthesises auto-mock beans at {@code AfterBeanDiscovery} time.
     *
     * @param indexItem      the combined index produced by Quarkus's
     *                       core processor; carries every class on
     *                       the build classpath
     * @param syntheticBeans build-item producer for the synthetic
     *                       mock beans this step contributes
     */
    @BuildStep
    void autoMockUnsatisfiedInterfaces(
            CombinedIndexBuildItem indexItem,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        IndexView index = indexItem.getIndex();
        Set<DotName> alreadyHandled = new LinkedHashSet<>();
        for (AnnotationInstance enableAnnotation : index.getAnnotations(ENABLE_TEST_BEANS)) {
            if (enableAnnotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo testClass = enableAnnotation.target().asClass();
            ClassInfo current = testClass;
            while (current != null && !current.name().equals(DotName.OBJECT_NAME)) {
                for (FieldInfo field : current.fields()) {
                    if (field.annotation(INJECT) == null) {
                        continue;
                    }
                    Type fieldType = field.type();
                    DotName fieldTypeName = fieldType.name();
                    if (!alreadyHandled.add(fieldTypeName)) {
                        continue;
                    }
                    ClassInfo target = index.getClassByName(fieldTypeName);
                    if (target == null) {
                        continue;
                    }
                    if (Modifier.isInterface(target.flags())) {
                        Collection<ClassInfo> impls = index.getAllKnownImplementors(fieldTypeName);
                        if (!impls.isEmpty()) {
                            continue;
                        }
                    } else {
                        if (!target.declaredAnnotations().isEmpty()) {
                            // Concrete class with class-level annotations is
                            // likely a managed bean — let Quarkus resolve it.
                            // Auto-mock applies only to fully unmanaged
                            // concrete classes.
                            continue;
                        }
                    }
                    syntheticBeans.produce(
                            SyntheticBeanBuildItem.configure(fieldTypeName)
                                    .types(fieldType)
                                    .scope(Singleton.class)
                                    .creator(MockBeanCreator.class)
                                    .param(MockBeanCreator.TYPE_NAME_PARAM, fieldTypeName.toString())
                                    .unremovable()
                                    .done());
                }
                DotName superName = current.superName();
                if (superName == null) {
                    break;
                }
                current = index.getClassByName(superName);
            }
        }
    }
}
