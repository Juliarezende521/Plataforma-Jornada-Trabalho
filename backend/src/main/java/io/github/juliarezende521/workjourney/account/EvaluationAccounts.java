package io.github.juliarezende521.workjourney.account;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("evaluation & !bootstrap")
public class EvaluationAccounts implements ApplicationRunner {
    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;
    private final String password;

    public EvaluationAccounts(AppUserRepository users, EmployeeRepository employees,
                              PasswordEncoder encoder, @Value("${EVALUATION_PASSWORD}") String password) {
        this.users = users;
        this.employees = employees;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Evaluation password must have at least 12 characters and at most 72 UTF-8 bytes");
        }
        create("brasil@example.invalid", "Colaborador Brasil", "America/Sao_Paulo", false);
        create("europa@example.invalid", "Colaborador Europa", "Europe/Amsterdam", false);
        create("rh@example.invalid", "Recursos Humanos", "America/Sao_Paulo", true);
    }

    private void create(String email, String name, String zone, boolean hrReader) {
        if (users.findByEmail(email).isPresent()) return;
        AppUser account = users.save(new AppUser(email, encoder.encode(password), hrReader));
        employees.save(new Employee(account, name, zone));
    }
}
