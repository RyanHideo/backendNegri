package com.sinapse.apiOzonio.modbus;

public enum TagType {
    COIL,   // Coil (0xxxx)
    DI,     // Discrete Input (1xxxx)
    HR,     // Holding Register (4xxxx)
    IR      // Input Register (3xxxx)
}
