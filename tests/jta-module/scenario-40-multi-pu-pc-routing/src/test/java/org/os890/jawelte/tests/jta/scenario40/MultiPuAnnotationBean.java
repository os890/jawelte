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
package org.os890.jawelte.tests.jta.scenario40;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;

/**
 * CDI bean using JPA-style {@code @PersistenceContext(unitName=...)}
 * and {@code @PersistenceUnit(unitName=...)} annotations.
 * jpa-module's {@code JpaCdiExtension} rewrites these into
 * {@code @Inject @Named("unitName")} on PAT, so the synthetic
 * per-PU CDI beans resolve correctly under multi-PU.
 */
@ApplicationScoped
public class MultiPuAnnotationBean {

    @PersistenceContext(unitName = "testJtaPU40a")
    private EntityManager entityManagerA;

    @PersistenceContext(unitName = "testJtaPU40b")
    private EntityManager entityManagerB;

    @PersistenceUnit(unitName = "testJtaPU40a")
    private EntityManagerFactory factoryA;

    @PersistenceUnit(unitName = "testJtaPU40b")
    private EntityManagerFactory factoryB;

    /** No-arg constructor for CDI. */
    public MultiPuAnnotationBean() {
    }

    /** @return the EntityManager for PU "a" */
    public EntityManager getEntityManagerA() {
        return entityManagerA;
    }

    /** @return the EntityManager for PU "b" */
    public EntityManager getEntityManagerB() {
        return entityManagerB;
    }

    /** @return the EntityManagerFactory for PU "a" */
    public EntityManagerFactory getFactoryA() {
        return factoryA;
    }

    /** @return the EntityManagerFactory for PU "b" */
    public EntityManagerFactory getFactoryB() {
        return factoryB;
    }
}
