package edu.uoc.uoctron.model;

import java.time.Duration;

public class RenewablePlant extends NuclearPlant {

    private double efficiency = 1.0;

    public RenewablePlant(String name, String type, String city, double latitude, double longitude,
                          int maxCapacityMW, Duration availability, Duration restartTime,
                          double stability, String image) {
        super(name, type, city, latitude, longitude, maxCapacityMW, availability, restartTime, stability, image);
    }

    @Override
    public double calculateElectricityGenerated(double demand) {
        // Genera hasta maxCapacityMW * eficiencia, sin exceder la demanda
        double generated = maxCapacityMW * efficiency;
        return Math.min(generated, demand);
    }

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }

    @Override
    public String toString() {
        return "{ \"name\": \"" + name + "\", \"type\": \"" + type + "\", \"city\": \"" + city + "\", \"efficiency\": " + efficiency + " }";
    }
}
