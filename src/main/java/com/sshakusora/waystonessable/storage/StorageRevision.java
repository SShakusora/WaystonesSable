package com.sshakusora.waystonessable.storage;

/**
 * Detects writes between batches without holding a lock across ticks.
 */
public interface StorageRevision {
    long waystonessable$getRevision();
}
