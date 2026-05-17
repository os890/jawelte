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

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.os890.jawelte.tests.lnp.scenario09.entity.hr.Department;
import org.os890.jawelte.tests.lnp.scenario09.entity.hr.Employee;

/**
 * HR domain service — {@code @Stateless} EJB. Method names mirror
 * scenario-01's HR test methods.
 */
@Stateless
public class HrService {

    @Inject
    private EntityManager em;

    /** No-arg constructor required by the EJB stereotype. */
    public HrService() {
    }

    /** SELECT e FROM Employee e WHERE e.department.id = :id. */
    public void queryEmployeesByDepartment(Long deptId) {
        em.createQuery(
                "SELECT e FROM Employee e WHERE e.department.id = :d",
                Employee.class)
                .setParameter("d", deptId)
                .getResultList();
    }

    /** SELECT e.department.name, COUNT(e) FROM Employee e GROUP BY ... */
    public void countEmployeesPerDepartment() {
        em.createQuery(
                "SELECT e.department.name, COUNT(e) FROM Employee e "
                        + "GROUP BY e.department.name",
                Object[].class)
                .getResultList();
    }

    /** Re-assign employee N to a different department. */
    public void updateEmployeeDepartment(Long employeeId, Long deptId) {
        Employee emp = em.find(Employee.class, employeeId);
        Department dept = em.find(Department.class, deptId);
        emp.setDepartment(dept);
        em.flush();
    }

    /** SELECT AVG(s.amount) FROM Salary s. */
    public void averageSalary() {
        em.createQuery(
                "SELECT AVG(s.amount) FROM Salary s", Double.class)
                .getSingleResult();
    }
}
