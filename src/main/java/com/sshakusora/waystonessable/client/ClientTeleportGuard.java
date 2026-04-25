package com.sshakusora.waystonessable.client;

public final class ClientTeleportGuard {

    private static long expiresAtMs;

    private ClientTeleportGuard() {
    }

    public static void protect(long durationMs) {
        expiresAtMs = System.currentTimeMillis() + durationMs;
    }

    public static boolean shouldIgnoreStop() {
        if (expiresAtMs == 0L) {
            return false;
        }

        if (System.currentTimeMillis() > expiresAtMs) {
            clear();
            return false;
        }

        return true;
    }

    public static void clear() {
        expiresAtMs = 0L;
    }
}
