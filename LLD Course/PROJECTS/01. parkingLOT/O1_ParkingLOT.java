
import java.util.*;

import java.time.LocalDateTime;
// import java.util.UUID;

// Enums first — always. Prevents magic strings like "CAR" or "COMPACT".
// Type-safe, autocomplete-friendly, impossible to typo.
// SOLID: Open/Closed — add a new type without changing existing logic.
enum VehicleType {
    TWO_WHEELER, COMPACT, LARGE
}

enum SpotType {
    TWO_WHEELER, COMPACT, LARGE
}

enum SpotStatus {
    AVAILABLE, OCCUPIED
}

// ── SOLID: Single Responsibility ──────────────────────────────────────────
// Vehicle knows ONLY about vehicle identity. No parking logic here.
// ── SOLID: Open/Closed ────────────────────────────────────────────────────
// Adding a new vehicle type (e.g. Bus) = new subclass. No changes to Vehicle.
abstract class Vehicle {

    private final String licensePlate;     // final = immutable after creation
    private final VehicleType vehicleType;

    public Vehicle(String licensePlate, VehicleType vehicleType) {
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }
}

// Each subclass is 3 lines. That's the power of inheritance done right.
class Car extends Vehicle {

    public Car(String p) {
        super(p, VehicleType.COMPACT);
    }
}

class Bike extends Vehicle {

    public Bike(String p) {
        super(p, VehicleType.TWO_WHEELER);
    }
}

class Truck extends Vehicle {

    public Truck(String p) {
        super(p, VehicleType.LARGE);
    }
}

// ── SOLID: Single Responsibility ──────────────────────────────────────────
// ParkingSpot manages only its own state — available/occupied, vehicle ref.
// It does NOT decide which vehicle to accept. That's ParkingFloor's job.
// ── SOLID: Encapsulation ──────────────────────────────────────────────────
// All fields private. State changes only through parkVehicle()/removeVehicle().
class ParkingSpot {

    private final String spotNumber;
    private final SpotType spotType;
    private SpotStatus status;
    private Vehicle parkedVehicle;

    public ParkingSpot(String spotNumber, SpotType spotType) {
        this.spotNumber = spotNumber;
        this.spotType = spotType;
        this.status = SpotStatus.AVAILABLE;
        this.parkedVehicle = null;
    }

    // Key insight: SpotType.COMPACT.name() == VehicleType.COMPACT.name()
    // Same enum names = no switch/if needed. Clean matching in one line.
    public boolean isAvailableForVehicle(Vehicle vehicle) {
        return status == SpotStatus.AVAILABLE
                && spotType.name().equals(vehicle.getVehicleType().name());
    }

    public void parkVehicle(Vehicle vehicle) {
        this.parkedVehicle = vehicle;
        this.status = SpotStatus.OCCUPIED;
    }

    public void removeVehicle() {
        this.parkedVehicle = null;
        this.status = SpotStatus.AVAILABLE;
    }

    public String getSpotNumber() {
        return spotNumber;
    }

    public SpotType getSpotType() {
        return spotType;
    }

    public SpotStatus getStatus() {
        return status;
    }

    public Vehicle getParkedVehicle() {
        return parkedVehicle;
    }

    public boolean isEmpty() {
        return status == SpotStatus.AVAILABLE;
    }
}

// ── SOLID: Single Responsibility ──────────────────────────────────────────
// Ticket is a VALUE OBJECT. It just holds data. No business logic.
// If we add pricing later, we add a FeeCalculator class — we don't touch this.
class ParkingTicket {

    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot allocatedSpot;
    private final LocalDateTime entryTime;

    public ParkingTicket(Vehicle vehicle, ParkingSpot allocatedSpot) {
        this.ticketId = UUID.randomUUID().toString();
        this.vehicle = vehicle;
        this.allocatedSpot = allocatedSpot;
        this.entryTime = LocalDateTime.now();
    }

    public String getTicketId() {
        return ticketId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ParkingSpot getAllocatedSpot() {
        return allocatedSpot;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }
}

// ── SOLID: Single Responsibility ──────────────────────────────────────────
// ParkingFloor manages spots on ONE floor. Does not touch other floors.
// ── SOLID: Open/Closed ────────────────────────────────────────────────────
// To add a "VIP floor" — extend ParkingFloor, don't modify it.
class ParkingFloor {

    private final int floorNumber;
    private final List<ParkingSpot> spots;

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
    }

    public void addSpot(ParkingSpot spot) {
        spots.add(spot);
    }

