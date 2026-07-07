import java.util.*;

// ============================================================
// 1. ENUMS
// ============================================================
enum SeatType { SILVER, GOLD, PLATINUM }
enum SeatStatus { AVAILABLE, LOCKED, BOOKED }
enum BookingStatus { PENDING, CONFIRMED, CANCELLED, FAILED }
enum PaymentType { CREDIT_CARD, UPI, WALLET }

// ============================================================
// 2. STATIC / PHYSICAL DATA CLASSES
// SOLID: SRP -- pure data, no business logic.
// ============================================================
class User {
    private final String userId;
    private final String name;
    public User(String userId, String name) { this.userId = userId; this.name = name; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
}

class Movie {
    private final String movieId;
    private final String title;
    private final int durationMins;
    public Movie(String movieId, String title, int durationMins) {
        this.movieId = movieId; this.title = title; this.durationMins = durationMins;
    }
    public String getTitle() { return title; }
}

class Seat {
    private final String seatId;
    private final String seatNumber;
    private final SeatType type;
    public Seat(String seatId, String seatNumber, SeatType type) {
        this.seatId = seatId; this.seatNumber = seatNumber; this.type = type;
    }
    public String getSeatId() { return seatId; }
    public SeatType getType() { return type; }
    public String getSeatNumber() { return seatNumber; }
}

class Screen {
    private final String screenId;
    private final List<Seat> seats;
    public Screen(String screenId, List<Seat> seats) { this.screenId = screenId; this.seats = seats; }
    public List<Seat> getSeats() { return seats; }
}

class Theater {
    private final String theaterId;
    private final String name;
    private final List<Screen> screens;
    public Theater(String theaterId, String name, List<Screen> screens) {
        this.theaterId = theaterId; this.name = name; this.screens = screens;
    }
}

// ============================================================
// 3. STATE PATTERN -- seat lifecycle
// SOLID: OCP -- new states (e.g. BLOCKED_FOR_MAINTENANCE) can be
// added without touching existing state classes.
// SOLID: SRP -- each state class knows only its own legal
// transitions; ShowSeat doesn't contain a giant if/else on status.
// Why State over a plain enum + switch: illegal transitions
// (e.g. confirming an already-booked seat) throw naturally
// instead of relying on scattered if-checks across the codebase.
// ============================================================
interface SeatState {
    SeatState lock();
    SeatState confirm();
    SeatState release();
    SeatStatus getStatus();
}

class AvailableState implements SeatState {
    public SeatState lock() { return new LockedState(); }
    public SeatState confirm() { throw new IllegalStateException("Cannot confirm a seat that isn't locked"); }
    public SeatState release() { return this; }
    public SeatStatus getStatus() { return SeatStatus.AVAILABLE; }
}

class LockedState implements SeatState {
    public SeatState lock() { throw new IllegalStateException("Seat already locked"); }
    public SeatState confirm() { return new BookedState(); }
    public SeatState release() { return new AvailableState(); }
    public SeatStatus getStatus() { return SeatStatus.LOCKED; }
}

class BookedState implements SeatState {
    public SeatState lock() { throw new IllegalStateException("Seat already booked"); }
    public SeatState confirm() { throw new IllegalStateException("Seat already booked"); }
    public SeatState release() { return new AvailableState(); } // e.g. cancellation
    public SeatStatus getStatus() { return SeatStatus.BOOKED; }
}

// ============================================================
// 4. ShowSeat -- the concurrency-critical class.
// This is "Seat, scoped to one Show." Every synchronized method
// here is intentional: this object IS the lock boundary.
// SOLID: SRP -- owns exactly one thing: this seat's status for
// this show, and enforcing legal transitions on it.
// ============================================================
class ShowSeat {
    private final Seat seat;
    private final double price;
    private SeatState state = new AvailableState();
    private String lockedByUserId;
    private long lockedAtMillis;

    public ShowSeat(Seat seat, double price) { this.seat = seat; this.price = price; }

    public synchronized boolean tryLock(String userId) {
        if (state.getStatus() != SeatStatus.AVAILABLE) return false;
        state = state.lock();
        lockedByUserId = userId;
        lockedAtMillis = System.currentTimeMillis();
        return true;
    }

    public synchronized void confirm() { state = state.confirm(); }

