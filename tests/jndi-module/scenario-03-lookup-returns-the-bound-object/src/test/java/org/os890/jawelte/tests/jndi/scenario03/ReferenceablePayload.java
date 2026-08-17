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

import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

/**
 * A bindable object with the same shape a JDBC {@code DataSource} has:
 * {@link Referenceable}, handing out a non-null {@link Reference} that
 * names a factory able to rebuild it, and with no {@code equals}
 * override, so equality is identity.
 *
 * <p>That combination is what triggers the substitution this scenario is
 * about. H2's {@code org.h2.jdbcx.JdbcDataSource} — the driver every
 * other test tree here uses — matches it exactly, but a data source
 * would drag a JDBC dependency into a naming test and hide the mechanism
 * behind a vendor class. This carries the same three properties and
 * nothing else.
 */
public class ReferenceablePayload implements Referenceable {

    /** Address type under which {@link #id} travels in the reference. */
    static final String ID_ADDR_TYPE = "id";

    private final String id;

    /**
     * @param id identifies this instance across a rebuild, so a
     *           reconstruction can be recognised as carrying the same
     *           state while being a different object
     */
    public ReferenceablePayload(String id) {
        this.id = id;
    }

    /** @return the identifying value handed to the factory */
    public String id() {
        return id;
    }

    @Override
    public Reference getReference() {
        return new Reference(ReferenceablePayload.class.getName(),
                new StringRefAddr(ID_ADDR_TYPE, id),
                ReferenceablePayloadFactory.class.getName(), null);
    }
}
