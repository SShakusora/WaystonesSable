package com.sshakusora.waystonessable.storage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Cooperative, server-wide budget. An individual Sable read cannot be preempted.
 */
public final class TickWorkQueue {
    public interface Work {
        boolean step();

        void cancel();
    }

    private final Deque<Work> work = new ArrayDeque<>();
    private final LongSupplier clock;
    private final int maxSteps;
    private final long maxNanos;
    private boolean closed;

    public TickWorkQueue(int maxSteps, long maxNanos) {
        this(maxSteps, maxNanos, System::nanoTime);
    }

    TickWorkQueue(int maxSteps, long maxNanos, LongSupplier clock) {
        if (maxSteps <= 0 || maxNanos <= 0) throw new IllegalArgumentException("Budget must be positive");
        this.maxSteps = maxSteps;
        this.maxNanos = maxNanos;
        this.clock = clock;
    }

    public void add(Work task) {
        if (closed) task.cancel();
        else work.addLast(task);
    }

    public void tick() {
        final long start = clock.getAsLong();
        for (int i = 0; !closed && i < maxSteps && !work.isEmpty(); i++) {
            if (clock.getAsLong() - start >= maxNanos) break;
            final var task = work.removeFirst();
            if (!task.step()) {
                if (closed) task.cancel();
                else work.addLast(task);
            }
        }
    }

    public void close() {
        closed = true;
        while (!work.isEmpty()) work.removeFirst().cancel();
    }
}
