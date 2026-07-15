import java.util.*;

// ============================================================
// 1. ENUMS — only what's actually used
// ============================================================
enum SeatType   { SILVER, GOLD, PLATINUM }
enum SeatStatus { AVAILABLE, LOCKED, BOOKED }
enum PaymentType { CREDIT_CARD, UPI }

// ============================================================
// 2. DATA CLASSES — pure data, no logic
// SOLID: SRP — only hold data
// ============================================================
class User {
    private final String userId;
    private final String name;

    public User(String userId, String name) {
        this.userId = userId;
        this.name   = name;
    }

    public String getUserId() { return userId; }
    public String getName()   { return name;   }
}

class Movie {
    private final String title;

    public Movie(String title) {
        this.title = title;
    }

    public String getTitle() { return title; }
}

class Seat {
    private final String   seatId;
    private final String   seatNumber;
    private final SeatType type;

    public Seat(String seatId, String seatNumber, SeatType type) {
        this.seatId     = seatId;
        this.seatNumber = seatNumber;
        this.type       = type;
    }

    public String   getSeatId()     { return seatId;     }
    public String   getSeatNumber() { return seatNumber; }
    public SeatType getType()       { return type;       }
}

class Screen {
    private final String     screenId;
    private final List<Seat> seats;

    public Screen(String screenId, List<Seat> seats) {
        this.screenId = screenId;
        this.seats    = seats;
    }

    public List<Seat> getSeats() { return seats; }
}

// ============================================================
// 3. SHOW SEAT — the most important class
// Holds seat status for ONE show. synchronized = thread safe.
// SOLID: SRP — only manages this seat's status for this show
// ============================================================
class ShowSeat {
    private final Seat   seat;
    private final double price;
    private SeatStatus   status = SeatStatus.AVAILABLE; // simple enum, no State pattern

    public ShowSeat(Seat seat, double price) {
        this.seat  = seat;
        this.price = price;
    }

    // synchronized — prevents two users booking same seat simultaneously
    public synchronized boolean tryLock() {
        if (status != SeatStatus.AVAILABLE) return false;
        status = SeatStatus.LOCKED;
        return true;
    }

    public synchronized void confirm() {
        status = SeatStatus.BOOKED;
    }

    public synchronized void release() {
        status = SeatStatus.AVAILABLE;
    }

    public Seat       getSeat()   { return seat;   }
    public double     getPrice()  { return price;  }
    public SeatStatus getStatus() { return status; }
}

// ============================================================
// 4. SHOW — links movie + screen + seat pricing
// ============================================================
class Show {
    private final String showId;
    private final Movie movie;
    private final Map<String, ShowSeat> showSeats = new LinkedHashMap<>();

    public Show(String showId, Movie movie, Screen screen, Map<SeatType, Double> priceMap) {
        this.showId = showId;
        this.movie  = movie;
        // Create one ShowSeat per physical seat with the right price
        for (Seat s : screen.getSeats()) {
            showSeats.put(s.getSeatId(),
                new ShowSeat(s, priceMap.get(s.getType())));
        }
    }

    public ShowSeat getShowSeat(String seatId) {
        return showSeats.get(seatId);
    }

    public Movie getMovie() { return movie; }
}

// ============================================================
// 5. STRATEGY PATTERN — payment
// SOLID: OCP — add new payment = new class only
// SOLID: DIP — BookingManager depends on interface, not concrete class
// ============================================================
interface PaymentStrategy {
    boolean pay(double amount);
}

class CreditCardPayment implements PaymentStrategy {
    @Override 
    public boolean pay(double amount) {
        System.out.println("Paid Rs." + amount + " via Credit Card");
        return true;
    }
}

class UpiPayment implements PaymentStrategy {
    @Override
    public boolean pay(double amount) {
        System.out.println("Paid Rs." + amount + " via UPI");
        return true;
    }
}

// ============================================================
// 6. BOOKING — result data object
// SOLID: SRP — just holds booking data
// ============================================================
class Booking {
    private final String         bookingId;
    private final User           user;
    private final List<ShowSeat> showSeats;
    private final double         amount;
    private       boolean        confirmed;

    public Booking(String bookingId, User user, List<ShowSeat> showSeats, double amount) {
        this.bookingId = bookingId;
        this.user      = user;
        this.showSeats = showSeats;
        this.amount    = amount;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public User           getUser()      { return user;      }
    public List<ShowSeat> getShowSeats() { return showSeats; }
    public double         getAmount()    { return amount;    }
    public boolean        isConfirmed()  { return confirmed; }
}

// ============================================================
// 7. OBSERVER PATTERN — notifications (optional)
// SOLID: OCP — add SMS/email notifier = new class, BookingManager unchanged
// ============================================================
interface BookingObserver {
    void onBookingConfirmed(Booking booking);
}

class NotificationService implements BookingObserver {
    @Override
    public void onBookingConfirmed(Booking booking) {
        System.out.printf("Notified %s — %d seat(s) — Rs.%.2f%n",
            booking.getUser().getName(),
            booking.getShowSeats().size(),
            booking.getAmount());
    }
}

// ============================================================
// 8. BOOKING MANAGER — Singleton + Facade
//
// Singleton : one booking authority for the whole app
// Facade    : one method (bookTickets) hides lock→pay→confirm→notify
// SOLID: SRP — coordinates only, no lock or payment logic itself
// SOLID: DIP — depends on PaymentStrategy + BookingObserver abstractions
// ============================================================
class BookingManager {

