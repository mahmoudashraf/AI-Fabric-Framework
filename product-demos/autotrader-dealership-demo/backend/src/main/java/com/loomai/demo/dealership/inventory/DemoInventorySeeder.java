package com.loomai.demo.dealership.inventory;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DemoInventorySeeder implements ApplicationRunner {

    private final VehicleRepository repository;
    private final DealershipDemoProperties properties;

    public DemoInventorySeeder(VehicleRepository repository, DealershipDemoProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Instant updated = Instant.now().minus(18, ChronoUnit.MINUTES);
        fixtures(updated).forEach(repository::insert);
    }

    private List<Vehicle> fixtures(Instant updated) {
        String source = properties.getSourceLabel();
        return List.of(
            vehicle("veh-aster-e1", "DEMO-1001", "aster-e1-motion", "Aster", "E1", "Motion Long Range", 2025, 31_950_00L, 4_850, "Electric", "Automatic", "SUV", "Ocean blue", 5, 5, 298, "Northfield Central", "A quiet, roomy electric SUV with confident motorway range and a flexible family cabin.", List.of("Adaptive cruise control", "Heat pump", "360-degree camera", "Wireless phone integration"), "/assets/demos/dealership/vehicle-01.webp", source, updated),
            vehicle("veh-northstar-s4", "DEMO-1002", "northstar-s4-touring", "Northstar", "S4", "Touring Hybrid", 2024, 27_400_00L, 8_920, "Hybrid", "Automatic", "Estate", "Graphite", 5, 5, null, "Northfield Central", "A practical hybrid estate for long journeys, luggage and low-speed electric driving around town.", List.of("Blind-spot monitoring", "Heated front seats", "Powered tailgate", "Roof rails"), "/assets/demos/dealership/vehicle-02.webp", source, updated.minusSeconds(90)),
            vehicle("veh-morrow-c2", "DEMO-1003", "morrow-c2-city", "Morrow", "C2", "City Electric", 2025, 22_750_00L, 1_980, "Electric", "Automatic", "Hatchback", "Pearl white", 5, 5, 241, "Northfield Riverside", "A compact electric hatchback with simple controls, easy parking and useful everyday range.", List.of("Rear parking camera", "Rapid charging", "Heated steering wheel", "Traffic-sign recognition"), "/assets/demos/dealership/vehicle-03.webp", source, updated.minusSeconds(180)),
            vehicle("veh-caldera-x6", "DEMO-1004", "caldera-x6-adventure", "Caldera", "X6", "Adventure AWD", 2023, 29_800_00L, 17_420, "Petrol", "Automatic", "SUV", "Forest green", 5, 5, null, "Northfield Central", "A composed all-wheel-drive SUV with generous space, towing capability and strong all-weather equipment.", List.of("All-wheel drive", "Tow bar preparation", "Panoramic roof", "Matrix LED headlights"), "/assets/demos/dealership/vehicle-04.webp", source, updated.minusSeconds(270)),
            vehicle("veh-arden-v3", "DEMO-1005", "arden-v3-executive", "Arden", "V3", "Executive Plug-in Hybrid", 2024, 34_600_00L, 6_310, "Plug-in hybrid", "Automatic", "Saloon", "Midnight blue", 4, 5, 48, "Northfield Riverside", "A refined plug-in hybrid saloon with a calm cabin and enough electric range for many local commutes.", List.of("Electric memory seats", "Premium audio", "Adaptive headlights", "Phone-as-key"), "/assets/demos/dealership/vehicle-05.webp", source, updated.minusSeconds(360)),
            vehicle("veh-aster-e2", "DEMO-1006", "aster-e2-sport", "Aster", "E2", "Sport Dual Motor", 2025, 39_250_00L, 3_760, "Electric", "Automatic", "Crossover", "Silver", 5, 5, 276, "Northfield Central", "A responsive dual-motor crossover combining brisk performance with useful range and a versatile cabin.", List.of("Dual-motor all-wheel drive", "Vehicle-to-load power", "Head-up display", "Performance seats"), "/assets/demos/dealership/vehicle-04.webp", source, updated.minusSeconds(450))
        );
    }

    private Vehicle vehicle(String id, String stockId, String slug, String make, String model,
                            String derivative, int year, long priceMinor, int mileage, String fuel,
                            String transmission, String bodyType, String colour, int doors, int seats,
                            Integer range, String location, String summary, List<String> features,
                            String imagePath, String source, Instant updated) {
        return new Vehicle(id, stockId, slug, make, model, derivative, year, priceMinor, "GBP", mileage,
            fuel, transmission, bodyType, colour, doors, seats, range, location, "ACTIVE", summary,
            features, imagePath, source, updated, 1L);
    }
}
