package edu.uoc.uoctron.model;

import java.time.Duration;

public class NuclearPlant {

    protected String name;
    protected String type;
    protected String city;
    protected double latitude;
    protected double longitude;
    protected int maxCapacityMW;
    protected Duration availability;
    protected Duration restartTime;
    protected double stability;
    protected String image;

    public NuclearPlant(String name, String type, String city, double latitude, double longitude,
                        int maxCapacityMW, Duration availability, Duration restartTime,
                        double stability, String image) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (stability < 0 || stability > 1) {
            throw new IllegalArgumentException("Stability must be between 0 and 1");
        }
        this.name = name;
        this.type = type;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxCapacityMW = maxCapacityMW;
        this.availability = availability;
        this.restartTime = restartTime;
        this.stability = stability;
        this.image = image;
    }

    // Implementación de generación de electricidad (concreta)
    public double calculateElectricityGenerated(double demand) {
        return Math.min(maxCapacityMW, demand);
    }

    public double getStability() {
        return stability;
    }

    @Override
    public String toString() {
        return "{ \"name\": \"" + name + "\", \"type\": \"" + type + "\", \"city\": \"" + city + "\" }";
    }

    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public String getCity() { return city; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getMaxCapacityMW() { return maxCapacityMW; }
    public Duration getAvailability() { return availability; }
    public Duration getRestartTime() { return restartTime; }
    public String getImage() { return image; }
}
