import java.util.*;

// ============================================================
// 1. ENUM
// ============================================================
enum SplitType {
    EQUAL, EXACT
}

// ============================================================
// 2. USER — pure data, no logic
// SOLID: Single Responsibility
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

//     @Override
//     public String toString()  { return name;   }
}

// ============================================================
// 3. SPLIT — one person's share of one expense
// SOLID: Single Responsibility
// ============================================================
class Split {
    private final User   user;
    private final double amount;

    public Split(User user, double amount) {
        this.user   = user;
        this.amount = amount;
    }

    public User   getUser()   { return user;   }
    public double getAmount() { return amount; }
}

// ============================================================
// 4. STRATEGY PATTERN — how to divide the bill
// SOLID: Open/Closed  — add new split type = new class only
// SOLID: ISP          — one focused method
// SOLID: DIP          — callers depend on this interface
// ============================================================
interface SplitStrategy {
    List<Split> calculate(List<User> users, double totalAmount, Map<String, Double> inputs);
}

// --- EQUAL: divide bill equally among all users ---
class EqualSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculate(List<User> users, double totalAmount, Map<String, Double> inputs) {
        List<Split> result = new ArrayList<>();
        double share = totalAmount / users.size();
        for (User u : users) {
            result.add(new Split(u, share));
        }
        return result;
    }
}

// --- EXACT: caller specifies exact amount per person ---
/*
    A pays 1000
    inputs = { "u1": 500, "u2": 300, "u3": 200 }

    Loop:
    u1 (A) → inputs.get("u1") = 500 → Split(A, 500)
    u2 (B) → inputs.get("u2") = 300 → Split(B, 300)
    u3 (C) → inputs.get("u3") = 200 → Split(C, 200)

    BalanceSheet sees paidBy = A, so:
    Split(A, 500) → SKIPPED (A doesn't owe herself)
    Split(B, 300) → B owes A: 300 ✅
    Split(C, 200) → C owes A: 200 ✅
*/
class ExactSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculate(List<User> users, double totalAmount, Map<String, Double> inputs) {
        List<Split> result = new ArrayList<>();

        for (User u : users) {
            double amount = inputs.get(u.getUserId()); // direct get, no default
            result.add(new Split(u, amount));
        }

        return result;
    }
}

// ============================================================
// 5. FACTORY PATTERN — one place to create strategies
// SOLID: SRP — creation logic lives here, not in ExpenseManager
// SOLID: OCP — add new type = one new case here, nothing else
// ============================================================
class SplitFactory {
    public static SplitStrategy getStrategy(SplitType type) {
        switch (type) {
            case EQUAL: return new EqualSplitStrategy();
            case EXACT: return new ExactSplitStrategy();
            default:    throw new IllegalArgumentException("Unknown: " + type);
        }
    }
}

// ============================================================
// 6. BALANCE SHEET — tracks who owes whom
// SOLID: SRP — only job is maintaining the ledger.
//              No split math, no notifications here.
//
// Data structure:  Map < debtorId , Map < creditorId , amount > >
// Read as: "debtor owes creditor this amount"
// ============================================================
class BalanceSheet {

    // "debtorId|creditorId" = amount debtor owes creditor
    private final Map<String, Double> balances = new HashMap<>();

    private String key(String debtorId, String creditorId) {
        return debtorId + "|" + creditorId;
    }

    public void update(User paidBy, List<Split> splits) {
        for (Split split : splits) {
            User debtor = split.getUser();

            // Payer doesn't owe themselves
            if (debtor.getUserId().equals(paidBy.getUserId())) continue;

            String k = key(debtor.getUserId(), paidBy.getUserId());
            balances.merge(k, split.getAmount(), Double::sum);
        }
    }

    // Return net balance for a user
    // Positive = others owe this user
    // Negative = this user owes others
    public double getNetBalance(String userId) {
        double net = 0.0;
        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            if (parts[1].equals(userId)) net += entry.getValue(); // others owe user
            if (parts[0].equals(userId)) net -= entry.getValue(); // user owes others
        }
        return net;
    }
}
// ============================================================
// 7. OBSERVER PATTERN (optional — include only if interviewer
//    asks about notifications)
// SOLID: OCP — add new notifier = new class, ExpenseManager unchanged
// SOLID: DIP — ExpenseManager depends on interface, not on concrete notifier
// ============================================================
// interface ExpenseObserver {
//     void onExpenseAdded(User paidBy, List<Split> splits);
// }

// class ConsoleNotifier implements ExpenseObserver {
//     @Override
//     public void onExpenseAdded(User paidBy, List<Split> splits) {
//         for (Split s : splits) {
//             if (!s.getUser().getUserId().equals(paidBy.getUserId())) {
//                 System.out.printf("Notify %s: you owe %s -> %.2f%n",
//                     s.getUser().getName(), paidBy.getName(), s.getAmount());
//             }
//         }
//     }
// }

