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
package org.os890.jawelte.module.wiremock.impl.adapter.contributor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Qualifier;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.adapter.quarkus.JaweltAutoMockBuildCompatibleExtension;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;
import org.os890.jawelte.module.wiremock.api.WireMockEndpoint;
import org.os890.jawelte.module.wiremock.impl.WireMockServerRegistry;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

import io.quarkus.arc.Arc;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.processor.BeanProcessor;

/**
 * wiremock-module's {@link ArcContextContributor}: replaces the
 * {@code AfterBeanDiscovery} half of {@code WireMockCdiExtension}
 * which calls {@code event.addBean()} — ArC returns {@code null}
 * from that builder, so synthetic-bean registration for discovered
 * {@code @WireMockEndpoint}-rooted qualifiers never happens under
 * the ArC-based test container.
 *
 * <p>This contributor performs the equivalent registration via
 * ArC's {@code BeanRegistrar} surface. For every
 * {@code (userQualifierType, endpointKey)} pair reachable from
 * {@code @Inject}-able WireMock fields on the active test class, it
 * registers three synthetic beans:
 *
 * <ul>
 *   <li>{@code WireMockServer} — produced by
 *       {@link ServerCreator}</li>
 *   <li>{@code WireMock} (client) — produced by
 *       {@link ClientCreator}</li>
 *   <li>{@code WireMockRuntimeInfo} — produced by
 *       {@link RuntimeInfoCreator}</li>
 * </ul>
 *
 * <p>Each synthetic bean carries the user qualifier annotation
 * type; {@code @Default} is added when no
 * {@code @Priority}-based winner exists in multi-qualifier mode
 * (mirroring the portable-extension behaviour).
 *
 * <p>The creator classes look up the {@link WireMockServerRegistry}
 * via {@code Arc.container().instance(...)} and read the matching
 * {@link com.github.tomakehurst.wiremock.WireMockServer} /
 * {@code WireMock} / {@code WireMockRuntimeInfo} bundle that
 * {@code WireMockLifecycleAdapter.beforeAll} already populated.
 *
 * <p>Default-only mode (no user qualifier discovered) is left to
 * {@code WireMockProducer} — its {@code @Default @Produces}
 * methods satisfy unqualified injection without help from this
 * contributor.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 */
public class WireMockArcContextContributor implements ArcContextContributor {

    private static final String ENDPOINT_KEY_PARAM = "endpointKey";

    private static final DotName DEFAULT_DOT =
            DotName.createSimple(Default.class.getName());

    private static final DotName WIREMOCK_SERVER_DOT =
            DotName.createSimple(WireMockServer.class.getName());

    private static final DotName WIREMOCK_CLIENT_DOT =
            DotName.createSimple(WireMock.class.getName());

    private static final DotName WIREMOCK_RUNTIME_INFO_DOT =
            DotName.createSimple(WireMockRuntimeInfo.class.getName());

    private static final Set<Class<?>> INJECTABLE_WIREMOCK_TYPES = Set.of(
            WireMockServer.class,
            WireMock.class,
            WireMockRuntimeInfo.class);

    /** No-arg constructor required by {@code ServiceLoader}. */
    public WireMockArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        Class<?> testClass = testContext.getTestClass();
        if (testClass.getAnnotation(EnableWireMock.class) == null) {
            return;
        }
        Map<Class<? extends Annotation>, Class<? extends Annotation>> endpoints =
                discoverEndpoints(testClass);
        if (endpoints.isEmpty()) {
            // Default-only mode: WireMockProducer's @Default @Produces
            // methods cover the injection points. No synthetic beans
            // needed.
            return;
        }
        Class<? extends Annotation> defaultWinner = resolveDefaultWinner(endpoints.keySet());
        boolean hasWinner = defaultWinner != null;

        // When at least one user qualifier was discovered, veto
        // WireMockProducer — its @Default @Produces methods would
        // otherwise compete with the synthetic bean my registrar
        // adds for the qualified case where the synthetic also
        // carries @Default. Mirrors WireMockCdiExtension.onProcessProducerType.
        DotName producerDot = DotName.createSimple(
                "org.os890.jawelte.module.wiremock.impl.WireMockProducer");
        builder.addExcludeType(classInfo -> classInfo.name().equals(producerDot));

