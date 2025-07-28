package dev.themeinerlp.mlfingerprint;

/**
 * Represents the state of a client in the ML Fingerprint plugin.
 * This class holds the last timestamp of the client, which can be used to track activity.
 */
final class ClientState {
    long lastTimestamp = 0;
}
