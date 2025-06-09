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
                switch (type.toLowerCase()) {
                    case "nuclear" -> plant = new NuclearPlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofDays(1), 1.0, "nuclear.png");
                    case "coal" -> plant = new ThermalPlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofHours(8), 0.9, "coal.png", FuelType.COAL);
                    case "fuel_gas" -> plant = new ThermalPlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofHours(4), 0.6, "fuel_gas.png", FuelType.FUEL_GAS);
                    case "combined_cycle" -> plant = new ThermalPlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofHours(2), 0.7, "combined_cycle.png", FuelType.COMBINED_CYCLE);
                    case "biomass" -> plant = new ThermalPlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofHours(3), 0.5, "biomass.png", FuelType.BIOMASS);
                    case "hydro" -> plant = new RenewablePlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofMinutes(3), 0.8, "hydro.png");
                    case "solar" -> plant = new RenewablePlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofMinutes(6), 0.1, "solar.png");
                    case "wind" -> plant = new RenewablePlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofMinutes(6), 0.2, "wind.png");
                    case "geothermal" -> plant = new RenewablePlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofHours(1), 0.7, "geothermal.png");
                    default -> plant = new RenewablePlant(name, type, city, latitude, longitude,
                            maxCapacityMW, java.time.Duration.ZERO, java.time.Duration.ofMinutes(6), 0.7, "default.png");
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
        if (currentSimulation == null) return array;

        for (MinuteSimulationResult result : currentSimulation.getResults()) {
            JSONObject obj = new JSONObject();
            obj.put("time", result.getTime().toString());
            obj.put("generatedMW", result.getGeneratedMW());
            obj.put("expectedDemandMW", result.getExpectedDemandMW());
            obj.put("averageStability", result.getAverageStability());

            JSONObject genByType = new JSONObject();
            for (Map.Entry<String, Double> entry : result.getGeneratedByTypeMW().entrySet()) {
                String normalizedKey = normalizeType(entry.getKey());
                genByType.put(normalizedKey, entry.getValue());
            }

            obj.put("generatedByTypeMW", genByType);
            array.put(obj);
        }
        return array;
    }

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

    public static String normalizeType(String type) {
        return switch (type.toLowerCase()) {
            case "solar" -> "Solar";
            case "wind" -> "Wind";
            case "hydro" -> "Hydroelectric";
            case "coal" -> "Coal";
            case "nuclear" -> "Nuclear";
            case "geothermal" -> "Geothermal";
            case "biomass" -> "Biomass";
            case "fuel_gas" -> "Fuel gas";
            case "combined_cycle" -> "Combined cycle";
            default -> type;
        };
    }
}