    // Returns first available matching spot, or null if floor is full
    public ParkingSpot getAvailableSpot(Vehicle vehicle) {
        return spots.stream()
                .filter(s -> s.isAvailableForVehicle(vehicle))
                .findFirst()
                .orElse(null);
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public List<ParkingSpot> getSpots() {
        return spots;
    }
}

// ── Design Pattern: SINGLETON ─────────────────────────────────────────────
// There is exactly ONE parking lot. Singleton guarantees this.
// If you could call new ParkingLot() twice, both would think they own all spots.
// 'synchronized' makes it thread-safe — mention this to the interviewer!
// ── SOLID: Dependency Inversion ───────────────────────────────────────────
// ParkingLot depends on ParkingFloor abstraction, not concrete spot logic.
class ParkingLot {

    // ── Singleton: step 1 — private static holder ─────────────────
    private static volatile ParkingLot instance;
    // volatile = ensures visibility across threads (double-checked locking)

    // ── Singleton: step 2 — private constructor ────────────────────
    private ParkingLot() {
        floors = new ArrayList<>();
        activeTickets = new HashMap<>();
    }

    // ── Singleton: step 3 — public accessor with double-checked lock ─
    public static ParkingLot getInstance() {
        if (instance == null) {                         // first check (no lock)
            synchronized (ParkingLot.class) {
                if (instance == null) {                  // second check (with lock)
                    instance = new ParkingLot();
                }
            }
        }
        return instance;
    }

    private List<ParkingFloor> floors;
    private Map<String, ParkingTicket> activeTickets;  // ticketId → ticket

    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    // ── PARK: entry flow ──────────────────────────────────────────────
    // 1. Scan floors for an available matching spot
    // 2. Park vehicle, create ticket, store in map
    // 3. Return ticket (null = lot is full for this type)
    public ParkingTicket park(Vehicle vehicle) {
        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.getAvailableSpot(vehicle);
            if (spot != null) {
                spot.parkVehicle(vehicle);
                ParkingTicket ticket = new ParkingTicket(vehicle, spot);
                activeTickets.put(ticket.getTicketId(), ticket);
                System.out.printf("[PARKED]  %s → Spot %s (Floor %d)%n",
                        vehicle.getLicensePlate(), spot.getSpotNumber(), floor.getFloorNumber());
                return ticket;
            }
        }
        System.out.printf("[FULL]    No spot for %s (%s)%n",
                vehicle.getLicensePlate(), vehicle.getVehicleType());
        return null;
    }

    // ── UNPARK: exit flow ─────────────────────────────────────────────
    // 1. Look up ticket by ID
    // 2. Free the spot, remove ticket from map
    public boolean unpark(String ticketId) {
        ParkingTicket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            System.out.println("[ERROR]   Invalid ticket: " + ticketId);
            return false;
        }
        ticket.getAllocatedSpot().removeVehicle();
        activeTickets.remove(ticketId);
        System.out.printf("[EXITED]  %s freed spot %s%n",
                ticket.getVehicle().getLicensePlate(),
                ticket.getAllocatedSpot().getSpotNumber());
        return true;
    }

    public void displayStatus() {
        System.out.println("\n╔══ Parking Lot Status ══╗");
        for (ParkingFloor f : floors) {
            System.out.println("  Floor " + f.getFloorNumber() + ":");
            for (ParkingSpot s : f.getSpots()) {
                String icon = s.isEmpty() ? "○" : "●";
                System.out.printf("    %s %s (%s)%n", icon, s.getSpotNumber(), s.getSpotType());
            }
        }
        System.out.println("╚════════════════════════╝\n");
    }
}

public class O1_ParkingLOT {

    public static void main(String[] args) {

        // Singleton — only one instance possible
        ParkingLot lot = ParkingLot.getInstance();

        // Setup floors
        ParkingFloor f1 = new ParkingFloor(1);
        f1.addSpot(new ParkingSpot("F1-S01", SpotType.TWO_WHEELER));
        f1.addSpot(new ParkingSpot("F1-S02", SpotType.COMPACT));
        f1.addSpot(new ParkingSpot("F1-S03", SpotType.COMPACT));
        f1.addSpot(new ParkingSpot("F1-S04", SpotType.LARGE));

        ParkingFloor f2 = new ParkingFloor(2);
        f2.addSpot(new ParkingSpot("F2-S01", SpotType.COMPACT));
        f2.addSpot(new ParkingSpot("F2-S02", SpotType.LARGE));

        lot.addFloor(f1);
        lot.addFloor(f2);
        lot.displayStatus();

        // Park vehicles
        ParkingTicket t1 = lot.park(new Bike("KA-BIKE-01"));   // → F1-S01
        ParkingTicket t2 = lot.park(new Car("KA-CAR-01"));    // → F1-S02
        ParkingTicket t3 = lot.park(new Car("KA-CAR-02"));    // → F1-S03
        ParkingTicket t4 = lot.park(new Truck("KA-TRK-01"));  // → F1-S04
        lot.displayStatus();

        // Unpark a car — spot should free up
        if (t2 != null) {
            lot.unpark(t2.getTicketId());
        }

        // New car takes the freed spot
        lot.park(new Car("KA-CAR-03"));  // → F1-S02 again
        lot.displayStatus();
    }
}
