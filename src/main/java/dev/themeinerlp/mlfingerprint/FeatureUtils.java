package dev.themeinerlp.mlfingerprint;

final class FeatureUtils {
    private  FeatureUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    static double calcEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0.0;
        int[] freq = new int[256];
        for (byte b : data) freq[b & 0xFF]++;
        double ent = 0.0;
        int len = data.length;
        for (int c : freq) {
            if (c > 0) {
                double p = (double) c / len;
                ent -= p * (Math.log(p) / Math.log(2));
            }
        }
        return ent;
    }
}
