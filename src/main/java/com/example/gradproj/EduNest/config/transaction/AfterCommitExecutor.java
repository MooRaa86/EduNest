package com.example.gradproj.EduNest.config.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Centralized reusable Spring component to execute actions either immediately (if no transaction
 * is active) or deferred until the active transaction successfully commits (afterCommit hook).
 *
 * Implements DRY post-commit delivery for notifications (EB-50 / NOTIF-03) and atomic post-commit
 * storage deletions (EB-45 / FILE-05).
 */
@Slf4j
@Component
public class AfterCommitExecutor {

    public void execute(Runnable action) {
        if (action == null) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception ex) {
                        log.error("Error executing post-commit action", ex);
                    }
                }
            });
        } else {
            action.run();
        }
    }
}
