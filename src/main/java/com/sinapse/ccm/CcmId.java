// src/main/java/com/sinapse/ccm/CcmId.java
package com.sinapse.ccm;

public enum CcmId {
    CCM1, CCM2;

    public static CcmId fromString(String raw) {
        if (raw == null) return CCM1; // default
        return switch (raw.toLowerCase()) {
            case "ccm1" -> CCM1;
            case "ccm2" -> CCM2;
            default -> CCM1;
        };
    }
}