    public synchronized void release() {
        state = state.release();
        lockedByUserId = null;
    }

    // Called lazily before any lock attempt -- avoids needing a background scheduler thread for the demo.
    public synchronized void expireIfStale(long ttlMillis) {
        if (state.getStatus() == SeatStatus.LOCKED && System.currentTimeMillis() - lockedAtMillis > ttlMillis) {
            release();
        }
    }

    public synchronized SeatStatus getStatus() { return state.getStatus(); }
    public Seat getSeat() { return seat; }
    public double getPrice() { return price; }
}

// ============================================================
// 5. SHOW
// ============================================================
class Show {
    private final String showId;
    private final Movie movie;
    private final Screen screen;
    private final long startTimeMillis;
    private final Map<String, ShowSeat> showSeats = new LinkedHashMap<>(); // seatId -> ShowSeat

    public Show(String showId, Movie movie, Screen screen, long startTimeMillis, Map<SeatType, Double> priceMap) {
        this.showId = showId; this.movie = movie; this.screen = screen; this.startTimeMillis = startTimeMillis;
        for (Seat s : screen.getSeats()) {
            showSeats.put(s.getSeatId(), new ShowSeat(s, priceMap.get(s.getType())));
        }
    }

    public ShowSeat getShowSeat(String seatId) { return showSeats.get(seatId); }
    public Movie getMovie() { return movie; }
}

// ============================================================
// 6. SEAT LOCK MANAGER -- the class the interviewer cares about most.
// SOLID: SRP -- this class ONLY handles the lock/unlock/confirm
// workflow across a *set* of seats atomically; it delegates the
// actual state transition to ShowSeat itself.
// Deadlock avoidance: seats are always locked in a fixed sorted
// order (by seatId) -- critical when locking multiple seats at once,
// otherwise two bookings locking the same two seats in opposite
// order can deadlock.
// ============================================================
class SeatLockManager {
    private static final long LOCK_TTL_MILLIS = 5 * 60 * 1000; // 5 min hold

    public boolean lockSeats(List<ShowSeat> seats, String userId) {
        List<ShowSeat> sorted = new ArrayList<>(seats);
        sorted.sort(Comparator.comparing(s -> s.getSeat().getSeatId())); // deadlock avoidance

        for (ShowSeat s : sorted) s.expireIfStale(LOCK_TTL_MILLIS);

        List<ShowSeat> locked = new ArrayList<>();
        for (ShowSeat s : sorted) {
            if (s.tryLock(userId)) {
                locked.add(s);
            } else {
                // rollback partial locks -- all-or-nothing
                for (ShowSeat l : locked) l.release();
                return false;
            }
        }
        return true;
    }

    public void confirmSeats(List<ShowSeat> seats) {
        for (ShowSeat s : seats) s.confirm();
    }

    public void releaseSeats(List<ShowSeat> seats) {
        for (ShowSeat s : seats) s.release();
    }
}

// ============================================================
// 7. STRATEGY PATTERN -- payment
// SOLID: OCP/DIP -- BookingManager depends on PaymentStrategy,
// never on a concrete payment class.
// ============================================================
interface PaymentStrategy {
    boolean pay(double amount);
}

class CreditCardPayment implements PaymentStrategy {
    public boolean pay(double amount) {
        System.out.println("Charged Rs." + amount + " via Credit Card");
        return true;
    }
}

class UpiPayment implements PaymentStrategy {
    public boolean pay(double amount) {
        System.out.println("Charged Rs." + amount + " via UPI");
        return true;
    }
}

class PaymentFactory {
    public static PaymentStrategy get(PaymentType type) {
        switch (type) {
            case CREDIT_CARD: return new CreditCardPayment();
            case UPI: return new UpiPayment();
            case WALLET: throw new UnsupportedOperationException("Wallet not implemented yet");
            default: throw new IllegalArgumentException("Unknown payment type");
        }
    }
}

// ============================================================
// 8. OBSERVER PATTERN -- booking notifications
// SOLID: OCP -- add SMS/email/push notifiers without touching
// BookingManager.
// ============================================================
interface BookingObserver {
    void onBookingConfirmed(Booking booking);
}

class NotificationService implements BookingObserver {
    public void onBookingConfirmed(Booking booking) {
        System.out.println("Booking confirmed for " + booking.getUser().getName() +
                " | seats: " + booking.getShowSeats().size() + " | amount: " + booking.getAmount());
    }
}

// ============================================================
// 9. BOOKING
// ============================================================
class Booking {
    private final String bookingId;
    private final User user;
    private final Show show;
    private final List<ShowSeat> showSeats;
    private final double amount;
    private BookingStatus status;