// ============================================================
// 8. EXPENSE MANAGER — Singleton + Facade
//
// Singleton: one shared instance managing global ledger state.
// Facade:    client calls one method (addExpense) instead of
//            manually wiring strategy + balance + notifications.
// SOLID: SRP — coordinates other classes, implements nothing itself.
// SOLID: DIP — depends on SplitStrategy + ExpenseObserver abstractions.
// ============================================================
class ExpenseManager {

    private static ExpenseManager instance;

    private final BalanceSheet balanceSheet = new BalanceSheet();
    // private final List<ExpenseObserver> observers    = new ArrayList<>();

    private ExpenseManager() {}

    public static synchronized ExpenseManager getInstance() {
        if (instance == null) instance = new ExpenseManager();
        return instance;
    }

    // public void addObserver(ExpenseObserver observer) {
    //     observers.add(observer);
    // }

    public void addExpense(double amount,
                           User paidBy,
                           List<User> involvedUsers,
                           SplitType type,
                           Map<String, Double> inputs) {

        SplitStrategy strategy = SplitFactory.getStrategy(type);
        List<Split>   splits   = strategy.calculate(involvedUsers, amount, inputs);

        balanceSheet.update(paidBy, splits);

        // for (ExpenseObserver ob : observers) {
        //     ob.onExpenseAdded(paidBy, splits);
        // }

        System.out.printf("[ADDED] %s paid %.2f (%s split)%n",
            paidBy.getName(), amount, type);
    }

    // Returns net balance — positive means others owe this user,
    // negative means this user owes others
    public double getNetBalance(String userId) {
        return balanceSheet.getNetBalance(userId);
    }
} 

// ============================================================
// 9. DEMO
// ============================================================
public class Splitwise_SDE1_Clean {
    public static void main(String[] args) {

        User alice = new User("u1", "Alice");
        User bob   = new User("u2", "Bob");
        User carol = new User("u3", "Carol");

        ExpenseManager manager = ExpenseManager.getInstance();

        // Alice pays 300 — equal split among all 3
        manager.addExpense(300, alice,
            Arrays.asList(alice, bob, carol),
            SplitType.EQUAL, null);

        // Bob pays 100 — Alice owes 60, Carol owes 40
        Map<String, Double> exact = new HashMap<>();
        exact.put("u1", 60.0);
        exact.put("u2", 0.0);
        exact.put("u3", 40.0);
        manager.addExpense(100, bob,
            Arrays.asList(alice, bob, carol),
            SplitType.EXACT, exact);

        // Print net balances
        System.out.println("\n--- Net Balances ---");
        for (User u : Arrays.asList(alice, bob, carol)) {
            double net = manager.getNetBalance(u.getUserId());
            if (net > 0.001) {
                System.out.printf("%s is owed: %.2f%n", u.getName(), net);
            } else if (net < -0.001) {
                System.out.printf("%s owes: %.2f%n", u.getName(), Math.abs(net));
            } else {
                System.out.printf("%s is settled up%n", u.getName());
            }
        }
    }
}
/*
 * ──────────────────────────────────────────────────────────────────
 * IS THIS ACCEPTABLE FOR SDE-1 LLD?  YES — here is why:
 * ──────────────────────────────────────────────────────────────────
 *
 * ✅ Correct patterns used and named:
 *    - Strategy  → SplitStrategy interface (Equal, Exact)
 *    - Factory   → SplitFactory.getStrategy()
 *    - Singleton → ExpenseManager.getInstance()
 *    - Observer  → ExpenseObserver (optional but included)
 *    - Facade    → ExpenseManager hides all wiring from caller
 *
 * ✅ SOLID demonstrated:
 *    - S: each class has one job (User=data, BalanceSheet=ledger,
 *         ExpenseManager=orchestration, strategies=math)
 *    - O: new split type = new class only, nothing existing changes
 *    - L: any SplitStrategy can replace any other — same contract
 *    - I: SplitStrategy has one focused method, ExpenseObserver
 *         has one focused method
 *    - D: ExpenseManager depends on abstractions (SplitStrategy,
 *         ExpenseObserver), never on concrete classes
 *
 * ✅ Readable, runs cleanly, interview-demonstrable in 35 minutes
 *
 * ✅ Clean extension points you can MENTION without coding:
 *    - Add PERCENT: new PercentSplitStrategy + one factory case
 *    - Add settlement: SettlementService with greedy two-heap
 *    - Add groups: Group class holds List<Expense> + List<User>
 *    - Add persistence: BalanceSheetRepository interface injected
 *      into ExpenseManager (another DIP layer)
 *
 * ⚠️  What to say if asked "is your BalanceSheet simplified?":
 *    "Yes — I kept it as a running per-pair ledger for simplicity.
 *     In production I'd add a settle() method using net balances
 *     + two priority queues to minimise total transactions. Do
 *     you want me to add that?"
 * ──────────────────────────────────────────────────────────────────
 */