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
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
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
    private static final DotName PRODUCES = DotName.createSimple("jakarta.enterprise.inject.Produces");
    private static final DotName OBSERVES = DotName.createSimple("jakarta.enterprise.event.Observes");
    private static final DotName OBSERVES_ASYNC = DotName.createSimple("jakarta.enterprise.event.ObservesAsync");
    private static final DotName DISPOSES = DotName.createSimple("jakarta.enterprise.inject.Disposes");
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
     * Walks every {@code @Inject} annotation in the Jandex index — on
     * fields, on methods (constructor / initializer; the method
     * parameters are the actual injection points), and on method
     * parameters directly. For each candidate target type that is
     * (a) an interface with no known implementor, or (b) a concrete
     * class with no class-level annotations, registers a synthetic
     * Mockito-backed bean.
     *
     * <p>Only fires when at least one {@code @EnableTestBeans} class
     * is present in the index — the build-step stays a no-op in
     * non-test Quarkus apps.
     *
     * @param indexItem      the combined index produced by Quarkus's
     *                       core processor
     * @param syntheticBeans build-item producer for the synthetic
     *                       mock beans this step contributes
     */
    @BuildStep
    void autoMockUnsatisfiedInjectionPoints(
            CombinedIndexBuildItem indexItem,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        IndexView index = indexItem.getIndex();
        if (index.getAnnotations(ENABLE_TEST_BEANS).isEmpty()) {
            return;
        }
        Set<DotName> alreadyHandled = new LinkedHashSet<>();
        for (AnnotationInstance injectAnnotation : index.getAnnotations(INJECT)) {
            AnnotationTarget target = injectAnnotation.target();
            switch (target.kind()) {
                case FIELD -> processCandidateType(
                        target.asField().type(), index, alreadyHandled, syntheticBeans);
                case METHOD -> {
                    MethodInfo method = target.asMethod();
                    for (Type parameterType : method.parameterTypes()) {
                        processCandidateType(parameterType, index, alreadyHandled, syntheticBeans);
                    }
                }
                case METHOD_PARAMETER -> {
                    MethodInfo enclosing = target.asMethodParameter().method();
                    int position = target.asMethodParameter().position();
                    processCandidateType(
                            enclosing.parameterTypes().get(position),
                            index, alreadyHandled, syntheticBeans);
                }
                default -> {
                    // Skip class-level / record-component / type-use targets;
                    // those aren't injection points.
                }
            }
        }
        // @Produces methods: every parameter is an implicit injection point.
        for (AnnotationInstance produces : index.getAnnotations(PRODUCES)) {
            if (produces.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo method = produces.target().asMethod();
            for (Type parameterType : method.parameterTypes()) {
                processCandidateType(parameterType, index, alreadyHandled, syntheticBeans);
            }
        }
        // @Observes / @ObservesAsync: every NON-event parameter on the same
        // method is an implicit injection point. @Disposes follows the same
        // pattern for the non-@Disposes parameters.
        processSiblingParameters(index, alreadyHandled, syntheticBeans, OBSERVES);
        processSiblingParameters(index, alreadyHandled, syntheticBeans, OBSERVES_ASYNC);
        processSiblingParameters(index, alreadyHandled, syntheticBeans, DISPOSES);
    }

    private static void processSiblingParameters(
            IndexView index,
            Set<DotName> alreadyHandled,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            DotName eventAnnotation) {
        for (AnnotationInstance instance : index.getAnnotations(eventAnnotation)) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD_PARAMETER) {
                continue;
            }
            MethodInfo method = instance.target().asMethodParameter().method();
            int eventPosition = instance.target().asMethodParameter().position();
            java.util.List<Type> parameters = method.parameterTypes();
            for (int i = 0; i < parameters.size(); i++) {
                if (i == eventPosition) {
                    continue;
                }
                processCandidateType(parameters.get(i), index, alreadyHandled, syntheticBeans);
            }
        }
    }

    private static void processCandidateType(
            Type candidate,
            IndexView index,
            Set<DotName> alreadyHandled,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        DotName name = candidate.name();
        if (!alreadyHandled.add(name)) {
            return;
        }
        String packageName = name.packagePrefix();
        if (packageName != null
                && (packageName.startsWith("java.")
                        || packageName.startsWith("jakarta.")
                        || packageName.startsWith("io.quarkus.")
                        || packageName.startsWith("org.jboss.")
                        || packageName.startsWith("org.eclipse.microprofile."))) {
            // Skip JDK / framework types — Quarkus or jakarta supply
            // those, jawelte's auto-mock applies only to user code.
            return;
        }
        ClassInfo target = index.getClassByName(name);
        if (target == null) {
            return;
        }
        if (Modifier.isInterface(target.flags())) {
            if (!index.getAllKnownImplementors(name).isEmpty()) {
                return;
            }
        } else {
            if (!target.declaredAnnotations().isEmpty()) {
                return;
            }
        }
        syntheticBeans.produce(
                SyntheticBeanBuildItem.configure(name)
                        .types(candidate)
                        .scope(Singleton.class)
                        .creator(MockBeanCreator.class)
                        .param(MockBeanCreator.TYPE_NAME_PARAM, name.toString())
                        .unremovable()
                        .done());
    }
}
