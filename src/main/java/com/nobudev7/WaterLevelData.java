package com.nobudev7;

import java.time.LocalTime;

public class WaterLevelData {

    private final LocalTime time;
    private final double waterLevel;

    public WaterLevelData(LocalTime time, double waterLevel) {
        this.time = time;
        this.waterLevel = waterLevel;
    }

    public LocalTime getTime() {
        return time;
    }

    public double getWaterLevel() {
        return waterLevel;
    }
}
