
import java.util.*;

// ============================================================
// 1. ENUMS
// ============================================================
enum RideStatus { 
    REQUESTED, ACCEPTED, ONGOING, COMPLETED, CANCELLED
}

enum VehicleType {
    MINI, SEDAN, SUV
}

enum DriverStatus {
    AVAILABLE, ON_TRIDE, OFFLINE
}

/* ============================================================
// 2. VALUE OBJECT — Location
// SOLID (SRP): only responsible for representing a point + distance math.
// Kept immutable — value objects should never mutate.
Imagine a classic right-angled triangle on a graph:
Point A (This Location): 
(Latitude: 1, Longitude: 1)Point B (Other Location): 
(Latitude: 4, Longitude: 5)
dx = 1 - 4 = -3
dy = 1 - 5 = -4
dx * dx = 9
dy * dy = 16
Sum = 9 + 16 = 25
Math.sqrt(25) = 5.0 
(The distance between them is 5 units)
============================================================*/
class Location {

    private final double lat;
    private final double lng;

    public Location(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    // Simple Euclidean distance (say out loud: "in reality I'd use Haversine
    // formula for lat/lng, but keeping this simple for interview scope")
    // Pythagorean Theorem ((a^2 + b^2 = c^2)) on a flat 2D grid.
    public double distanceTo(Location other) {
        double dx = this.lat - other.lat; //  Find the Horizontal Distance (dx)
        double dy = this.lng - other.lng; // double dy = this.lng - other.lng;
        return Math.sqrt(dx * dx + dy * dy); //  Find the Square Root
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}

// ============================================================
// 3. DATA CLASSES — User hierarchy
// SOLID (SRP): each class only holds its own identity/profile data.
// Rider/Driver extend a common User instead of duplicating id/name fields —
// avoids repetition without over-engineering an interface for two fields.
// ============================================================
abstract class User {

    private final String id;
    private final String name;

    public User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

class Rider extends User {

    public Rider(String id, String name) {
        super(id, name);
    }
}

class Vehicle {

    private final String vehicleNumber;
    private final VehicleType type;

    public Vehicle(String vehicleNumber, VehicleType type) {
        this.vehicleNumber = vehicleNumber;
        this.type = type;
    }

    public VehicleType getType() {
        return type;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }
}

// ============================================================
// 4. DRIVER — mutable state (location + availability)
// SOLID (SRP): Driver only manages its own status/location.
// synchronized on status change — same reasoning as ShowSeat.tryLock():
// two ride requests must not match the same driver concurrently.
// ============================================================
class Driver extends User {

    private final Vehicle vehicle;
    private Location currentLocation;
    private DriverStatus status = DriverStatus.AVAILABLE;

    public Driver(String id, String name, Vehicle vehicle, Location startLocation) {
        super(id, name);
        this.vehicle = vehicle;
        this.currentLocation = startLocation;
    }

    // Atomic check-then-set, exactly like ShowSeat.tryLock() in BookMyShow —
    // prevents two concurrent ride requests from both matching this driver.
    public synchronized boolean tryAssign() {
        if (status != DriverStatus.AVAILABLE) {
            return false;
        }
        status = DriverStatus.ON_TRIDE;
        return true;
    }

    public synchronized void free() {
        status = DriverStatus.AVAILABLE;
    }

    public void updateLocation(Location loc) {
        this.currentLocation = loc;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }
}

// ============================================================
// 5. STRATEGY PATTERN — Driver Matching
// SOLID (OCP): new matching algorithms (e.g. surge-aware, ML-ranked) are
// added as new classes — RideManager never changes.
// SOLID (DIP): RideManager depends on the interface, not a concrete impl.
// ============================================================
interface DriverMatchingStrategy {

    Driver findDriver(Location pickup, List<Driver> allDrivers, VehicleType type);
}

class NearestDriverStrategy implements DriverMatchingStrategy {

    @Override
    public Driver findDriver(Location pickup, List<Driver> allDrivers, VehicleType type) {
        Driver best = null;
        double bestDist = Double.MAX_VALUE;
        for (Driver d : allDrivers) {
            if (d.getStatus() != DriverStatus.AVAILABLE) {
                continue;
            }
            if (d.getVehicle().getType() != type) {
                continue;
            }
            double dist = pickup.distanceTo(d.getCurrentLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best; // null if none found — caller must handle
    }
}

// ============================================================
// 6. STRATEGY PATTERN — Fare Calculation
// SOLID (OCP): adding surge pricing / city-specific rates = new class only.
// ============================================================
interface FareStrategy {

    double calculateFare(Location pickup, Location drop, VehicleType type);
}

class StandardFareStrategy implements FareStrategy {

    private static final double BASE_FARE = 50.0;
    private static final double PER_KM = 12.0;

    @Override
    public double calculateFare(Location pickup, Location drop, VehicleType type) {
        double distance = pickup.distanceTo(drop);
        double multiplier = (type == VehicleType.SUV) ? 1.5 : 1.0;
        return (BASE_FARE + distance * PER_KM) * multiplier;
    }
}

// ============================================================
// 7. STRATEGY PATTERN — Payment (same as BookMyShow)
// ============================================================
interface PaymentStrategy {

    boolean pay(double amount);
}

class UpiPayment implements PaymentStrategy {

    @Override
    public boolean pay(double amount) {
        System.out.println("Paid Rs." + amount + " via UPI");
        return true;
    }
}

// ============================================================
// 8. RIDE — the state machine at the center of the design
// SOLID (SRP): only tracks this ride's own lifecycle/status.
// State transitions are deliberately restricted through methods
// (not a public status setter) — this is a lightweight State pattern
// without a full class-per-state, same simplification call as BookMyShow.
// ============================================================
class Ride {

    private final String rideId;
    private final Rider rider;
    private Driver driver;      // assigned after matching
    private final Location pickup;
    private final Location drop;
    private final VehicleType requestedType;
    private RideStatus status = RideStatus.REQUESTED;
    private double fare;

    public Ride(String rideId, Rider rider, Location pickup, Location drop, VehicleType type) {
        this.rideId = rideId;
        this.rider = rider;
        this.pickup = pickup;
        this.drop = drop;
        this.requestedType = type;
    }

    public void assignDriver(Driver d) {
        this.driver = d;
        this.status = RideStatus.ACCEPTED;
    }

    public void start() {
        this.status = RideStatus.ONGOING;
    }

    public void end() {
        this.status = RideStatus.COMPLETED;
    }

    public void cancel() {
        this.status = RideStatus.CANCELLED;
    }

    public void setFare(double fare) {
        this.fare = fare;
    }

    public Rider getRider() {
        return rider;
    }

    public Driver getDriver() {
        return driver;
    }

    public Location getPickup() {
        return pickup;
    }

    public Location getDrop() {
        return drop;
    }

    public VehicleType getRequestedType() {
        return requestedType;
    }

    public RideStatus getStatus() {
        return status;
    }

    public double getFare() {
        return fare;
    }
}

// ============================================================
// 9. OBSERVER PATTERN — notifications
// SOLID (OCP): add SMS/push notifier without touching RideManager.
// ============================================================
// interface RideObserver {
//     void onRideStatusChanged(Ride ride);
// }
// class NotificationService implements RideObserver {
//     @Override
//     public void onRideStatusChanged(Ride ride) {
//         System.out.println("Notify rider " + ride.getRider().getName()
//             + ": ride status is now " + ride.getStatus());
//     }
// }
// ============================================================
// 10. RIDE MANAGER — Singleton + Facade
// Singleton: single coordinating authority for all ride requests.
// Facade: requestRide() hides match -> assign -> fare-calc -> notify.
// SOLID (SRP): coordinates only; matching/fare/payment logic lives
// in their own strategy classes — RideManager doesn't implement any of it.
// SOLID (DIP): depends on DriverMatchingStrategy, FareStrategy,
// PaymentStrategy, RideObserver interfaces — none of these are hardcoded.
// ============================================================
class RideManager {

    private static RideManager instance;

    private final List<Driver> allDrivers = new ArrayList<>();
    // private final List<RideObserver> observers  = new ArrayList<>();
    private final DriverMatchingStrategy matchingStrategy;
    private final FareStrategy fareStrategy;
    private int counter = 0;

    private RideManager(DriverMatchingStrategy matchingStrategy, FareStrategy fareStrategy) {
        this.matchingStrategy = matchingStrategy;
        this.fareStrategy = fareStrategy;
    }

    public static synchronized RideManager getInstance(DriverMatchingStrategy m, FareStrategy f) {
        if (instance == null) {
            instance = new RideManager(m, f);
        }
        return instance;
    }

    public void registerDriver(Driver d) {
        allDrivers.add(d);
    }
    // public void addObserver(RideObserver o) { observers.add(o); }

    public synchronized Ride requestRide(Rider rider, Location pickup, Location drop,
            VehicleType type, PaymentStrategy payment) {
        String rideId = "RIDE" + (++counter);
        Ride ride = new Ride(rideId, rider, pickup, drop, type);

        // Step 1 — find nearest available driver of requested type
        Driver matched = matchingStrategy.findDriver(pickup, allDrivers, type);
        if (matched == null) {
            ride.cancel();
            System.out.println("No drivers available for " + type);
            return ride;
        }

        // Step 2 — atomically claim the driver (all-or-nothing, same idea
        // as seat locking in BookMyShow)
        if (!matched.tryAssign()) {
            ride.cancel();
            System.out.println("Driver got taken, try again");
            return ride;
        }

        ride.assignDriver(matched);
        // notifyAll(ride);

        // Step 3 — simulate ride lifecycle
        ride.start();
        // notifyAll(ride); 

        double fare = fareStrategy.calculateFare(pickup, drop, type);
        ride.setFare(fare);          
          
        // Step 4 — payment
        payment.pay(fare);

        ride.end();
        matched.free(); // driver becomes available again
        // notifyAll(ride);

        return ride;
    }

    // private void notifyAll(Ride ride) 
    //     for (RideObserver o : observers) o.onRideStatusChanged(ride);
}

// ============================================================
// 11. MAIN
// ============================================================
public class CabBooking {

    public static void main(String[] args) {
        Vehicle v1 = new Vehicle("KA01AB1234", VehicleType.SEDAN);
        Driver d1 = new Driver("D1", "Ramesh", v1, new Location(12.90, 77.60));

        Vehicle v2 = new Vehicle("KA01CD5678", VehicleType.SEDAN);
        Driver d2 = new Driver("D2", "Suresh", v2, new Location(12.95, 77.62));

        RideManager manager = RideManager.getInstance(
                new NearestDriverStrategy(), new StandardFareStrategy());
        manager.registerDriver(d1);
        manager.registerDriver(d2);
        // manager.addObserver(new NotificationService());

        Rider alice = new Rider("R1", "Alice");
        Ride ride = manager.requestRide(
                alice,
                new Location(12.91, 77.61),
                new Location(12.93, 77.64),
                VehicleType.SEDAN,
                new UpiPayment());

        System.out.println("Final status: " + ride.getStatus() + ", fare: " + ride.getFare());
    }
}
