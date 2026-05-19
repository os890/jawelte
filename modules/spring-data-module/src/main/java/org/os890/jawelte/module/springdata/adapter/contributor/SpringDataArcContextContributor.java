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
package org.os890.jawelte.module.springdata.adapter.contributor;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.adapter.quarkus.JaweltAutoMockBuildCompatibleExtension;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.processor.BeanProcessor;

/**
 * spring-data-module's {@link ArcContextContributor}: replaces the
 * {@code AfterBeanDiscovery} half of
 * {@code SpringDataRepositoryExtension}, which calls
 * {@code event.addBean()...} — ArC returns {@code null} from that
 * builder, so synthetic-bean registration for every discovered
 * Spring Data repository interface never happens under the ArC-based
 * test container.
 *
 * <p>This contributor performs the equivalent registration via ArC's
 * {@code BeanRegistrar} surface. For every {@code @Inject}-able
 * Spring Data repository interface found on the active test class
 * (and not annotated {@code @NoRepositoryBean}), it registers a
 * synthetic {@code @RequestScoped} CDI bean whose creator builds the
 * repository through Spring Data's {@code JpaRepositoryFactory} on
 * first use.
 *
 * <p>The matching bean shape is also pre-registered through
 * {@link JaweltAutoMockBuildCompatibleExtension#preRegisterExistingBeanShape}
 * so the auto-mock BCE and {@code MockAndInlineBeanRegistrar} don't
 * add a competing auto-mock for the same IPs.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 */
public class SpringDataArcContextContributor implements ArcContextContributor {

    private static final String SPRING_DATA_PACKAGE_PREFIX = "org.springframework.data";

    private static final String REPOSITORY_INTERFACE_PARAM = "repositoryInterface";

    /** No-arg constructor required by {@code ServiceLoader}. */
    public SpringDataArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        Set<Class<?>> repositoryInterfaces = discoverRepositoryInterfaces(testContext.getTestClass());
        if (repositoryInterfaces.isEmpty()) {
            return;
        }
        builder.addBeanRegistrar(registration -> {
            for (Class<?> repositoryInterface : repositoryInterfaces) {
                DotName typeDot = DotName.createSimple(repositoryInterface.getName());
                @SuppressWarnings({"rawtypes", "unchecked"})
                io.quarkus.arc.processor.BeanConfigurator<?> configurator =
                        registration.configure(typeDot);
                configurator.scope(RequestScoped.class)
                        .addType(repositoryInterface)
                        .addQualifier(AnnotationInstance.builder(
                                DotName.createSimple("jakarta.enterprise.inject.Default")).build())
                        .creator((Class) RepositoryCreator.class)
                        .param(REPOSITORY_INTERFACE_PARAM, repositoryInterface.getName());
                configurator.done();
            }
        });

        // Pre-register the shape so the BCE auto-mock and
        // MockAndInlineBeanRegistrar don't add a parallel synthetic
        // bean for these IPs. Repository IPs are unqualified — the
        // pre-registered shape uses an empty qualifier set, matching
        // qualifierFqnSet's filtering of @Default / @Any / @Named.
        for (Class<?> repositoryInterface : repositoryInterfaces) {
            JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape(
                    repositoryInterface.getName(), Set.of());
        }
    }

    /**
     * Walks the test class hierarchy and returns every
     * {@code @Inject}-able Spring Data repository interface
     * reachable through field types. Mirrors
     * {@code SpringDataRepositoryExtension.collectFromTestClass} so
     * the discovery decision is identical regardless of which CDI
     * runtime drives the bootstrap.
     */
    private static Set<Class<?>> discoverRepositoryInterfaces(Class<?> testClass) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> current = testClass; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (!fieldType.isInterface()) {
                    continue;
                }
                if (isSpringDataMarker(fieldType)) {
                    continue;
                }
                if (fieldType.isAnnotationPresent(NoRepositoryBean.class)) {
                    continue;
                }
                if (!Repository.class.isAssignableFrom(fieldType)) {
                    continue;
                }
                result.add(fieldType);
            }
        }
        return result;
    }

    private static boolean isSpringDataMarker(Class<?> candidate) {
        String packageName = candidate.getPackageName();
        return packageName.equals(SPRING_DATA_PACKAGE_PREFIX)
                || packageName.startsWith(SPRING_DATA_PACKAGE_PREFIX + ".");
    }

    /** Creator for the synthetic Spring Data repository bean. */
    public static class RepositoryCreator implements BeanCreator<Object> {

        /** No-arg constructor required by ArC's reflective lookup. */
        public RepositoryCreator() {
        }

        @Override
        public Object create(SyntheticCreationalContext<Object> ctx) {
            String repositoryFqn = (String) ctx.getParams().get(REPOSITORY_INTERFACE_PARAM);
            if (repositoryFqn == null) {
                throw new IllegalStateException(
                        "Spring Data synthetic bean is missing the '" + REPOSITORY_INTERFACE_PARAM
                                + "' param");
            }
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = SpringDataArcContextContributor.class.getClassLoader();
            }
            Class<?> repositoryInterface;
            try {
                repositoryInterface = Class.forName(repositoryFqn, false, cl);
            } catch (ClassNotFoundException notFound) {
                throw new IllegalStateException(
                        "Cannot load Spring Data repository interface " + repositoryFqn, notFound);
            }
            EntityManager entityManager = CDI.current().select(EntityManager.class).get();
            JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
            return factory.getRepository(repositoryInterface);
        }
    }
}