    private static BookingManager instance;

    private final List<BookingObserver> observers = new ArrayList<>();
    private int counter = 0;

    private BookingManager() {}

    public static synchronized BookingManager getInstance() {
        if (instance == null) instance = new BookingManager();
        return instance;
    }

    public void addObserver(BookingObserver o) {
        observers.add(o);
    }

    public Booking bookTickets(User user, Show show, List<String> seatIds, PaymentStrategy payment) {
        // Step 1 — collect ShowSeat objects
        List<ShowSeat> showSeats = new ArrayList<>();
        for (String id : seatIds) {
            showSeats.add(show.getShowSeat(id));
        }

        // Step 2 — lock all seats (all-or-nothing)
        List<ShowSeat> locked = new ArrayList<>();
        for (ShowSeat s : showSeats) {
            if (s.tryLock()) {
                locked.add(s);
            } else {
                // rollback — release already locked seats
                for (ShowSeat l : locked) l.release();
                throw new RuntimeException("Seat " +
                    s.getSeat().getSeatNumber() + " is not available");
            }
        }

        // Step 3 — calculate total
        double amount = showSeats.stream().mapToDouble(ShowSeat::getPrice).sum(); 

        // Step 4 — pay
        Booking booking = new Booking( "BKG" + (++counter), user, showSeats, amount);

        if (!payment.pay(amount)) {
            for (ShowSeat s : locked) s.release();
            booking.setConfirmed(false);
            return booking;
        }

        // Step 5 — confirm seats + notify
        for (ShowSeat s : locked) s.confirm();
        booking.setConfirmed(true);
        for (BookingObserver ob : observers) ob.onBookingConfirmed(booking);

        return booking;
    }

    public void cancelBooking(Booking booking) {
        for (ShowSeat s : booking.getShowSeats()) s.release();
        booking.setConfirmed(false);
        System.out.println("Booking cancelled for " + booking.getUser().getName());
    }
}

// ============================================================
// 9. MAIN
// ============================================================
public class BookMyShow {

    public static void main(String[] args) {

        // Setup
        Seat s1 = new Seat("S1", "A1", SeatType.SILVER);
        Seat s2 = new Seat("S2", "A2", SeatType.GOLD);
        Screen screen = new Screen("SCR1", Arrays.asList(s1, s2));
        Movie  movie  = new Movie("Interstellar");

        Map<SeatType, Double> prices = new HashMap<>();
        prices.put(SeatType.SILVER, 150.0);
        prices.put(SeatType.GOLD,   250.0);

        Show show = new Show("SH1", movie, screen, prices);

        BookingManager manager = BookingManager.getInstance();
        manager.addObserver(new NotificationService());

        // Alice books both seats via UPI
        User alice = new User("U1", "Alice");
        Booking b1 = manager.bookTickets(
            alice, show,
            Arrays.asList("S1", "S2"),
            new UpiPayment());

        System.out.println("Booking confirmed: " + b1.isConfirmed());

        // Bob tries to book S1 — should fail
        User bob = new User("U2", "Bob");
        try {
            manager.bookTickets(
                bob, show,
                Arrays.asList("S1"),
                new CreditCardPayment());
        } catch (RuntimeException e) {
            System.out.println("Expected: " + e.getMessage());
        }
    }
}
/*
Seat is the physical chair — static, one row/type. ShowSeat is that chair's booking state for one 
specific show — because the same chair is reused across many shows with different prices and different
availability. Show is the factory that creates all ShowSeats for a screen when a show is scheduled. 
Booking is just the receipt. BookingManager is the only class with actual business logic — 
it orchestrates lock → pay → confirm/rollback as one atomic-feeling operation, which is why it's a Facade."


Alice wants seats S1 (Silver, ₹150) and S2 (Gold, ₹250) for SH1.
bookTickets resolves S1, S2 → their ShowSeat objects for SH1 specifically.
Tries tryLock() on both. Both are AVAILABLE → both become LOCKED. locked = [S1, S2].
amount = 150 + 250 = 400.
Creates Booking("BKG1", alice, [S1,S2], 400).
Calls UpiPayment.pay(400) → returns true.
Both seats → confirm() → status becomes BOOKED.
booking.setConfirmed(true).
Loops over observers → NotificationService prints "Notified Alice — 2 seats — ₹400".

Now Bob tries S1:

tryLock() on S1's ShowSeat → status is already BOOKED, not AVAILABLE → returns false.
Since locked list is empty (nothing succeeded before this), rollback loop does nothing.
Throws RuntimeException("Seat A1 is not available").
Caught in main, printed.
*/