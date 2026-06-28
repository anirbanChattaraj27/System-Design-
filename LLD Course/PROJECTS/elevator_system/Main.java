import java.util.*;

// Direction the elevator is currently travelling (or IDLE = not moving)
enum Direction { UP, DOWN, IDLE }

// ── State Pattern (lightweight version) ───────────────────────────────────
// Full State Pattern would make each of these a CLASS with its own
// behavior (IdleState.handle(), MovingState.handle()...).
// For an interview, an enum + a switch/if in Elevator.step() is the
// pragmatic choice — explain you'd extract to full State classes if
// each state's logic grew complex (e.g. maintenance mode, emergency stop).
enum ElevatorState {
    IDLE,         // no pending requests, doors closed, not moving
    MOVING,       // travelling toward next stop
    DOORS_OPEN    // stopped at a floor, doors open
}

// ── SOLID: Single Responsibility ──────────────────────────────────────────
// Represents ONE request — either:
//  - External: "I'm on floor 5, I want to go UP" (destinationFloor unknown yet)
//  - Internal: "Take me to floor 8" (destinationFloor known, called from inside cabin)
class Request {
    private final int floor;             // the floor where the button was pressed
    private final Direction direction; // desired direction (UP/DOWN); IDLE for internal dest-only requests

    public Request(int floor, Direction direction) {
        this.floor     = floor;
        this.direction = direction;
    }

    public int getFloor()         { return floor; }
    public Direction getDirection() { return direction; }
}


// ── SOLID: Single Responsibility ──────────────────────────────────────────
// Elevator manages ONLY its own movement, state, and request queues.
// It does NOT know about other elevators or how it was chosen — that's
// the Controller's job. This separation is what makes the class testable
// in isolation (you can unit-test ONE elevator with no controller at all).
//
// ── DATA STRUCTURE CHOICE: two priority queues ────────────────────────────
// upRequests   = min-heap → always serve the LOWEST floor above us next
// downRequests = max-heap → always serve the HIGHEST floor below us next
// This is the classic "elevator scheduling" trick: it guarantees we serve
// requests in floor-order along our current direction, never zig-zagging.

class Elevator {

    private final int id;
    private int currentFloor;
    private ElevatorState state;
    private Direction direction;

    private final PriorityQueue<Integer> upRequests;   // min-heap (natural order)
    private final PriorityQueue<Integer> downRequests; // max-heap (reverse order)

    public Elevator(int id) {
        this.id           = id;
        this.currentFloor = 0;            // ground floor
        this.state        = ElevatorState.IDLE;
        this.direction    = Direction.IDLE;
        this.upRequests   = new PriorityQueue<>();                          // ascending
        this.downRequests = new PriorityQueue<>(Collections.reverseOrder()); // descending
    }

    // ── Add a destination floor (called for BOTH internal cabin requests
    // AND external requests once this elevator has been chosen) ──────
    public void addRequest(int floor) {
        if (floor > currentFloor) {
            upRequests.add(floor);
        } else if (floor < currentFloor) {
            downRequests.add(floor);
        }
        // floor == currentFloor → already here, nothing to queue

        // If we were idle, pick a direction now based on the new request
        if (state == ElevatorState.IDLE) {
            if (floor > currentFloor) {
                direction = Direction.UP;
                state = ElevatorState.MOVING;
            }
            else if (floor < currentFloor) {
                direction = Direction.DOWN;
                state = ElevatorState.MOVING;
            }
            else {
                state = ElevatorState.DOORS_OPEN;
            }
        }
    }

    // ── THE SIMULATION TICK ───────────────────────────────────────────
    // Moves the elevator ONE floor per call (or opens/closes doors).
    // In a real system, this would be driven by a timer/motor controller —
    // for the interview, calling step() repeatedly in Main simulates time.
    public void step() {
        switch (state) {
            case IDLE:
                // nothing to do
                break;

            case DOORS_OPEN:
                // doors close, decide what's next
                state = ElevatorState.MOVING;
                decideNextDirection();
                break;

            case MOVING:
                if (direction == Direction.UP) {
                    currentFloor++;
                    if (!upRequests.isEmpty() && upRequests.peek() == currentFloor) {
                        upRequests.poll();              // arrived — remove this stop
                        state = ElevatorState.DOORS_OPEN;
                    }
                } else if (direction == Direction.DOWN) {
                    currentFloor--;
                    if (!downRequests.isEmpty() && downRequests.peek() == currentFloor) {
                        downRequests.poll();
                        state = ElevatorState.DOORS_OPEN;
                    }
                }
                break;
        }
    }

