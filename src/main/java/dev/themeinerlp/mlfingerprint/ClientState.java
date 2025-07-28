package dev.themeinerlp.mlfingerprint;

/**
 * Represents the state of a client in the ML Fingerprint plugin.
 * This class holds the last timestamp of the client, which can be used to track activity,
 * and a packet counter to track the number of packets processed.
 */
final class ClientState {
    long lastTimestamp = 0;
    int packetCount = 0;
}
