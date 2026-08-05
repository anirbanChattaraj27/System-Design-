import java.util.*;

// ============================================================
// 1. ENUM — no dependencies, quick to write
// ============================================================
enum SplitType {
    EQUAL, EXACT, PERCENT
}

// ============================================================
// 2. PLAIN DATA CLASSES (POJOs)
// SOLID: Single Responsibility — these classes ONLY hold data,
// no business logic. If User ever needs auth/notifications,
// that logic goes in a separate service, not here.
// ============================================================
class User {
    private final String userId;
    private final String name;
    private final String email;

    public User(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
}

class Split {
    private final User user;
    private double amount; // amount this user owes for one expense

    public Split(User user, double amount) {
        this.user = user;
        this.amount = amount;
    }

    public User getUser() { return user; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}

// ============================================================
// 3. THE VARYING PART — Strategy Pattern
// SOLID: Open/Closed Principle — ExpenseManager never changes
// when a new split type is added; you just add a new strategy
// class. SOLID: Interface Segregation — this interface has
// exactly the methods a split algorithm needs, nothing more.
// SOLID: Dependency Inversion — ExpenseManager depends on the
// SplitStrategy abstraction, never on EqualSplitStrategy etc.
// directly.
// ============================================================
interface SplitStrategy {

    void calculate(double totalAmount, double[] shares);
}

class EqualSplitStrategy implements SplitStrategy {

    @Override
    public void calculate(double totalAmount, double[] shares) {

        double sum = 0;

        for (double share : shares) {
            sum += share;
        }

        if (sum == totalAmount)
            System.out.println("Valid Split");
        else
            System.out.println("Invalid Split");
    }
}

class ExactSplitStrategy implements SplitStrategy {

    @Override
    public void calculate(double totalAmount, double[] shares) {

        double sum = 0;

        for (double share : shares) {
            sum += share;
        }

        if (sum == totalAmount)
            System.out.println("Valid Split");
        else
            System.out.println("Invalid Split");
    }
}

class PercentSplitStrategy implements SplitStrategy {
    
}

// ============================================================
// 4. FACTORY PATTERN
// Encapsulates "which strategy to use" decision in one place.
// SOLID: SRP — object creation logic lives here, not scattered
// across ExpenseManager.
// ============================================================
class SplitFactory {
    public static SplitStrategy getStrategy(SplitType type) {
        switch (type) {
            case EQUAL:   return new EqualSplitStrategy();
            case EXACT:   return new ExactSplitStrategy();
            case PERCENT: return new PercentSplitStrategy();
            default: throw new IllegalArgumentException("Unknown split type: " + type);
        }
    }
}

// ============================================================
// 5. EXPENSE — container, depends on abstractions above
// ============================================================
class Expense {
    private final String expenseId;
    private final double amount;
    private final User paidBy;
    private final List<Split> splits;
    private final SplitType splitType;

    public Expense(String expenseId, double amount, User paidBy, List<Split> splits, SplitType splitType) {
        this.expenseId = expenseId;
        this.amount = amount;
        this.paidBy = paidBy;
        this.splits = splits;
        this.splitType = splitType;
    }

    public double getAmount() { return amount; }
    public User getPaidBy() { return paidBy; }
    public List<Split> getSplits() { return splits; }
}

// ============================================================
// 6. OBSERVER PATTERN — notify interested parties when an
// expense is added, without ExpenseManager knowing WHO is
// listening or HOW they notify (push/email/sms).
// SOLID: Open/Closed — add a new notifier without touching
// ExpenseManager. SOLID: DIP — ExpenseManager depends on the
// ExpenseObserver interface, not concrete notifiers.

// no need to create unless interviewr asks explicitly for notifications. If they don't, you can skip this part and just have ExpenseManager update the balance sheet silently.
// ============================================================
interface ExpenseObserver {
    void onExpenseAdded(Expense expense);
}

class NotificationService implements ExpenseObserver {
    @Override
    public void onExpenseAdded(Expense expense) {
        for (Split s : expense.getSplits()) {
            System.out.println("Notify " + s.getUser().getName() +
                    ": you owe " + s.getAmount() + " for expense paid by " + expense.getPaidBy().getName());
        }
    }
}

// ============================================================
// 7. GROUP
// If interviewer says Only expense sharing Then don't even create Group class. But if they say "I want to create groups of friends and share expenses within a group" then you can create this class.
// ============================================================
class Group {
    private final String groupId;
    private final List<User> members = new ArrayList<>();
    private final List<Expense> expenses = new ArrayList<>();

    public Group(String groupId) { this.groupId = groupId; }

