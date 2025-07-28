package dev.themeinerlp.mlfingerprint;

/**
 * Represents the state of a client in the ML Fingerprint plugin.
 * This class holds various information about the client, including:
 * - The last timestamp of the client, which can be used to track activity
 * - A packet counter to track the number of packets processed
 * - The last evaluation time, which is used to determine when to re-evaluate the client
 * - The last client type and percentage from the evaluation
 */
final class ClientState {
    long lastTimestamp = 0;
    int packetCount = 0;
    
    // Fields for client evaluation
    long lastEvaluationTime = 0;
    String lastClientType = null;
    double lastPercentage = 0.0;
}
