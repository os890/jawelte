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
package org.os890.jawelte.tests.lnp.scenario10;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * A distinct {@link ParameterizedType} per {@code id}. The cdi extension's
 * {@code unwrapWrapper} returns a non-{@code Provider}/{@code Instance}
 * {@code ParameterizedType} unchanged, so each instance yields a distinct
 * candidate key — letting the stress test add many distinct elements through
 * the real observer without needing many real types.
 */
public class UniqueParameterizedType implements ParameterizedType {

    private final int id;

    public UniqueParameterizedType(int id) {
        this.id = id;
    }

    @Override
    public Type[] getActualTypeArguments() {
        return new Type[] {Integer.class};
    }

    @Override
    public Type getRawType() {
        return List.class;
    }

    @Override
    public Type getOwnerType() {
        return null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UniqueParameterizedType that && this.id == that.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
