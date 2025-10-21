package com.sinapse.apiOzonio.modbus;

import java.time.Instant;

public record TagValue(String name, double value, Instant ts) {}