        builder.addBeanRegistrar(registration -> {
            for (Map.Entry<Class<? extends Annotation>, Class<? extends Annotation>> entry
                    : endpoints.entrySet()) {
                Class<? extends Annotation> userQualifier = entry.getKey();
                Class<? extends Annotation> endpointKey = entry.getValue();
                boolean addDefault = !hasWinner || userQualifier == defaultWinner;

                registerBean(registration, WIREMOCK_SERVER_DOT, WireMockServer.class,
                        userQualifier, endpointKey, addDefault, ServerCreator.class);
                registerBean(registration, WIREMOCK_CLIENT_DOT, WireMock.class,
                        userQualifier, endpointKey, addDefault, ClientCreator.class);
                registerBean(registration, WIREMOCK_RUNTIME_INFO_DOT, WireMockRuntimeInfo.class,
                        userQualifier, endpointKey, addDefault, RuntimeInfoCreator.class);
            }
        });

        // Tell the auto-mock BCE about the shapes we're registering so
        // it doesn't add a parallel synthetic auto-mock bean for the
        // qualified IPs (which would land us in ambiguous resolution
        // at deployment).
        for (Map.Entry<Class<? extends Annotation>, Class<? extends Annotation>> entry
                : endpoints.entrySet()) {
            Class<? extends Annotation> userQualifier = entry.getKey();
            Set<String> qualifierFqns = Set.of(userQualifier.getName());
            JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape(
                    WireMockServer.class.getName(), qualifierFqns);
            JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape(
                    WireMock.class.getName(), qualifierFqns);
            JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape(
                    WireMockRuntimeInfo.class.getName(), qualifierFqns);
            // When this synthetic bean carries @Default, it ALSO
            // satisfies the unqualified injection point — register
            // the empty-qualifier shape so the BCE auto-mock pass
            // does not double up on the unqualified IP.
            boolean addDefault = !hasWinner || userQualifier == defaultWinner;
            if (addDefault) {
                JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape(
                        WireMockServer.class.getName(), Set.of());
                JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape(
                        WireMock.class.getName(), Set.of());
                JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape(
                        WireMockRuntimeInfo.class.getName(), Set.of());
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerBean(
            io.quarkus.arc.processor.BeanRegistrar.RegistrationContext registration,
            DotName typeDot,
            Class<?> beanClass,
            Class<? extends Annotation> userQualifier,
            Class<? extends Annotation> endpointKey,
            boolean addDefault,
            Class<? extends BeanCreator<?>> creator) {
        io.quarkus.arc.processor.BeanConfigurator<?> configurator = registration.configure(typeDot);
        configurator.scope(Dependent.class)
                .addType(beanClass)
                .addQualifier(AnnotationInstance.builder(
                        DotName.createSimple(userQualifier.getName())).build())
                .creator((Class) creator)
                .param(ENDPOINT_KEY_PARAM, endpointKey.getName());
        if (addDefault) {
            configurator.addQualifier(AnnotationInstance.builder(DEFAULT_DOT).build());
        }
        configurator.done();
    }

    /**
     * Walks the test class hierarchy and returns the
     * {@code userQualifierType → endpointKey} map of every
     * {@code @WireMockEndpoint}-rooted qualifier reachable from a
     * field whose type is one of the injectable WireMock types.
     * Mirrors {@code WireMockLifecycleAdapter.discoverEndpoints}
     * — duplicated here to keep wiremock-module/impl's contributor
     * package independent of the lifecycle adapter's static helpers.
     */
    private static Map<Class<? extends Annotation>, Class<? extends Annotation>>
            discoverEndpoints(Class<?> testClass) {
        Map<Class<? extends Annotation>, Class<? extends Annotation>> sink = new LinkedHashMap<>();
        for (Class<?> current = testClass; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!INJECTABLE_WIREMOCK_TYPES.contains(field.getType())) {
                    continue;
                }
                for (Annotation annotation : field.getAnnotations()) {
                    collectEndpoint(annotation.annotationType(), sink, new HashSet<>());
                }
            }
        }
        return sink;
    }

    private static void collectEndpoint(
            Class<? extends Annotation> annotationType,
            Map<Class<? extends Annotation>, Class<? extends Annotation>> sink,
            Set<Class<? extends Annotation>> visited) {
        if (!visited.add(annotationType)) {
            return;
        }
        Class<? extends Annotation> endpointKey = findEndpointAncestor(annotationType, new HashSet<>());
        if (endpointKey == null) {
            return;
        }
        if (annotationType.isAnnotationPresent(Qualifier.class)) {
            sink.putIfAbsent(annotationType, endpointKey);
        }
    }

