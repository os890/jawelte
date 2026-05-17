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
package org.os890.jawelte.tests.lnp.scenario09.service;

import java.math.BigDecimal;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.os890.jawelte.tests.lnp.scenario09.entity.finance.Account;
import org.os890.jawelte.tests.lnp.scenario09.entity.finance.FinancialTransaction;

/**
 * Finance domain service — {@code @Stateless} EJB. Method names
 * mirror scenario-01's finance test methods.
 */
@Stateless
public class FinanceService {

    @Inject
    private EntityManager em;

    /** No-arg constructor required by the EJB stereotype. */
    public FinanceService() {
    }

    /** SELECT t FROM FinancialTransaction t WHERE t.account.id = :id. */
    public void queryTransactionsByAccount(Long accountId) {
        em.createQuery(
                "SELECT t FROM FinancialTransaction t WHERE t.account.id = :id",
                FinancialTransaction.class)
                .setParameter("id", accountId)
                .getResultList();
    }

    /** SELECT SUM(a.balance) FROM Account a. */
    public void sumAccountBalances() {
        em.createQuery(
                "SELECT SUM(a.balance) FROM Account a", BigDecimal.class)
                .getSingleResult();
    }

    /** Add {@code delta} to account N's balance. */
    public void updateAccountBalance(Long accountId, BigDecimal delta) {
        Account acc = em.find(Account.class, accountId);
        acc.setBalance(acc.getBalance().add(delta));
        em.flush();
    }
}
