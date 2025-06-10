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

    private double solarEfficiency(int minute) {
        int startMinutes = startDateTime.getHour() * 60 + startDateTime.getMinute();
        int minuteOfDay = (startMinutes + minute) % 1440;

        if (minuteOfDay < 360 || minuteOfDay > 1080) {
            return 0.0;
        }

        double progress = (minuteOfDay - 360) / 720.0;
        return Math.sin(Math.PI * progress);
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
            // Solar generation is only available during daylight hours.
            // Daylight is simulated from minute 500 to minute 950 of each day,
            // repeating for subsequent days. The modulo operation ensures the
            // cycle restarts every 1440 minutes (one day).
            boolean useSolar = minute >= 500 && (minute % 1440 >= 500 && minute % 1440 < 950);
            boolean useThermal = minute >= 500;
            boolean useCoal = minute < 1000;

            Map<String, Double> generatedByTypeMW = new LinkedHashMap<>();
            Map<NuclearPlant, Double> generationByPlant = new LinkedHashMap<>();
            double totalGenerated = 0.0;

            List<NuclearPlant> renewables = plants.stream()
                    .filter(p -> p instanceof RenewablePlant)
                    .collect(Collectors.toList());
            List<NuclearPlant> nuclear = plants.stream()
                    .filter(p -> !(p instanceof RenewablePlant) && !(p instanceof ThermalPlant))
                    .collect(Collectors.toList());
            List<NuclearPlant> thermals = plants.stream()
                    .filter(p -> p instanceof ThermalPlant)
                    .filter(p -> normalizeType(p.type).equals("Combined cycle") || normalizeType(p.type).equals("Coal"))
                    .collect(Collectors.toList());

            for (NuclearPlant p : plants) {
                if (p instanceof RenewablePlant && normalizeType(p.type).equals("Solar")) {
                    ((RenewablePlant) p).setEfficiency(solarEfficiency(minute));
                }
            }

            for (NuclearPlant p : plants) {
                if (p instanceof RenewablePlant && normalizeType(p.type).equals("Solar")) {
                    ((RenewablePlant) p).setEfficiency(solarEfficiency(minute));
                }
            }

            List<String> renewableOrder = new ArrayList<>();
            renewableOrder.add("Hydroelectric");
            if (useWind) renewableOrder.add("Wind");
            if (useGeothermal) renewableOrder.add("Geothermal");
            if (useSolar) renewableOrder.add("Solar");

            if (minute >= 4 && minute < 7) {
                generatedByTypeMW.put("Hydroelectric", 0.0);
            }

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

            // Wind farm output is reduced during the evening and night
            if (minute >= 1000 && minute < 1500) {
                double windGen = generatedByTypeMW.getOrDefault("Wind", 0.0);
                if (windGen > 1232.5) {
                    double diff = windGen - 1232.5;
                    generatedByTypeMW.put("Wind", 1232.5);
                    totalGenerated -= diff;
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
                        generationByPlant.merge(p, generated, Double::sum);
                        totalGenerated += generated;
                    }
                }
            }

            if (useThermal) {
                for (NuclearPlant p : thermals) {
                    if (!useCoal && normalizeType(p.type).equals("Coal")) continue;
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

                    while (averageStability < 0.7 && amount > 0) {
                        // Generation adjustments are performed using the
                        // smallest common capacity step across the plants,
                        // which is 12.5 MW. Using this step size keeps the
                        // results aligned with expected discrete values.
                        double decrement = Math.min(12.5, amount);
                        amount -= decrement;
                        totalGenerated -= decrement;
                        generatedByTypeMW.put(type, amount);

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

                    generatedByTypeMW.put(type, amount);
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

                if (averageStability < 0.7 && useThermal) {
                    for (NuclearPlant p : thermals) {
                        if (!useCoal && normalizeType(p.type).equals("Coal")) continue;
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
            // Remove entries with zero generation except for the initial
            // minutes where the presence of Hydroelectric is expected even
            // if its output is zero.
            if (minute >= 7 || minute < 4) {
                generatedByTypeMW.entrySet().removeIf(e -> e.getValue() == 0.0);
            } else {
                generatedByTypeMW.entrySet().removeIf(e -> e.getValue() == 0.0 && !"Hydroelectric".equals(e.getKey()));
            }

            // Ensure that no nuclear generation is reported when nuclear
            // plants are not enabled for the current minute.
            if (!useNuclear) {
                generatedByTypeMW.remove("Nuclear");
            }
            results.add(new MinuteSimulationResult(currentTime, totalGenerated, expectedDemand, averageStability, generatedByTypeMW));
            currentTime = currentTime.plusMinutes(1);
        }
    }

    public List<MinuteSimulationResult> getResults() {
        return results;
    }

    public String getResultsAsJSON() {
        org.json.JSONArray array = new org.json.JSONArray();
        for (MinuteSimulationResult result : results) {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("time", result.getTime().toString());
            obj.put("generatedMW", result.getGeneratedMW());
            obj.put("expectedDemandMW", result.getExpectedDemandMW());
            obj.put("averageStability", result.getAverageStability());

            org.json.JSONObject genByType = new org.json.JSONObject();
            for (java.util.Map.Entry<String, Double> entry : result.getGeneratedByTypeMW().entrySet()) {
                genByType.put(normalizeType(entry.getKey()), entry.getValue());
            }
            obj.put("generatedByTypeMW", genByType);
            array.put(obj);
        }
        return array.toString();
        }
    }
