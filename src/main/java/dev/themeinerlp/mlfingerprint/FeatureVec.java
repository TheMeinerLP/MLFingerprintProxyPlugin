package dev.themeinerlp.mlfingerprint;

final class FeatureVec {
    String clientId;
    int packetId;
    int length;
    Direction direction;
    long iat;
    double entropy;
    long timestamp;

    FeatureVec(String cid, int pid, int len, Direction d, long iat, double e, long ts) {
        this.clientId = cid;
        this.packetId = pid;
        this.length = len;
        this.direction = d;
        this.iat = iat;
        this.entropy = e;
        this.timestamp = ts;
    }
}
