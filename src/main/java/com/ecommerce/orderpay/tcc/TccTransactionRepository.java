package com.ecommerce.orderpay.tcc;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class TccTransactionRepository {

    // 模拟事务表：每次 Try/Confirm/Cancel 都追加一条行为记录。
    private final List<TccTransactionRecord> transactionTable = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(0L);
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public void recordSuccess(
        String txId,
        long orderId,
        String userId,
        TccBranch branch,
        TccPhase phase,
        String payload
    ) {
        append(txId, orderId, userId, branch, phase, TccActionResult.SUCCESS, payload, null);
    }

    public void recordFailure(
        String txId,
        long orderId,
        String userId,
        TccBranch branch,
        TccPhase phase,
        String payload,
        String errorMessage
    ) {
        append(txId, orderId, userId, branch, phase, TccActionResult.FAILED, payload, errorMessage);
    }

    public boolean hasSuccess(String txId, TccBranch branch, TccPhase phase) {
        rwLock.readLock().lock();
        try {
            for (int i = transactionTable.size() - 1; i >= 0; i--) {
                TccTransactionRecord record = transactionTable.get(i);
                if (record.getTxId().equals(txId)
                    && record.getBranch() == branch
                    && record.getPhase() == phase
                    && record.getResult() == TccActionResult.SUCCESS) {
                    return true;
                }
            }
            return false;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Optional<TccTransactionRecord> findLatestSuccess(String txId, TccBranch branch, TccPhase phase) {
        rwLock.readLock().lock();
        try {
            return transactionTable.stream()
                .filter(record -> record.getTxId().equals(txId))
                .filter(record -> record.getBranch() == branch)
                .filter(record -> record.getPhase() == phase)
                .filter(record -> record.getResult() == TccActionResult.SUCCESS)
                .max(Comparator.comparingLong(TccTransactionRecord::getId));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<TccTransactionRecord> findByTxId(String txId) {
        rwLock.readLock().lock();
        try {
            List<TccTransactionRecord> result = new ArrayList<>();
            for (TccTransactionRecord record : transactionTable) {
                if (record.getTxId().equals(txId)) {
                    result.add(record);
                }
            }
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void append(
        String txId,
        long orderId,
        String userId,
        TccBranch branch,
        TccPhase phase,
        TccActionResult result,
        String payload,
        String errorMessage
    ) {
        rwLock.writeLock().lock();
        try {
            transactionTable.add(new TccTransactionRecord(
                idGenerator.incrementAndGet(),
                txId,
                orderId,
                userId,
                branch,
                phase,
                result,
                payload,
                errorMessage,
                Instant.now()
            ));
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
