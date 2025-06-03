package edu.uoc.uoctron.model;

import java.time.Duration;

public class RenewablePlant extends NuclearPlant {

    private double efficiency = 1.0;

    public RenewablePlant(String name, String type, String city, double latitude, double longitude,
                          double maxCapacityMW, Duration availability, Duration restartTime,
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
        return "{ \"name\": \"" + name + "\", " +
                "\"type\": \"" + getAdjustedType() + "\", " +
                "\"city\": \"" + city + "\", " +
                "\"latitude\": " + latitude + ", " +
                "\"longitude\": " + longitude + ", " +
                "\"maxCapacityMW\": " + maxCapacityMW + ", " +
                "\"icon\": \"" + image + "\" }";
    }

    private String getAdjustedType() {
        switch (type.toLowerCase()) {
            case "hydro":
                return "Hydroelectric";
            case "combined_cycle":
                return "Combined cycle";
            case "fuel_gas":
                return "Fuel gas";
            default:
                // Capitaliza el primer carácter y deja el resto como está
                return type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
        }
    }

}
