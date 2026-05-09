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
package org.os890.jawelte.tests.jpa.scenario64;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.impl.launcher.JpaLauncherSessionListener;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;
import org.os890.jawelte.module.jpa.impl.util.JpaActivePersistenceUnits;

/**
 * Opt-in {@link JpaLauncherSessionListener}: the scenario's
 * {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}
 * registers the listener; Surefire's launcher session fires the listener
 * before any test class runs (pre-warm) and after every test class has
 * finished (cleanup).
 *
 * <p>{@link #firstMethodSeesListenerHasFiredOnSessionOpen} asserts the
 * pre-warm side effects observable in-test: {@code OPEN_COUNT} is
 * non-zero and the active persistence unit is registered (proves the
 * full bootstrap path still works under the listener).
 *
 * <p>{@link #secondMethodInvokesDeactivateImperatively} runs the
 * cleanup path imperatively because the real
 * {@code launcherSessionClosed} fires after every test method (so
 * there's no in-test observation point). Calling
 * {@link JpaLauncherSessionListener#deactivate()} directly verifies
 * {@link EmfCache#closeAll()} drops every cached EMF and
 * {@link JpaActivePersistenceUnits#reset()} clears the registry.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario64Test {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario64Test() {
    }

    /**
     * The listener fired on session open before any test class loaded;
     * its pre-warm of the entity scanner is silent on success but
     * {@code OPEN_COUNT} is incremented. Booting jpa-module + persisting
     * a row inside this method proves the listener didn't break the
     * bootstrap path.
     */
    @Test
    @Order(1)
    @Transactional
    public void firstMethodSeesListenerHasFiredOnSessionOpen() {
        assertThat(JpaLauncherSessionListener.OPEN_COUNT.get())
                .as("META-INF/services-registered LauncherSessionListener must have fired "
                        + "launcherSessionOpened before any test method ran")
                .isPositive();

        assertThat(JpaActivePersistenceUnits.get())
                .as("the active-PU registry must contain testPU64 once the CDI Extension has booted")
                .containsExactly("testPU64");

        assertThat(EmfCache.getCached("testPU64"))
                .as("EmfCache must hold the live EMF after bootstrap")
                .isPresent();

        entityManager.persist(new Marker());
        entityManager.flush();

        long count = entityManager
                .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
        assertThat(count)
                .as("the bootstrap survived a real persist + query under the opt-in listener")
                .isEqualTo(1L);
    }

    /**
     * Drive the cleanup path imperatively. The real session-close hook
     * runs after every test class, so there's no in-process way to
     * assert its side-effects from a test method. Calling
     * {@code deactivate()} directly catches regressions in the cleanup
     * logic regardless.
     */
    @Test
    @Order(2)
    public void secondMethodInvokesDeactivateImperatively() {
        assertThat(EmfCache.getCached("testPU64"))
                .as("precondition: EmfCache still holds the EMF before deactivate()")
                .isPresent();

        int closeCountBefore = JpaLauncherSessionListener.CLOSE_COUNT.get();

        JpaLauncherSessionListener.deactivate();

        assertThat(JpaLauncherSessionListener.CLOSE_COUNT.get())
                .as("deactivate() must increment CLOSE_COUNT exactly once")
                .isEqualTo(closeCountBefore + 1);

        assertThat(EmfCache.getCached("testPU64"))
                .as("EmfCache.closeAll() must close + remove every entry")
                .isEmpty();

        assertThat(JpaActivePersistenceUnits.get())
                .as("JpaActivePersistenceUnits.reset() must clear the registry")
                .isEmpty();
    }
}
