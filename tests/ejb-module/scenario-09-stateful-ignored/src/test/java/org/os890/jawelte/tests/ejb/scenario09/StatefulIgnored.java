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
package org.os890.jawelte.tests.ejb.scenario09;

import jakarta.ejb.Stateful;
import jakarta.enterprise.context.RequestScoped;

/**
 * {@code @jakarta.ejb.Stateful} bean. The default mapper recognises
 * neither {@code @Singleton} nor {@code @Stateless} on this class and
 * returns {@code null} from {@code mapBeanMetadata} — so ejb-module
 * applies no scope override and adds no implicit
 * {@code @Transactional}. The class also carries
 * {@code @RequestScoped} purely so the type is discovered as a CDI
 * bean under {@code bean-discovery-mode="annotated"} (otherwise the
 * "did ejb-module touch this?" assertion has no observable bean to
 * test against).
 */
@Stateful
@RequestScoped
public class StatefulIgnored {

    /** Required public no-arg constructor. */
    public StatefulIgnored() {
    }

    /**
     * @return a literal "stateful" marker
     */
    public String tag() {
        return "stateful";
    }
}
