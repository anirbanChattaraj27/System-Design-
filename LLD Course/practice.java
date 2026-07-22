
import java.util.*;

enum RideStatus{ REQUESTED, ONGOING, CANCELED, COMPLETED}

enum vehicleType{CAR, BIKE, AUTO}

enum DriverStatus{AVAILABLE, UNAVAILABLE, ON_RIDE}

class Location{
    private final double lat;
    private final double lng;

    public Location(double lat, double lng) {
        this.lat = lat;
        this.lng = lng; 
    }

    public double distanceTo(Location other){
        double dx = this.lat - other.lat;
        double dy = this.lng - other.lng;
        return Math.sqrt(dx*dx + dy*dy);
    }

    public double getlat() {return lat;}
    public double getLng() {return lng;}
}

class User{
    private final String id;
    private final String name;

    public User(String id, String name){
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


class Vehicle{
    String num;
    vehicleType type;

    
}



public class practice {
    
}
