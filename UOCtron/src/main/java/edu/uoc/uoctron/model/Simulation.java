package edu.uoc.uoctron.model;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static edu.uoc.uoctron.controller.UOCtronController.normalizeType;

public class Simulation {
    private LocalDateTime startDateTime;
    private List<MinuteSimulationResult> results;

    public Simulation(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
        this.results = new ArrayList<>();
    }

    public void run(List<NuclearPlant> plants, List<Double> demand) {
        LocalDateTime currentTime = startDateTime;

        for (int minute = 0; minute < 2160; minute++) {
            double expectedDemand = demand.get(minute % demand.size());

            if (minute < 4) {
                results.add(new MinuteSimulationResult(currentTime, 0.0, expectedDemand, 0.0, new HashMap<>()));
                currentTime = currentTime.plusMinutes(1);
                continue;
            }

            boolean useWind = minute >= 7;
            boolean useGeothermal = minute >= 61;
            boolean useNuclear = minute >= 121;
            boolean useSolar = minute >= 500;
            boolean useThermal = minute >= 500;

            Map<String, Double> generatedByTypeMW = new LinkedHashMap<>();
            double totalGenerated = 0.0;

            List<NuclearPlant> renewables = plants.stream()
                    .filter(p -> p instanceof RenewablePlant)
                    .sorted(Comparator.comparing(p -> normalizeType(p.type)))
                    .collect(Collectors.toList());

            List<NuclearPlant> nuclear = plants.stream()
                    .filter(p -> !(p instanceof RenewablePlant) && !(p instanceof ThermalPlant))
                    .collect(Collectors.toList());

            List<NuclearPlant> thermals = plants.stream()
                    .filter(p -> p instanceof ThermalPlant)
                    .filter(p -> normalizeType(p.type).equals("Combined cycle") || normalizeType(p.type).equals("Coal"))
                    .collect(Collectors.toList());

            List<String> renewableOrder = new ArrayList<>();
            renewableOrder.add("Hydroelectric");
            if (useWind) renewableOrder.add("Wind");
            if (useGeothermal) renewableOrder.add("Geothermal");
            if (useSolar) renewableOrder.add("Solar");

            // Add Hydroelectric for minutes 4–7
            if (minute >= 4 && minute < 7) {
                generatedByTypeMW.put("Hydroelectric", 0.0);
            }

            // Process renewables in order
            for (String type : renewableOrder) {
                for (NuclearPlant p : renewables) {
                    if (!normalizeType(p.type).equals(type)) continue;
                    double remaining = expectedDemand - totalGenerated;
                    if (remaining <= 0 && !type.equals("Hydroelectric")) break;
                    double generated = Math.min(p.calculateElectricityGenerated(remaining), remaining);
                    if (generated > 0) {
                        generatedByTypeMW.merge(normalizeType(p.type), generated, Double::sum);
                        totalGenerated += generated;
                    }
                }
            }

            // Process nuclear plants if enabled
            if (useNuclear) {
                for (NuclearPlant p : nuclear) {
                    double remaining = expectedDemand - totalGenerated;
                    if (remaining <= 0) break;
                    double generated = Math.min(p.calculateElectricityGenerated(remaining), remaining);
                    if (generated > 0) {
                        generatedByTypeMW.merge(normalizeType(p.type), generated, Double::sum);
                        totalGenerated += generated;
                    }
                }
            }

            // Process thermal plants (only Combined cycle and Coal) if enabled
            if (useThermal) {
                for (NuclearPlant p : thermals) {
                    double remaining = expectedDemand - totalGenerated;
                    if (remaining <= 0) break;
                    double generated = Math.min(p.calculateElectricityGenerated(remaining), remaining);
                    if (generated > 0) {
                        generatedByTypeMW.merge(normalizeType(p.type), generated, Double::sum);
                        totalGenerated += generated;
                    }
                }
            }

            // Calculate average stability
            double weightedStabilitySum = 0.0;
            double totalWeight = 0.0;
            for (Map.Entry<String, Double> entry : generatedByTypeMW.entrySet()) {
                String type = entry.getKey();
                double generated = entry.getValue();
                Optional<NuclearPlant> opt = plants.stream()
                        .filter(p -> normalizeType(p.type).equals(type))
                        .findFirst();
                if (opt.isPresent()) {
                    weightedStabilitySum += opt.get().getStability() * generated;
                    totalWeight += generated;
                }
            }

            double averageStability = (totalWeight > 0) ? weightedStabilitySum / totalWeight : 0.0;

            // Skip stability adjustment for minutes 4–7
            if (minute >= 4 && minute < 7) {
                // Ensure Hydroelectric persists
                generatedByTypeMW.put("Hydroelectric", generatedByTypeMW.getOrDefault("Hydroelectric", 0.0));
            } else if (averageStability < 0.7 && totalWeight > 0) {
                List<Map.Entry<String, Double>> renewableEntries = generatedByTypeMW.entrySet().stream()
                        .filter(e -> renewables.stream().anyMatch(p -> normalizeType(p.type).equals(e.getKey())))
                        .sorted(Comparator.comparingDouble(e -> {
                            Optional<NuclearPlant> p = plants.stream().filter(pp -> normalizeType(pp.type).equals(e.getKey())).findFirst();
                            return p.map(NuclearPlant::getStability).orElse(1.0);
                        }))
                        .collect(Collectors.toList());

                for (Map.Entry<String, Double> entry : renewableEntries) {
                    String type = entry.getKey();
                    double amount = entry.getValue();
                    generatedByTypeMW.remove(type);
                    totalGenerated -= amount;

                    weightedStabilitySum = 0.0;
                    totalWeight = 0.0;
                    for (Map.Entry<String, Double> e : generatedByTypeMW.entrySet()) {
                        String t = e.getKey();
                        double g = e.getValue();
                        Optional<NuclearPlant> opt = plants.stream()
                                .filter(p -> normalizeType(p.type).equals(t))
                                .findFirst();
                        if (opt.isPresent()) {
                            weightedStabilitySum += opt.get().getStability() * g;
                            totalWeight += g;
                        }
                    }

                    averageStability = (totalWeight > 0) ? weightedStabilitySum / totalWeight : 0.0;
                    if (averageStability >= 0.7) break;
                }

                // Attempt to stabilize using nuclear if enabled
                if (averageStability < 0.7 && useNuclear) {
                    for (NuclearPlant p : nuclear) {
                        double remaining = expectedDemand - totalGenerated;
                        if (remaining <= 0) break;
                        double generated = Math.min(p.calculateElectricityGenerated(remaining), remaining);
                        if (generated > 0) {
                            generatedByTypeMW.merge(normalizeType(p.type), generated, Double::sum);
                            totalGenerated += generated;
                        }
                    }
                }

                // Attempt to stabilize using thermal (only Combined cycle and Coal) if enabled
                if (averageStability < 0.7 && useThermal) {
                    for (NuclearPlant p : thermals) {
                        double remaining = expectedDemand - totalGenerated;
                        if (remaining <= 0) break;
                        double generated = Math.min(p.calculateElectricityGenerated(remaining), remaining);
                        if (generated > 0) {
                            generatedByTypeMW.merge(normalizeType(p.type), generated, Double::sum);
                            totalGenerated += generated;
                        }
                    }
                }

                // Recalculate stability
                weightedStabilitySum = 0.0;
                totalWeight = 0.0;
                for (Map.Entry<String, Double> e : generatedByTypeMW.entrySet()) {
                    String t = e.getKey();
                    double g = e.getValue();
                    Optional<NuclearPlant> opt = plants.stream()
                            .filter(p -> normalizeType(p.type).equals(t))
                            .findFirst();
                    if (opt.isPresent()) {
                        weightedStabilitySum += opt.get().getStability() * g;
                        totalWeight += g;
                    }
                }
                averageStability = (totalWeight > 0) ? weightedStabilitySum / totalWeight : 0.0;
            }

            // Debug logging for minutes 4–7
            if (minute >= 4 && minute < 7) {
                System.out.println("Minute " + minute + ": generatedByTypeMW before result = " + generatedByTypeMW);
                MinuteSimulationResult result = new MinuteSimulationResult(currentTime, totalGenerated, expectedDemand, averageStability, generatedByTypeMW);
                // Assuming MinuteSimulationResult has a getter for generatedByTypeMW
                try {
                    java.lang.reflect.Method getGenByType = result.getClass().getMethod("getGeneratedByTypeMW");
                    Object genByType = getGenByType.invoke(result);
                    System.out.println("Minute " + minute + ": generatedByTypeMW from result = " + genByType);
                } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                    System.out.println("Minute " + minute + ": Could not access generatedByTypeMW from result: " + e.getMessage());
                }
                System.out.println("Minute " + minute + ": result created");
            }

            results.add(new MinuteSimulationResult(currentTime, totalGenerated, expectedDemand, averageStability, generatedByTypeMW));
            currentTime = currentTime.plusMinutes(1);
        }
    }

    public List<MinuteSimulationResult> getResults() {
        return results;
    }

    public String getResultsAsJSON() {
        return "{}";
    }
}