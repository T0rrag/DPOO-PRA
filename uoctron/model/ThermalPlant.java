package edu.uoc.uoctron.model;

import java.time.Duration;

public class ThermalPlant extends NuclearPlant {
    private FuelType fuelType;

    public ThermalPlant(String name, String type, String city, double latitude, double longitude,
                        double maxCapacityMW, Duration availability, Duration restartTime,
                        double stability, String image, FuelType fuelType) {
        super(name, type, city, latitude, longitude, maxCapacityMW, availability, restartTime, stability, image);
        this.fuelType = fuelType;
    }

    @Override
    public double calculateElectricityGenerated(double demand) {
        // Genera hasta maxCapacityMW
        return maxCapacityMW;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    @Override
    public String toString() {
        return super.toString() + ", \"fuelType\": \"" + fuelType + "\" }";
    }
}
