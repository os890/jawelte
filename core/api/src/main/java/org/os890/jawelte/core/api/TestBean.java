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
package org.os890.jawelte.core.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a test bean replacement. Place either on a class (or a
 * meta-annotation), or on a static field whose value becomes the bean
 * instance.
 *
 * <p>Placed on a {@code static} field with no attributes, the field
 * value becomes the bean instance with scope {@code @Singleton}, bean
 * types of the declared field type plus {@code Object}, and the field's
 * CDI qualifiers (plus {@code @Default} / {@code @Any} as appropriate).
 *
 * <p>Validation rules for field-mode declarations (must be static,
 * value must be non-null at registration time) are enforced by the CDI
 * extension. So is the rule that {@code bean} and {@code beanProducer}
 * are mutually exclusive on a single declaration.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(TestBeans.class)
public @interface TestBean {

    /**
     * The {@code @Alternative} bean class to activate.
     *
     * @return the bean class, or {@code void.class} if none
     */
    Class<?> bean() default void.class;

    /**
     * The {@code @Alternative} producer class to activate.
     *
     * @return the producer class, or {@code void.class} if none
     */
    Class<?> beanProducer() default void.class;
}