    public void addMember(User u) { members.add(u); }
    public void addExpense(Expense e) { expenses.add(e); }
    public List<User> getMembers() { return members; }
}

// ============================================================
// 8. BALANCE SHEET — the class most likely to have bugs.
// SOLID: SRP — this class ONLY tracks who-owes-whom. It has
// no idea what a "split strategy" is; it just receives final
// numbers and updates a ledger.
// ============================================================
class BalanceSheet {
    // owes.get(A).get(B) = amount A owes B
    private final Map<String, Map<String, Double>> owes = new HashMap<>();

    public void updateBalance(User paidBy, List<Split> splits) {
        for (Split split : splits) {
            User owedByUser = split.getUser();
            if (owedByUser.getUserId().equals(paidBy.getUserId())) continue; // payer doesn't owe themself

            double amt = split.getAmount();
            adjust(owedByUser.getUserId(), paidBy.getUserId(), amt);
        }
    }

    private void adjust(String debtor, String creditor, double amount) {
        owes.computeIfAbsent(debtor, k -> new HashMap<>())
            .merge(creditor, amount, Double::sum);

        // Net off if creditor also owes debtor (keeps balances minimal)
        Map<String, Double> creditorMap = owes.get(creditor);
        if (creditorMap != null && creditorMap.containsKey(debtor)) {
            double reverse = creditorMap.get(debtor);
            double net = owes.get(debtor).get(creditor) - reverse;
            if (net >= 0) {
                owes.get(debtor).put(creditor, net);
                creditorMap.remove(debtor);
            } else {
                creditorMap.put(debtor, -net);
                owes.get(debtor).remove(creditor);
            }
        }
    }

    public void showBalances(String userId) {
        Map<String, Double> owedByUser = owes.getOrDefault(userId, Collections.emptyMap());
        for (Map.Entry<String, Double> e : owedByUser.entrySet()) {
            System.out.println(userId + " owes " + e.getKey() + ": " + e.getValue());
        }
        for (Map.Entry<String, Map<String, Double>> entry : owes.entrySet()) {
            if (entry.getValue().containsKey(userId)) {
                System.out.println(entry.getKey() + " owes " + userId + ": " + entry.getValue().get(userId));
            }
        }
    }
}

// ============================================================
// 9. EXPENSE MANAGER — Facade + Singleton.
// SOLID: SRP at the orchestration level — this class coordinates
// other classes but doesn't implement split math or ledger math
// itself. SOLID: DIP — depends on SplitStrategy and
// ExpenseObserver abstractions.
// Pattern: Singleton — one shared instance managing global state.
// Pattern: Facade — client code calls one simple method
// (addExpense) instead of manually wiring strategy + balance
// sheet + notifications every time.
// ============================================================
class ExpenseManager {
    private static ExpenseManager instance;
    private final BalanceSheet balanceSheet = new BalanceSheet();
    private final List<ExpenseObserver> observers = new ArrayList<>();
    private int expenseCounter = 0;

    private ExpenseManager() {}

    public static synchronized ExpenseManager getInstance() {
        if (instance == null) instance = new ExpenseManager();
        return instance;
    }

    public void addObserver(ExpenseObserver observer) { observers.add(observer); }

    public Expense addExpense(double amount, User paidBy, List<User> involvedUsers, SplitType type, Map<String, Double> shareInputs) {
        SplitStrategy strategy = SplitFactory.getStrategy(type);
        List<Split> splits = strategy.calculate(involvedUsers, amount, shareInputs);
        strategy.validate(splits, amount);

        Expense expense = new Expense("EXP" + (++expenseCounter), amount, paidBy, splits, type);
        balanceSheet.updateBalance(paidBy, splits);

        for (ExpenseObserver ob : observers) ob.onExpenseAdded(expense);
        return expense;
    }

    public void showBalance(String userId) { balanceSheet.showBalances(userId); }
}

// ============================================================
// 10. DEMO
// ============================================================
public class Main {
    public static void main(String[] args) {
        User alice = new User("u1", "Alice", "alice@mail.com");
        User bob   = new User("u2", "Bob", "bob@mail.com");
        User carol = new User("u3", "Carol", "carol@mail.com");

        ExpenseManager manager = ExpenseManager.getInstance();
        manager.addObserver(new NotificationService());

        // Equal split: Alice pays 300, split among all 3
        manager.addExpense(300, alice, Arrays.asList(alice, bob, carol), SplitType.EQUAL, null);

        // Exact split: Bob pays 100, Alice owes 60, Carol owes 40
        Map<String, Double> exact = new HashMap<>();
        exact.put("u1", 60.0);
        exact.put("u2", 0.0);
        exact.put("u3", 40.0);
        manager.addExpense(100, bob, Arrays.asList(alice, bob, carol), SplitType.EXACT, exact);

        manager.showBalance("u1");

    }
}