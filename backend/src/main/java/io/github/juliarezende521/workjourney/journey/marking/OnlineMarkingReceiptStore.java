package io.github.juliarezende521.workjourney.journey.marking;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!bootstrap")
public class OnlineMarkingReceiptStore {
    private final EntityManager entityManager;

    public OnlineMarkingReceiptStore(EntityManager entityManager) { this.entityManager = entityManager; }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<OnlineMarkingReceipt> find(UUID employeeId, UUID key) {
        return entityManager.createQuery("SELECT r FROM OnlineMarkingReceipt r WHERE r.employeeId = :employee AND r.operation = :operation AND r.requestKey = :key", OnlineMarkingReceipt.class)
                .setParameter("employee", employeeId).setParameter("operation", MarkingCommand.OPERATION)
                .setParameter("key", key).getResultList().stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(UUID employeeId, MarkingCommand command, MarkingResult result) {
        entityManager.persist(new OnlineMarkingReceipt(employeeId, command, result));
        entityManager.flush();
    }
}
