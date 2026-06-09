package com.nobudev7;

import java.time.ZonedDateTime;

public class WaterLevelData {

    private final ZonedDateTime time;
    private final double waterLevel;

    public WaterLevelData(ZonedDateTime time, double waterLevel) {
        this.time = time;
        this.waterLevel = waterLevel;
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public double getWaterLevel() {
        return waterLevel;
    }
}
