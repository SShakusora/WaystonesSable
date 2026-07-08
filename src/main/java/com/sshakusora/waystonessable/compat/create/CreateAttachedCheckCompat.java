package com.sshakusora.waystonessable.compat.create;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CreateAttachedCheckCompat {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private CreateAttachedCheckCompat() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        BlockMovementChecks.registerAttachedCheck((state, level, pos, direction) ->
                SableWaystoneCompat.isWaystoneAttachedTowards(state, level, pos, direction)
                        ? CheckResult.SUCCESS
                        : CheckResult.PASS);

        WaystonesSable.LOGGER.info(
                "Registered Create attached-block check for Waystone upper/lower halves; "
                        + "Create Aeronautics block discovery can now keep both halves in one Sable SubLevel."
        );
    }
}
