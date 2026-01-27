package com.sinapse.ccm.modbus;

public enum TagType {
    HR,   // Holding Register 4xxxx
    IR,   // Input Register    3xxxx
    COIL, // Coils             0xxxx
    DI    // Discrete Inputs   1xxxx
}