    private static Class<? extends Annotation> findEndpointAncestor(
            Class<? extends Annotation> annotationType, Set<Class<? extends Annotation>> visited) {
        if (!visited.add(annotationType)) {
            return null;
        }
        if (annotationType.isAnnotationPresent(WireMockEndpoint.class)) {
            return annotationType;
        }
        for (Annotation meta : annotationType.getAnnotations()) {
            String packageName = meta.annotationType().getPackageName();
            if (packageName.startsWith("java.lang") || packageName.startsWith("jakarta.")) {
                continue;
            }
            Class<? extends Annotation> result = findEndpointAncestor(meta.annotationType(), visited);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * The {@code @Default} winner among the discovered qualifiers in
     * multi-qualifier mode. Mirrors {@code WireMockCdiExtension}'s
     * priority-based selection: a {@code @Priority} on a qualifier
     * lowers its sort value; among qualifiers carrying
     * {@code @Priority}, the single one at the minimum value wins;
     * ties produce no winner (all synthetic beans keep their
     * {@code @Default} → ambiguous unqualified injection).
     */
    private static Class<? extends Annotation> resolveDefaultWinner(
            Iterable<Class<? extends Annotation>> qualifiers) {
        Class<? extends Annotation> lowestPriorityQualifier = null;
        int lowestPriorityValue = Integer.MAX_VALUE;
        int countAtLowest = 0;
        int totalQualifiers = 0;
        for (Class<? extends Annotation> qualifier : qualifiers) {
            totalQualifiers++;
            Priority priorityAnnotation = qualifier.getAnnotation(Priority.class);
            if (priorityAnnotation == null) {
                continue;
            }
            int value = priorityAnnotation.value();
            if (value < lowestPriorityValue) {
                lowestPriorityValue = value;
                lowestPriorityQualifier = qualifier;
                countAtLowest = 1;
            } else if (value == lowestPriorityValue) {
                countAtLowest++;
            }
        }
        if (totalQualifiers <= 1) {
            return null;
        }
        if (lowestPriorityQualifier == null) {
            return null;
        }
        if (countAtLowest != 1) {
            return null;
        }
        return lowestPriorityQualifier;
    }

    /**
     * Resolves the {@link WireMockServerRegistry} bean from the
     * running ArC container and reads the cached resource bundle
     * for the endpoint key passed via the synthetic bean's
     * {@code endpointKey} param.
     */
    private static <T> T resolve(
            SyntheticCreationalContext<T> ctx,
            java.util.function.Function<org.os890.jawelte.module.wiremock.impl.EndpointResources, T> selector) {
        String endpointKeyName = (String) ctx.getParams().get(ENDPOINT_KEY_PARAM);
        if (endpointKeyName == null) {
            throw new IllegalStateException(
                    "WireMock synthetic bean is missing the 'endpointKey' param");
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = WireMockArcContextContributor.class.getClassLoader();
        }
        Class<? extends Annotation> endpointKey;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> loaded =
                    (Class<? extends Annotation>) Class.forName(endpointKeyName, false, cl);
            endpointKey = loaded;
        } catch (ClassNotFoundException notFound) {
            throw new IllegalStateException(
                    "Cannot load endpoint key class " + endpointKeyName, notFound);
        }
        WireMockServerRegistry registry =
                Arc.container().instance(WireMockServerRegistry.class).get();
        return selector.apply(registry.getFor(endpointKey));
    }

    /** Creator for the {@link WireMockServer} synthetic bean. */
    public static class ServerCreator implements BeanCreator<WireMockServer> {

        /** No-arg constructor required by ArC's reflective lookup. */
        public ServerCreator() {
        }

        @Override
        public WireMockServer create(SyntheticCreationalContext<WireMockServer> ctx) {
            return resolve(ctx,
                    org.os890.jawelte.module.wiremock.impl.EndpointResources::server);
        }
    }

    /** Creator for the {@link WireMock} client synthetic bean. */
    public static class ClientCreator implements BeanCreator<WireMock> {

        /** No-arg constructor required by ArC's reflective lookup. */
        public ClientCreator() {
        }

        @Override
        public WireMock create(SyntheticCreationalContext<WireMock> ctx) {
            return resolve(ctx,
                    org.os890.jawelte.module.wiremock.impl.EndpointResources::client);
        }
    }

    /** Creator for the {@link WireMockRuntimeInfo} synthetic bean. */
    public static class RuntimeInfoCreator implements BeanCreator<WireMockRuntimeInfo> {

        /** No-arg constructor required by ArC's reflective lookup. */
        public RuntimeInfoCreator() {
        }

        @Override
        public WireMockRuntimeInfo create(SyntheticCreationalContext<WireMockRuntimeInfo> ctx) {
            return resolve(ctx,
                    org.os890.jawelte.module.wiremock.impl.EndpointResources::runtimeInfo);
        }
    }
}