    // After doors close, decide: continue same direction, switch, or go idle
    private void decideNextDirection() {
        if (direction == Direction.UP && !upRequests.isEmpty()) {
            return; // keep going up — more stops above
        }
        if (direction == Direction.DOWN && !downRequests.isEmpty()) {
            return; // keep going down — more stops below
        }

        // No more requests in current direction — try the other direction
        if (!upRequests.isEmpty()) {
            direction = Direction.UP;
        } 
        else if (!downRequests.isEmpty()) {
            direction = Direction.DOWN;
        } 
        else {
            direction = Direction.IDLE;
            state     = ElevatorState.IDLE;
        }
    }

    // ── Helper for dispatch strategy: how "good" a fit is this elevator? ──
    // Simple metric: absolute floor distance. Lower = better.
    public int distanceTo(int floor) {
        return Math.abs(this.currentFloor - floor);
    }

    public int getId()            { return id; }
    public int getCurrentFloor() { return currentFloor; }
    public ElevatorState getState()     { return state; }
    public Direction getDirection()   { return direction; }
}

// ── Design Pattern: STRATEGY ──────────────────────────────────────────────
// "Which elevator answers this call?" is THE classic example of varying
// behavior. Real systems use complex heuristics (load balancing, zoning,
// SCAN/LOOK algorithms); we implement the simplest correct one and make
// it swappable.
// ── SOLID: Open/Closed ────────────────────────────────────────────────────
// Add ZoningStrategy or LoadBalancedStrategy later → new class implementing
// this interface. ElevatorController is untouched.

interface DispatchStrategy {
    Elevator selectElevator(List<Elevator> elevators, Request request);
}

// ── SOLID: Single Responsibility ──────────────────────────────────────────
// Does ONE thing: picks the elevator with the smallest floor distance
// to the request's floor. Doesn't care about direction matching —
// that nuance is exactly what a more advanced strategy would add.
class NearestElevatorStrategy implements DispatchStrategy {
    @Override
    public Elevator selectElevator(List<Elevator> elevators, Request request) {
        Elevator best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Elevator e : elevators) {
            int dist = e.distanceTo(request.getFloor());
            if (dist < bestDistance) {
                bestDistance = dist;
                best = e;
            }
        }
        return best;  // elevators list is never empty in our design, so always non-null
    }
}

class LoadBalancedStrategy{

    // code
    // in future we can add more strategies like this to balance the load of elevators based on their current load and requests.
}

class LeastBusyStrategy{
    
    // code
    // in future we can add more strategies like this to select the least busy elevator based on their current load and requests.
}

// ── Design Pattern: SINGLETON ─────────────────────────────────────────────
// One controller manages all elevators in the building — same reasoning
// as ParkingLot / RateLimiter / Logger. Two controllers would double-dispatch.
// ── SOLID: Single Responsibility ──────────────────────────────────────────
// Controller's ONE job: receive external requests, pick an elevator via
// the strategy, and forward the request. It does NOT implement movement —
// that's Elevator's job (separation of concerns).
// ── SOLID: Dependency Inversion ───────────────────────────────────────────
// Controller depends on DispatchStrategy (interface), never on
// NearestElevatorStrategy directly.

class ElevatorController {

    // There should only be one controller for the entire building.
    private static ElevatorController instance;

    private final List<Elevator> elevators;  // Stores all elevators.
    private DispatchStrategy dispatchStrategy; // DispatchStrategy we are using, not NearestElevatorStrategy (bcz in future we can hvave more strategies), so calling interface here.

    // EXPLANATION bellow: 🟧🟧
    // ElevatorController.getInstance(3); // run loop 3 times
    private ElevatorController(int numElevators) { // Runs only once because constructor is private.
        this.elevators = new ArrayList<>(); // Store all elevators in a list
        for (int i = 1; i <= numElevators; i++) {
            elevators.add(new Elevator(i));
        }
        this.dispatchStrategy = new NearestElevatorStrategy(); // Whenever someone presses a button, use Nearest Elevator Strategy.
    } 

    // EXPLANATION bellow: 🟧🟧
    public static ElevatorController getInstance(int numElevators) {

        if (instance == null) {
            instance = new ElevatorController(numElevators);
        }
        return instance;
    }

    // Allows swapping strategy at runtime — e.g. switch to a
    // LoadBalancedStrategy during peak hours.
    public void setDispatchStrategy(DispatchStrategy strategy) {
        this.dispatchStrategy = strategy;
    }

