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
package example.mapper;

import java.lang.annotation.Annotation;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.ejb.Stateful;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.AnnotationLiteral;

import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * Custom additional EjbAnnotationMapper that claims @jakarta.ejb.Stateful
 * classes and adds @RequestScoped. The default terminal mapper doesn't
 * know about @Stateful, so without this provider the @Stateful class
 * would not become a CDI bean.
 */
@Priority(1)
public class StatefulAsRequestScopedMapper implements EjbAnnotationMapper {

    private static final class RequestScopedLiteral
            extends AnnotationLiteral<RequestScoped> implements RequestScoped {
    }

    @Override
    public List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager) {
        if (beanClass.isAnnotationPresent(Stateful.class)) {
            return List.of(new RequestScopedLiteral());
        }
        return null;   // not handled — fall through to the next mapper / default
    }
}
