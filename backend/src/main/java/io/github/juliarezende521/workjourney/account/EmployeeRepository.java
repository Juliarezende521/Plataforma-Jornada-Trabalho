package io.github.juliarezende521.workjourney.account;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("!bootstrap")
public interface EmployeeRepository extends JpaRepository<Employee, UUID> { }