    // EXPLANATION bellow: 🔴🔴
    // ── EXTERNAL REQUEST: "I'm on floor X, going UP/DOWN" ────────────
    // 1. Ask the strategy which elevator should handle this
    // 2. Forward the floor to that elevator's queue
    public void requestElevator(int floor, Direction direction) {
        Request request = new Request(floor, direction);
        Elevator chosen = dispatchStrategy.selectElevator(elevators, request);
        chosen.addRequest(floor);
        System.out.printf("[DISPATCH] Floor %d (%s) -> Elevator %d%n",
            floor, direction, chosen.getId());
    }

    // EXPLANATION bellow: 🔴🔴
    // ── INTERNAL REQUEST: "Take me to floor X" (from inside a cabin) ──
    // No dispatch needed — goes straight to the elevator you're already in.
    public void requestFloorFromInsideElevator(int elevatorId, int destinationFloor) {
        for (Elevator e : elevators) {
            if (e.getId() == elevatorId) {
                e.addRequest(destinationFloor);
                System.out.printf("[CABIN] Elevator %d -> Floor %d%n", elevatorId, destinationFloor);
                return;
            }
        }
    }

    // ── Advance simulation by one tick for ALL elevators ─────────────
    public void stepAll() {
        for (Elevator e : elevators) {
            e.step();
        }
    }

    public void printStatus() {
        for (Elevator e : elevators) {
            System.out.printf("  Elevator %d: floor=%d state=%s dir=%s%n",
                e.getId(), e.getCurrentFloor(), e.getState(), e.getDirection());
        }
    }
}


public class Main {
    public static void main(String[] args) {

        // 3 elevators, all starting at ground floor (0)
        ElevatorController controller = ElevatorController.getInstance(3);

        // External request: someone on floor 5 wants to go UP
        controller.requestElevator(5, Direction.UP);

        // Internal request: person inside Elevator 1 wants floor 8
        controller.requestFloorFromInsideElevator(1, 8);

        System.out.println("\nInitial status:");
        controller.printStatus();

        // Simulate 6 time steps — watch Elevator 1 move toward floor 5, then 8
        for (int tick = 1; tick <= 6; tick++) {
            controller.stepAll();
            System.out.println("\nAfter tick " + tick + ":");
            controller.printStatus();
        }
    }
}

/* Expected output (abridged):
[DISPATCH] Floor 5 (UP) -> Elevator 1   (all elevators at floor 0, Elevator 1 picked — tie-break: first)
[CABIN] Elevator 1 -> Floor 8

Initial status:
  Elevator 1: floor=0 state=MOVING dir=UP
  Elevator 2: floor=0 state=IDLE dir=IDLE
  Elevator 3: floor=0 state=IDLE dir=IDLE

After tick 1:
  Elevator 1: floor=1 state=MOVING dir=UP
  ...
After tick 5:
  Elevator 1: floor=5 state=DOORS_OPEN dir=UP   (reached floor 5, doors open)
After tick 6:
  Elevator 1: floor=5 state=MOVING dir=UP       (doors closed, continuing to floor 8)
*/


/*
EXPLANATION: 🟧🟧

Now suppose your main() is

    public static void main(String[] args) {

        ElevatorController controller = ElevatorController.getInstance(3);

    }

Step 1: The first time getInstance() is called, instance is null, so we create a new ElevatorController with 3 elevators. This is the only time the constructor runs.
    Execution starts here----> ElevatorController controller = ElevatorController.getInstance(3);
    The JVM looks for----> getInstance(3) ----> and enters this method.

Step 2:
    if(instance == null)
    Initially instance is null, Because no controller has been created yet. so we create a new ElevatorController with 3 elevators and assign it to instance.

Step 3:
    instance = new ElevatorController(3);
    Java now says "I need to create a new ElevatorController object."
    Before creating the object, it automatically calls the constructor. So execution jumps here. 
        private ElevatorController(int numElevators)
    numElevators ---> 3
    Inside the constructor, we initialize the elevators list and add 3 Elevator objects to it. bcz we passed 3 as numElevators. We also set the default dispatch strategy to NearestElevatorStrategy.

Step 4:
    this.elevators = new ArrayList<>(); --> Creates an empty list.
    Nothing inside yet.

Step 5:
    for(int i=1;i<=3;i++)
    i = 1 ---> elevators.add(new Elevator(1)); ----> Now list has 1 Elevator object with id=1, currentFloor=0, state=IDLE, direction=IDLE
    i = 2 ---> elevators.add(new Elevator(2)); ----> Now list has 2 Elevator objects with id=1 and id=2, both at floor 0, state=IDLE, direction=IDLE
    i = 3 ---> elevators.add(new Elevator(3)); ----> Now list has 3 Elevator objects with id=1, id=2, id=3, all at floor 0, state=IDLE, direction=IDLE

    [Elevator1, Elevator2, Elevator3]

Step 6:
    this.dispatchStrategy = new NearestElevatorStrategy();
    We set the default dispatch strategy to NearestElevatorStrategy. This means that when someone presses a button, the controller will use this strategy to decide which elevator should respond.
    Controller --> dispatchStrategy ----> NearestElevatorStrategy
    Whenever someone presses a button --->  Use this strategy -------> Choose nearest elevator.

Step 7:
    Constructor finishes. Java goes back here

    instance
        ↓
    Controller
    |
    |----Elevator1
    |
    |----Elevator2
    |
    |----Elevator3
    |
    |----NearestElevatorStrategy

*/

