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
package org.os890.jawelte.tests.jndi.scenario03;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * Rebuilds a {@link ReferenceablePayload} from its {@link Reference} —
 * the vendor {@code ObjectFactory} equivalent, and the reason this
 * scenario is a real test rather than a tautology.
 *
 * <p>Because this factory works, {@code NamingManager.getObjectInstance}
 * succeeds on the payload's reference and yields a <em>different</em>
 * object carrying the same state. So if the naming tree dereferences,
 * the scenario's identity assertions fail with two working payloads
 * rather than with an error — which is exactly the failure an application
 * would hit, and which a non-reconstructible reference would have hidden.
 */
public class ReferenceablePayloadFactory implements ObjectFactory {

    /** No-arg constructor required by {@code NamingManager}. */
    public ReferenceablePayloadFactory() {
    }

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
            Hashtable<?, ?> environment) {
        if (!(obj instanceof Reference reference)) {
            return null;
        }
        RefAddr id = reference.get(ReferenceablePayload.ID_ADDR_TYPE);
        if (id == null) {
            return null;
        }
        return new ReferenceablePayload((String) id.getContent());
    }
}
