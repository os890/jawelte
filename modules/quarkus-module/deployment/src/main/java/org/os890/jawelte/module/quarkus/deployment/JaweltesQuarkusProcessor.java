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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.os890.jawelte.module.quarkus.runtime.MockBeanCreator;

import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
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
    private static final DotName QUALIFIER = DotName.createSimple("jakarta.inject.Qualifier");
    private static final DotName NAMED = DotName.createSimple("jakarta.inject.Named");
    private static final DotName DEFAULT_QUALIFIER = DotName.createSimple("jakarta.enterprise.inject.Default");
    private static final DotName ANY_QUALIFIER = DotName.createSimple("jakarta.enterprise.inject.Any");
    private static final DotName TEST_BEAN = DotName.createSimple("org.os890.jawelte.core.api.TestBean");
    private static final DotName TEST_BEANS_CONTAINER = DotName.createSimple("org.os890.jawelte.core.api.TestBeans");
    private static final DotName PRIORITY = DotName.createSimple("jakarta.annotation.Priority");
    private static final DotName ALTERNATIVE = DotName.createSimple("jakarta.enterprise.inject.Alternative");
    private static final DotName DEPENDENT = DotName.createSimple("jakarta.enterprise.context.Dependent");
    private static final DotName[] SCOPE_ANNOTATIONS = {
        DEPENDENT,
        DotName.createSimple("jakarta.inject.Singleton"),
        DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"),
        DotName.createSimple("jakarta.enterprise.context.RequestScoped"),
        DotName.createSimple("jakarta.enterprise.context.SessionScoped"),
        DotName.createSimple("jakarta.enterprise.context.ConversationScoped"),
    };
    private static final int TEST_BEAN_PRIORITY = Integer.MAX_VALUE;

    private static final Set<DotName> WRAPPER_TYPES = Set.of(
            DotName.createSimple("jakarta.inject.Provider"),
            DotName.createSimple("jakarta.enterprise.inject.Instance"));

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
     * Activates {@code @TestBean(bean=X)} alternatives by adding a
     * {@code @Priority(Integer.MAX_VALUE)} annotation to each declared
     * target class via Quarkus's {@link AnnotationsTransformer}. The
     * target must already carry {@code @Alternative}; otherwise the
     * registration is a silent no-op (matches cdi-module's scenario-35
     * semantics).
     *
     * @param indexItem the combined index produced by Quarkus's core processor
     * @return an annotations-transformer build item that selectively
     *         adds {@code @Priority} to the {@code @TestBean} targets
     */
    @BuildStep
    AnnotationsTransformerBuildItem activateTestBeanAlternatives(
            CombinedIndexBuildItem indexItem) {
        IndexView index = indexItem.getIndex();
        Set<DotName> targets = collectTestBeanTargets(index);
        return new AnnotationsTransformerBuildItem(new AnnotationsTransformer() {
            @Override
            public boolean appliesTo(AnnotationTarget.Kind kind) {
                return kind == AnnotationTarget.Kind.CLASS;
            }

            @Override
            public void transform(TransformationContext context) {
                ClassInfo classInfo = context.getTarget().asClass();
                if (!targets.contains(classInfo.name())) {
                    return;
                }
                if (classInfo.declaredAnnotation(ALTERNATIVE) == null) {
                    return;
                }
                var transformation = context.transform();
                if (classInfo.declaredAnnotation(PRIORITY) == null) {
                    transformation.add(AnnotationInstance.create(
                            PRIORITY,
                            classInfo,
                            new AnnotationValue[] {
                                    AnnotationValue.createIntegerValue("value", TEST_BEAN_PRIORITY)
                            }));
                }
                if (!hasScope(classInfo)) {
                    transformation.add(AnnotationInstance.create(
                            DEPENDENT, classInfo, new AnnotationValue[0]));
                }
                transformation.done();
            }

            private boolean hasScope(ClassInfo classInfo) {
                for (DotName scope : SCOPE_ANNOTATIONS) {
                    if (classInfo.declaredAnnotation(scope) != null) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private static Set<DotName> collectTestBeanTargets(IndexView index) {
        Set<DotName> targets = new LinkedHashSet<>();
        for (AnnotationInstance testBean : index.getAnnotations(TEST_BEAN)) {
            addTestBeanTarget(testBean, targets);
        }
        for (AnnotationInstance container : index.getAnnotations(TEST_BEANS_CONTAINER)) {
            AnnotationValue value = container.value();
            if (value == null) {
                continue;
            }
            for (AnnotationInstance nested : value.asNestedArray()) {
                addTestBeanTarget(nested, targets);
            }
        }
        return targets;
    }

    private static void addTestBeanTarget(AnnotationInstance testBean, Set<DotName> targets) {
        addClassValueTarget(testBean.value("bean"), targets);
        addClassValueTarget(testBean.value("beanProducer"), targets);
    }

    private static void addClassValueTarget(AnnotationValue value, Set<DotName> targets) {
        if (value == null) {
            return;
        }
        DotName beanClass = value.asClass().name();
        if (beanClass.equals(DotName.createSimple("void"))) {
            return;
        }
        targets.add(beanClass);
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
        Set<String> alreadyHandled = new LinkedHashSet<>();
        for (AnnotationInstance injectAnnotation : index.getAnnotations(INJECT)) {
            AnnotationTarget target = injectAnnotation.target();
            switch (target.kind()) {
                case FIELD -> {
                    var field = target.asField();
                    processCandidateType(
                            field.type(),
                            qualifiersOf(field.declaredAnnotations(), index),
                            index, alreadyHandled, syntheticBeans);
                }
                case METHOD -> {
                    MethodInfo method = target.asMethod();
                    for (int i = 0; i < method.parametersCount(); i++) {
                        processCandidateType(
                                method.parameterType(i),
                                qualifiersOf(method.parameters().get(i).declaredAnnotations(), index),
                                index, alreadyHandled, syntheticBeans);
                    }
                }
                case METHOD_PARAMETER -> {
                    var parameterInfo = target.asMethodParameter();
                    MethodInfo enclosing = parameterInfo.method();
                    int position = parameterInfo.position();
                    processCandidateType(
                            enclosing.parameterType(position),
                            qualifiersOf(enclosing.parameters().get(position).declaredAnnotations(), index),
                            index, alreadyHandled, syntheticBeans);
                }
                default -> {
                    // Skip class-level / record-component / type-use targets;
                    // those aren't injection points.
                }
            }
        }
        for (AnnotationInstance produces : index.getAnnotations(PRODUCES)) {
            if (produces.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo method = produces.target().asMethod();
            for (int i = 0; i < method.parametersCount(); i++) {
                processCandidateType(
                        method.parameterType(i),
                        qualifiersOf(method.parameters().get(i).declaredAnnotations(), index),
                        index, alreadyHandled, syntheticBeans);
            }
        }
        processSiblingParameters(index, alreadyHandled, syntheticBeans, OBSERVES);
        processSiblingParameters(index, alreadyHandled, syntheticBeans, OBSERVES_ASYNC);
        processSiblingParameters(index, alreadyHandled, syntheticBeans, DISPOSES);
    }

    private static void processSiblingParameters(
            IndexView index,
            Set<String> alreadyHandled,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            DotName eventAnnotation) {
        for (AnnotationInstance instance : index.getAnnotations(eventAnnotation)) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD_PARAMETER) {
                continue;
            }
            MethodInfo method = instance.target().asMethodParameter().method();
            int eventPosition = instance.target().asMethodParameter().position();
            for (int i = 0; i < method.parametersCount(); i++) {
                if (i == eventPosition) {
                    continue;
                }
                processCandidateType(
                        method.parameterType(i),
                        qualifiersOf(method.parameters().get(i).declaredAnnotations(), index),
                        index, alreadyHandled, syntheticBeans);
            }
        }
    }

    private static List<AnnotationInstance> qualifiersOf(
            List<AnnotationInstance> annotations, IndexView index) {
        List<AnnotationInstance> qualifiers = new ArrayList<>();
        for (AnnotationInstance ann : annotations) {
            DotName n = ann.name();
            if (n.equals(INJECT) || n.equals(DEFAULT_QUALIFIER) || n.equals(ANY_QUALIFIER)) {
                continue;
            }
            if (n.equals(NAMED)) {
                qualifiers.add(ann);
                continue;
            }
            ClassInfo annClass = index.getClassByName(n);
            if (annClass == null) {
                continue;
            }
            if (annClass.declaredAnnotation(QUALIFIER) != null) {
                qualifiers.add(ann);
            }
        }
        return qualifiers;
    }

    private static final DotName NONBINDING = DotName.createSimple("jakarta.enterprise.util.Nonbinding");

    private static String qualifierFingerprint(List<AnnotationInstance> qualifiers, IndexView index) {
        if (qualifiers.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (AnnotationInstance ann : qualifiers) {
            // Skip @Nonbinding member values — CDI's qualifier
            // equality treats two annotations with the same
            // binding-member values as the same qualifier even if
            // their @Nonbinding members differ. Without this filter
            // each distinct @Nonbinding value would produce its own
            // synthetic auto-mock bean and trip
            // AmbiguousResolutionException at deployment-validation
            // time (scenario-07).
            ClassInfo qualifierType = index.getClassByName(ann.name());
            StringBuilder b = new StringBuilder(ann.name().toString());
            for (var value : ann.values()) {
                if (qualifierType != null && isMemberNonbinding(qualifierType, value.name())) {
                    continue;
                }
                b.append('|').append(value.name()).append('=').append(value.value());
            }
            parts.add(b.toString());
        }
        parts.sort(String::compareTo);
        return String.join(";", parts);
    }

    /**
     * If {@code candidate} is a parameterized {@code Provider<X>} or
     * {@code Instance<X>}, return the inner type argument {@code X};
     * otherwise return {@code candidate} unchanged.
     */
    private static Type unwrapWrapperType(Type candidate) {
        if (candidate.kind() != Type.Kind.PARAMETERIZED_TYPE) {
            return candidate;
        }
        if (!WRAPPER_TYPES.contains(candidate.name())) {
            return candidate;
        }
        var arguments = candidate.asParameterizedType().arguments();
        if (arguments.isEmpty()) {
            return candidate;
        }
        return arguments.get(0);
    }

    private static boolean isMemberNonbinding(ClassInfo qualifierType, String memberName) {
        for (MethodInfo method : qualifierType.methods()) {
            if (method.name().equals(memberName) && method.hasDeclaredAnnotation(NONBINDING)) {
                return true;
            }
        }
        return false;
    }

    private static void processCandidateType(
            Type candidate,
            List<AnnotationInstance> qualifiers,
            IndexView index,
            Set<String> alreadyHandled,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        // Unwrap jakarta.inject.Provider<X> and
        // jakarta.enterprise.inject.Instance<X> — the IP's actual
        // bean type is the inner type argument, not the wrapper. CDI
        // resolves Provider/Instance to the underlying bean at
        // injection time; for auto-mock registration we need to
        // synthesise a mock for the inner type, not for the wrapper.
        candidate = unwrapWrapperType(candidate);
        DotName name = candidate.name();
        String qualifierKey = qualifierFingerprint(qualifiers, index);
        String dedupKey = name.toString() + "##" + qualifierKey;
        if (!alreadyHandled.add(dedupKey)) {
            return;
        }
        String packageName = name.packagePrefix();
        boolean isJdkOrFrameworkType = packageName != null
                && (packageName.startsWith("java.")
                        || packageName.startsWith("jakarta.")
                        || packageName.startsWith("io.quarkus.")
                        || packageName.startsWith("org.jboss.")
                        || packageName.startsWith("org.eclipse.microprofile."));
        if (isJdkOrFrameworkType && qualifiers.isEmpty()) {
            return;
        }
        if (isProducedByExistingMethod(name, qualifierKey, index)) {
            return;
        }
        ClassInfo target = index.getClassByName(name);
        if (target == null && !isJdkOrFrameworkType) {
            return;
        }
        if (target != null) {
            if (Modifier.isInterface(target.flags())) {
                if (qualifiers.isEmpty() && !index.getAllKnownImplementors(name).isEmpty()) {
                    return;
                }
            } else {
                if (qualifiers.isEmpty() && !target.declaredAnnotations().isEmpty()) {
                    return;
                }
            }
        }
        // Scope matches cdi-module's auto-mock contract:
        //   - JDK / framework types → Dependent (one mock per
        //     injection point because the bean is qualifier-typed
        //     and we don't want shared mutable state across
        //     unrelated injection sites)
        //   - User types (non-JDK / framework) → RequestScoped (per-
        //     request lifecycle is the safest auto-mock scope and
        //     matches cdi-module's scenario-23 expectation)
        Class<? extends java.lang.annotation.Annotation> beanScope =
                isJdkOrFrameworkType ? jakarta.enterprise.context.Dependent.class
                                     : jakarta.enterprise.context.RequestScoped.class;
        var configurator = SyntheticBeanBuildItem.configure(name)
                .types(candidate)
                .scope(beanScope)
                .creator(MockBeanCreator.class)
                .param(MockBeanCreator.TYPE_NAME_PARAM, name.toString())
                .unremovable();
        for (AnnotationInstance qualifier : qualifiers) {
            configurator.addQualifier(qualifier);
        }
        syntheticBeans.produce(configurator.done());
    }

    private static boolean isProducedByExistingMethod(
            DotName targetName, String qualifierKey, IndexView index) {
        for (AnnotationInstance produces : index.getAnnotations(PRODUCES)) {
            if (produces.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo method = produces.target().asMethod();
            if (!method.returnType().name().equals(targetName)) {
                continue;
            }
            List<AnnotationInstance> producerQualifiers = qualifiersOf(
                    method.declaredAnnotations(), index);
            if (qualifierFingerprint(producerQualifiers, index).equals(qualifierKey)) {
                return true;
            }
        }
        return false;
    }
}
