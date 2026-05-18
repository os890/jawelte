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
package org.apache.deltaspike.integration;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test-classpath bean that lives under the
 * {@code org.apache.deltaspike.} prefix. Stands in for any DeltaSpike
 * vendor-internal bean a downstream user might layer on top of
 * jawelte. The bundled framework allowlist must let it survive
 * {@code @EnableTestBeans(limitToTestBeans=true)} mode.
 */
@ApplicationScoped
public class DeltaSpikeStubBean {

    public DeltaSpikeStubBean() {
    }

    public String identify() {
        return "deltaspike-stub";
    }
}
