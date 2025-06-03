package edu.uoc.uoctron.controller;

import edu.uoc.uoctron.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public class UOCtronController {

    private List<NuclearPlant> plants;
    private Map<LocalTime, Double> minuteDemand;
    private Simulation currentSimulation;

    public UOCtronController(String plantsFile, String demandFile) {
        plants = new LinkedList<>();
        minuteDemand = new LinkedHashMap<>();
        loadPlants(plantsFile);
        loadMinuteDemand(demandFile);
    }

    private void loadPlants(String filename) {
        try (var is = getClass().getResourceAsStream("/data/" + filename);
             var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;

                String[] parts = line.split(",", 6);
                if (parts.length < 6) continue;

                String type = capitalize(parts[0].trim());
                String name = parts[1].trim();
                double latitude = Double.parseDouble(parts[2].trim());
                double longitude = Double.parseDouble(parts[3].trim());
                String city = parts[4].trim();
                double maxCapacityMW = Double.parseDouble(parts[5].trim());

                NuclearPlant plant;
                if (type.equalsIgnoreCase("Nuclear")) {
                    plant = new NuclearPlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofDays(1), 1.0, "nuclear.png");
                } else if (type.equalsIgnoreCase("Coal") || type.equalsIgnoreCase("Fuel_gas") || type.equalsIgnoreCase("Combined_cycle")) {
                    FuelType fuelType = FuelType.valueOf(type.toUpperCase());
                    plant = new ThermalPlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofHours(4), 0.8, "thermal.png", fuelType);
                } else {
                    plant = new RenewablePlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofMinutes(6), 0.7, "renewable.png");
                }

                plants.add(plant);
            }

        } catch (Exception e) {
            System.err.println("Error reading plants file: " + e.getMessage());
        }
    }

    private void loadMinuteDemand(String filename) {
        try (var is = getClass().getResourceAsStream("/data/" + filename);
             var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;

                String[] parts = line.split(",", 2);
                if (parts.length != 2) continue;

                LocalTime time = LocalTime.parse(parts[0].trim());
                double demand = Double.parseDouble(parts[1].trim());
                addMinuteDemand(time, demand);
            }

        } catch (Exception e) {
            System.err.println("Error reading demand forecast file: " + e.getMessage());
        }
    }

    private void addMinuteDemand(LocalTime time, double demand) {
        minuteDemand.put(time, demand);
    }

    public NuclearPlant[] getNuclearPlants() {
        return plants.toArray(new NuclearPlant[0]);
    }

    public void runBlackoutSimulation(LocalDateTime blackoutStart) {
        List<Double> demands = new ArrayList<>(minuteDemand.values());
        currentSimulation = new Simulation(blackoutStart);
        currentSimulation.run(plants, demands);
    }

    public JSONArray getSimulationResults() {
        JSONArray array = new JSONArray();
        for (MinuteSimulationResult result : currentSimulation.getResults()) {
            JSONObject obj = new JSONObject();
            obj.put("time", result.getTime().toString());
            obj.put("generatedMW", result.getGeneratedMW());
            obj.put("expectedDemandMW", result.getExpectedDemandMW());
            obj.put("averageStability", result.getAverageStability());
            obj.put("generatedByTypeMW", result.getGeneratedByTypeMW());
            array.put(obj);
        }
        return array;
    }

    // Método final y clave para los tests
    public JSONArray getPlantsAsJSON() {
        JSONArray array = new JSONArray();
        for (NuclearPlant plant : plants) {
            JSONObject obj = new JSONObject();
            obj.put("name", plant.getName());
            obj.put("type", plant.getType());
            obj.put("city", plant.getCity());
            obj.put("latitude", plant.getLatitude());
            obj.put("longitude", plant.getLongitude());
            obj.put("maxCapacityMW", plant.getMaxCapacityMW());
            array.put(obj);
        }
        return array;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
