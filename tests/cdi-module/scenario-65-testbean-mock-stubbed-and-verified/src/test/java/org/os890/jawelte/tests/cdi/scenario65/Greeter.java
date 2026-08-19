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
package org.os890.jawelte.tests.cdi.scenario65;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The bean under test; it calls its collaborator once per greeting. */
@ApplicationScoped
public class Greeter {

    @Inject
    private AuditService auditService;

    public Greeter() {
    }

    /**
     * Greets and audits.
     *
     * @param name who to greet
     * @return the greeting, with whatever the collaborator answered appended
     */
    public String greet(String name) {
        return "hello " + name + "/" + auditService.audit("greet:" + name);
    }

    /**
     * @return the collaborator this bean was injected with
     */
    public AuditService collaborator() {
        return auditService;
    }
}