    public Booking(String bookingId, User user, Show show, List<ShowSeat> showSeats, double amount) {
        this.bookingId = bookingId; this.user = user; this.show = show;
        this.showSeats = showSeats; this.amount = amount; this.status = BookingStatus.PENDING;
    }

    public void setStatus(BookingStatus status) { this.status = status; }
    public User getUser() { return user; }
    public List<ShowSeat> getShowSeats() { return showSeats; }
    public double getAmount() { return amount; }
}

// ============================================================
// 10. BOOKING MANAGER -- Facade + Singleton
// SOLID: SRP at orchestration level -- coordinates, doesn't
// implement locking/payment logic itself.
// SOLID: DIP -- depends on SeatLockManager, PaymentStrategy,
// BookingObserver abstractions.
// Pattern: Facade -- one method (bookTickets) hides lock ->
// pay -> confirm -> notify.
// Pattern: Singleton -- one shared booking authority per process.
// ============================================================
class BookingManager {
    private static BookingManager instance;
    private final SeatLockManager lockManager = new SeatLockManager();
    private final List<BookingObserver> observers = new ArrayList<>();
    private int counter = 0;

    private BookingManager() {}

    public static synchronized BookingManager getInstance() {
        if (instance == null) instance = new BookingManager();
        return instance;
    }

    public void addObserver(BookingObserver o) { observers.add(o); }

    public Booking bookTickets(User user, Show show, List<String> seatIds, PaymentType paymentType) {
        List<ShowSeat> showSeats = new ArrayList<>();
        for (String id : seatIds) showSeats.add(show.getShowSeat(id));

        if (!lockManager.lockSeats(showSeats, user.getUserId())) {
            throw new RuntimeException("One or more seats are no longer available");
        }

        double amount = showSeats.stream().mapToDouble(ShowSeat::getPrice).sum();
        Booking booking = new Booking("BKG" + (++counter), user, show, showSeats, amount);

        PaymentStrategy payment = PaymentFactory.get(paymentType);
        boolean paid = payment.pay(amount);

        if (!paid) {
            lockManager.releaseSeats(showSeats);
            booking.setStatus(BookingStatus.FAILED);
            return booking;
        }

        lockManager.confirmSeats(showSeats);
        booking.setStatus(BookingStatus.CONFIRMED);
        for (BookingObserver ob : observers) ob.onBookingConfirmed(booking);
        return booking;
    }

    public void cancelBooking(Booking booking) {
        lockManager.releaseSeats(booking.getShowSeats());
        booking.setStatus(BookingStatus.CANCELLED);
    }
}

// ============================================================
// 11. DEMO
// ============================================================
public class BookMyShowDemo {
    public static void main(String[] args) {
        Seat s1 = new Seat("S1", "A1", SeatType.SILVER);
        Seat s2 = new Seat("S2", "A2", SeatType.GOLD);
        Screen screen = new Screen("SCR1", Arrays.asList(s1, s2));
        Movie movie = new Movie("M1", "Interstellar", 170);

        Map<SeatType, Double> prices = new HashMap<>();
        prices.put(SeatType.SILVER, 150.0);
        prices.put(SeatType.GOLD, 250.0);

        Show show = new Show("SH1", movie, screen, System.currentTimeMillis(), prices);

        BookingManager manager = BookingManager.getInstance();
        manager.addObserver(new NotificationService());

        User alice = new User("U1", "Alice");
        Booking booking = manager.bookTickets(alice, show, Arrays.asList("S1", "S2"), PaymentType.UPI);

        // Simulate a second user trying to grab the same seat -- should fail
        User bob = new User("U2", "Bob");
        try {
            manager.bookTickets(bob, show, Arrays.asList("S1"), PaymentType.CREDIT_CARD);
        } catch (RuntimeException e) {
            System.out.println("Expected failure: " + e.getMessage());
        }
    }
}