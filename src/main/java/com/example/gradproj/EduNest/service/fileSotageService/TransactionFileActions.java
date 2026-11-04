package com.example.gradproj.EduNest.service.fileSotageService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Coordinates best-effort file cleanup with the outcome of the enclosing database transaction. */
@Slf4j
public final class TransactionFileActions {
    private TransactionFileActions() { }

    public static void deleteAfterCommit(Runnable delete) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            delete.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    delete.run();
                } catch (Exception ex) {
                    log.error("Post-commit file cleanup failed; manual reconciliation may be required", ex);
                }
            }
        });
    }

    public static void deleteOnRollback(Runnable delete) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try {
                            delete.run();
                        } catch (Exception ex) {
                            log.error("Rollback file cleanup failed; manual reconciliation may be required", ex);
                        }
                    }
                }
            });
        }
    }
}