/*
// EXPLANATION bellow: 🔴🔴

External Request → Someone outside the elevator presses UP/DOWN.
Internal Request → Someone inside the elevator presses a destination floor.

Function 1: External Request

    STEP 1:
        controller.requestElevator(5, Direction.UP);
        This means someone on floor 5 wants to go UP. The controller will use the dispatch strategy to choose the best elevator to handle this request.

    STEP 2:
        Elevator chosen = dispatchStrategy.selectElevator(elevators, request);
        This is where the Strategy Pattern comes in. Instead of the controller deciding, it delegates the decision to the dispatch strategy. The strategy looks at all elevators and picks the one closest to floor 5.
    
    STEP 3:
        chosen.addRequest(floor); ---> choose = Elevator 2 && floor = 5
        Elevator2.addRequest(5);
        The chosen elevator adds floor 5 to its request queue. It will now move towards floor 5.
    
        Inside Elevator2 --> upRequests is empty ---> addRequest(5) ---> upRequests.add(5) ---> upRequests = [5] 
        Now Elevator2 knows --> "I need to stop at Floor 5."
    
    STEP 4:
        System.out.printf();
        This prints a message indicating which elevator was dispatched to handle the request.

Visual Flow
    User presses -> Floor 5 --> UP --> Controller ---> Create Request Object ---> Ask Strategy --> "Which elevator?"  --> Nearest Strategy --> Elevator2 -->  Elevator2.addRequest(5) --> Queue --> 5
                                                                                                               
    Controller ->  Choose elevator -> Assign request

    Movement is handled by the Elevator class. This follows the Single Responsibility Principle



Function 2: Internal Request:
    Now suppose the elevator has arrived. You enter Elevator2. Inside the cabin you press 8
    Main calls ---> controller.requestFloorFromInsideElevator(2, 8); ---> This means you are inside Elevator2 and want to go to floor 8. The controller

    STEP 1:
        loop starts --> for(Elevator e : elevators)
        Elevator1, Elevator2, Elevator3

        e = Elevator1
            if(e.getId()==2) ---> 1 == 2 --> false ---> continue
        e = Elevator2
            if(e.getId()==2) ---> 2 == 2 --> true ---> addRequest
    STEP 2:
        e.addRequest(8) ---> Elevator2.addRequest(8) ---> upRequests.add(8) ---> upRequests = [5, 8] --> (if current Queue has 5 already, then add 8 to the queue)
        Now Elevator2 knows --> "I need to stop at Floor 5 and then Floor 8."

    STEP 3:
        System.out.printf();
        This prints a message indicating that Elevator2 has received an internal request to go to floor 8.

    STEP 4:
        Movement is handled by the Elevator class. This follows the Single Responsibility Principle

        
VISUAL FLOW
    User presses -> Floor 8 --> Inside Elevator2 --> Controller ---> Elevator2.addRequest(8) ---> upRequests = [5, 8]


*/

/* 
Why is the constructor private?
    ElevatorController c1 = new ElevatorController(3);

    ElevatorController c2 = new ElevatorController(3);

Now there would be: Controller1, Controller2

    Two controllers managing the same elevators. That defeats the purpose of the Singleton pattern.

    Making the constructor private prevents code outside the class from creating new ElevatorController objects.
    The only way to obtain one is through getInstance(), which guarantees that only one instance is created.

"Explain your Singleton implementation"
    "The constructor is private so that no other class can create an ElevatorController object directly using new.
    The static getInstance() method is the only way to obtain the controller. The first time it is called, 
    it creates the controller, initializes all elevators and the default dispatch strategy, stores the object in  
    the static instance variable, and returns it. On subsequent calls, since instance is no longer null, it simply 
    returns the same object. This ensures that the building has exactly one controller managing all elevators."
*/