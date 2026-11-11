package com.sshakusora.waystonessable.association;

import java.util.UUID;

/**
 * UUIDs are scoped to a dimension, including in the persistent reverse index.
 */
public record SubLevelKey(String dimension, UUID id) {
}
