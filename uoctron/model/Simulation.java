package edu.uoc.uoctron.model;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class Simulation {
    private LocalDateTime startDateTime;
    private List<MinuteSimulationResult> results;

    public Simulation(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
        this.results = new ArrayList<>();
    }

    public void run(List<NuclearPlant> plants, List<Double> demand) {
        LocalDateTime currentTime = startDateTime;

        for (int minute = 0; minute < 2160; minute++) {  //
            double expectedDemand = demand.get(minute % demand.size()); //

            Map<String, Double> generatedByTypeMW = new HashMap<>();
            double totalGenerated = 0.0;

            // Separar por tipo
            List<NuclearPlant> renewables = plants.stream()
                    .filter(p -> p instanceof RenewablePlant)
                    .sorted(Comparator.comparingDouble(NuclearPlant::getStability).reversed())
                    .collect(Collectors.toList());

            List<NuclearPlant> nuclear = plants.stream()
                    .filter(p -> p instanceof NuclearPlant)
                    .collect(Collectors.toList());

            List<NuclearPlant> thermals = plants.stream()
                    .filter(p -> p instanceof ThermalPlant)
                    .sorted(Comparator.comparingDouble(NuclearPlant::getStability).reversed())
                    .collect(Collectors.toList());

            // 1. Generar con renovables
            for (NuclearPlant p : renewables) {
                double generated = Math.min(p.calculateElectricityGenerated(expectedDemand - totalGenerated),
                        expectedDemand - totalGenerated);
                if (generated > 0) {
                    generatedByTypeMW.merge(p.type, generated, Double::sum);
                    totalGenerated += generated;
                }
                if (totalGenerated >= expectedDemand) break;
            }

            // 2. Si falta, generar con nucleares
            if (totalGenerated < expectedDemand) {
                for (NuclearPlant p : nuclear) {
                    double generated = Math.min(p.calculateElectricityGenerated(expectedDemand - totalGenerated),
                            expectedDemand - totalGenerated);
                    if (generated > 0) {
                        generatedByTypeMW.merge(p.type, generated, Double::sum);
                        totalGenerated += generated;
                    }
                    if (totalGenerated >= expectedDemand) break;
                }
            }

            // 3. Si sigue faltando, térmicas
            if (totalGenerated < expectedDemand) {
                for (NuclearPlant p : thermals) {
                    double generated = Math.min(p.calculateElectricityGenerated(expectedDemand - totalGenerated),
                            expectedDemand - totalGenerated);
                    if (generated > 0) {
                        generatedByTypeMW.merge(p.type, generated, Double::sum);
                        totalGenerated += generated;
                    }
                    if (totalGenerated >= expectedDemand) break;
                }
            }

            // 4. Calcular estabilidad media ponderada
            double weightedStabilitySum = 0.0;
            double totalWeight = 0.0;
            for (Map.Entry<String, Double> entry : generatedByTypeMW.entrySet()) {
                String type = entry.getKey();
                double generated = entry.getValue();

                // Buscar planta por tipo (asumimos que todas las del mismo tipo tienen misma estabilidad)
                Optional<NuclearPlant> optPlant = plants.stream()
                        .filter(p -> p.type.equals(type))
                        .findFirst();
                if (optPlant.isPresent()) {
                    weightedStabilitySum += optPlant.get().getStability() * generated;
                    totalWeight += generated;
                }
            }
            double averageStability = (totalWeight > 0) ? weightedStabilitySum / totalWeight : 0.0;

            // 5. Ajustar si estabilidad < 0.7 (recortar renovables menos estables)
            if (averageStability < 0.7 && !renewables.isEmpty()) {
                renewables.sort(Comparator.comparingDouble(NuclearPlant::getStability));
                for (NuclearPlant p : renewables) {
                    double reduction = generatedByTypeMW.getOrDefault(p.type, 0.0);
                    if (reduction > 0) {
                        totalGenerated -= reduction;
                        generatedByTypeMW.remove(p.type);
                        // Recalcular estabilidad promedio
                        weightedStabilitySum = 0.0;
                        totalWeight = 0.0;
                        for (Map.Entry<String, Double> entry : generatedByTypeMW.entrySet()) {
                            String type = entry.getKey();
                            double generated = entry.getValue();
                            Optional<NuclearPlant> optPlant = plants.stream()
                                    .filter(pp -> pp.type.equals(type))
                                    .findFirst();
                            if (optPlant.isPresent()) {
                                weightedStabilitySum += optPlant.get().getStability() * generated;
                                totalWeight += generated;
                            }
                        }
                        averageStability = (totalWeight > 0) ? weightedStabilitySum / totalWeight : 0.0;
                        if (averageStability >= 0.7) break;
                    }
                }

                // Volver a llenar con nucleares y térmicas si falta electricidad
                if (totalGenerated < expectedDemand) {
                    for (NuclearPlant p : nuclear) {
                        double generated = Math.min(p.calculateElectricityGenerated(expectedDemand - totalGenerated),
                                expectedDemand - totalGenerated);
                        if (generated > 0) {
                            generatedByTypeMW.merge(p.type, generated, Double::sum);
                            totalGenerated += generated;
                        }
                        if (totalGenerated >= expectedDemand) break;
                    }
                    if (totalGenerated < expectedDemand) {
                        for (NuclearPlant p : thermals) {
                            double generated = Math.min(p.calculateElectricityGenerated(expectedDemand - totalGenerated),
                                    expectedDemand - totalGenerated);
                            if (generated > 0) {
                                generatedByTypeMW.merge(p.type, generated, Double::sum);
                                totalGenerated += generated;
                            }
                            if (totalGenerated >= expectedDemand) break;
                        }
                    }
                }
            }

            // 6. Guardar resultado de este minuto
            MinuteSimulationResult result = new MinuteSimulationResult(
                    currentTime, totalGenerated, expectedDemand, averageStability, generatedByTypeMW);
            results.add(result);

            // Siguiente minuto
            currentTime = currentTime.plusMinutes(1);
        }
    }

    public List<MinuteSimulationResult> getResults() {
        return results;
    }

    public String getResultsAsJSON() {
        // Opcionalmente se puede usar org.json aquí
        return "{}";
    }
}
