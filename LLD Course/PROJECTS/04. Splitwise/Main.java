//  simple easy code

import java.util.*;

// ============================================================
// 1. ENUM
// ============================================================
enum SplitType {
    EQUAL, EXACT
}

// ============================================================
// 2. PLAIN DATA CLASSES
// Only hold data. No calculation logic lives here.
// ============================================================
class User {
    private final String id;
    private final String name;

    public User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
}

class Split {
    private final User user;
    private final double amount;

    public Split(User user, double amount) {
        this.user = user;
        this.amount = amount;
    }

    public User getUser() { return user; }
    public double getAmount() { return amount; }
}

// ============================================================
// 3. STRATEGY PATTERN — one class per split type.
// Each strategy takes the total amount and the list of users
// involved, and returns how much each person owes.
//
// Note: "exactAmounts" is a simple List<Double>, lined up by
// index with the "users" list (users.get(i) owes exactAmounts.get(i)).
// This avoids map lookups and keeps the calculation a plain loop.
// ============================================================
interface SplitStrategy {
    List<Split> calculate(double totalAmount, List<User> users, List<Double> exactAmounts);
}

class EqualSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculate(double totalAmount, List<User> users, List<Double> exactAmounts) {
        double sharePerPerson = totalAmount / users.size();

        List<Split> splits = new ArrayList<>();
        for (User user : users) {
            splits.add(new Split(user, sharePerPerson));
        }
        return splits;
    }
}

class ExactSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculate(double totalAmount, List<User> users, List<Double> exactAmounts) {

        // simple validation: exact amounts must add up to the total
        double sum = 0;
        for (double amount : exactAmounts) {
            sum += amount;
        }
        if (sum != totalAmount) {
            throw new IllegalArgumentException("Exact amounts (" + sum + ") do not add up to total (" + totalAmount + ")");
        }

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            splits.add(new Split(users.get(i), exactAmounts.get(i)));
        }
        return splits;
    }
}

// ============================================================
// 4. FACTORY — picks the right strategy so ExpenseManager
// doesn't need an if/else chain.
// ============================================================
class SplitFactory {
    public static SplitStrategy getStrategy(SplitType type) {
        if (type == SplitType.EQUAL) {
            return new EqualSplitStrategy();
        } else if (type == SplitType.EXACT) {
            return new ExactSplitStrategy();
        }
        throw new IllegalArgumentException("Unsupported split type: " + type);
    }
}

// ============================================================
// 5. EXPENSE — plain data holder for one expense and its splits.
// ============================================================
class Expense {
    private final String id;
    private final double amount;
    private final User paidBy;
    private final List<Split> splits;

    public Expense(String id, double amount, User paidBy, List<Split> splits) {
        this.id = id;
        this.amount = amount;
        this.paidBy = paidBy;
        this.splits = splits;
    }

    public String getId() { return id; }
    public double getAmount() { return amount; }
    public User getPaidBy() { return paidBy; }
    public List<Split> getSplits() { return splits; }
}

// ============================================================
// 6. EXPENSE MANAGER — the entry point. Creates expenses using
// the right strategy, and can print them. No ledger, no netting,
// no balance sheet — just "what did this one expense look like".
// ============================================================
class ExpenseManager {
    private final List<Expense> expenses = new ArrayList<>();
    private int expenseCounter = 0;

    public Expense addExpense(double amount, User paidBy, List<User> users, SplitType type, List<Double> exactAmounts) {
        SplitStrategy strategy = SplitFactory.getStrategy(type);
        List<Split> splits = strategy.calculate(amount, users, exactAmounts);

        expenseCounter++;
        Expense expense = new Expense("EXP" + expenseCounter, amount, paidBy, splits);
        expenses.add(expense);
        return expense;
    }

    public void printExpense(Expense expense) {
        System.out.println("Expense " + expense.getId() + " | Amount: " + expense.getAmount()
                + " | Paid by: " + expense.getPaidBy().getName());
        for (Split split : expense.getSplits()) {
            System.out.println("   " + split.getUser().getName() + " owes: " + split.getAmount());
        }
    }
}

// ============================================================
// 7. DEMO
// ============================================================
public class Main {
    public static void main(String[] args) {
        User alice = new User("u1", "Alice");
        User bob   = new User("u2", "Bob");
        User carol = new User("u3", "Carol");

        ExpenseManager manager = new ExpenseManager();

        // Equal split: Alice pays 300, split among all 3
        Expense e1 = manager.addExpense(300, alice, Arrays.asList(alice, bob, carol), SplitType.EQUAL, null);
        manager.printExpense(e1);

        // Exact split: Bob pays 100 -> Alice owes 60, Bob owes 0, Carol owes 40
        Expense e2 = manager.addExpense(100, bob, Arrays.asList(alice, bob, carol), SplitType.EXACT,
                Arrays.asList(60.0, 0.0, 40.0));
        manager.printExpense(e2);
    }
}

/*
1.
Design a simplified version of Splitwise where:

Users can be added to the system.
A user can add an expense that they paid for, and specify how it should be split among a group of people.
Splits can be of two types (keep it to two for simplicity — a third can be added later):
Equal — split the amount evenly among everyone involved.
Exact — the payer specifies exactly how much each person owes.
The system should be able to print/show how the expense was split.

We are intentionally not building a running ledger (a "who owes whom overall" balance sheet with debt-netting). That's a real Splitwise feature, but it adds map-of-maps complexity that isn't needed to demonstrate good LLD. If interviewer asks for it as a follow-up, you extend the design — see Section 7.



2. Functional

Add a user.
Add an expense: amount, who paid, who it's split among, and the split type.
Support EQUAL and EXACT split types.
Show the split details for an expense (who owes how much for that expense).

3. Non-functional

Easy to add a new split type (e.g. PERCENT) later without changing existing code.
Clean separation between "how a split is calculated" and "how an expense is stored".


4.
User
 - id, name

Split
 - user: User
 - amount: double

SplitType (enum)
 - EQUAL, EXACT

SplitStrategy (interface)
 + calculate(totalAmount, users, exactAmounts): List<Split>

EqualSplitStrategy implements SplitStrategy
ExactSplitStrategy implements SplitStrategy

SplitFactory
 + getStrategy(SplitType): SplitStrategy

Expense
 - id, amount, paidBy: User, splits: List<Split>

ExpenseManager
 + addExpense(...): Expense
 + printExpense(Expense): void


5. Talking Points / How to Extend (say these out loud in the interview)
"How do I add a PERCENT split?" — Add PercentSplitStrategy implements SplitStrategy, compute amount = (percent / 100.0) * totalAmount in the same loop style as ExactSplitStrategy, register it in SplitFactory. Zero changes to ExpenseManager or Expense.
"How do I add a running balance / who-owes-whom-overall feature?" — Add a small class, say Ledger, that ExpenseManager updates after every addExpense call. Keep it as a simple List of (fromUserId, toUserId, amount) entries rather than nested maps if you want to keep it readable — you only need nested-map netting if the interviewer specifically asks for balance simplification/netting.
"How do I support groups of users?" — Add a Group class holding a List<User> and List<Expense>; ExpenseManager.addExpense takes a Group instead of a raw List<User>. Everything else is unchanged.
"Why Strategy + Factory and not just an if/else in ExpenseManager?" — Because Open/Closed: adding a new split type shouldn't require editing tested code in ExpenseManager. The if/else lives in exactly one place (SplitFactory), and even that can be replaced with a Map<SplitType, Supplier<SplitStrategy>> if asked to remove the if/else entirely.
"Why List<Double> instead of Map<String, Double> for exact amounts? — Simpler to reason about and explain live; the trade-off is it depends on users and exactAmounts staying in the same order, which you should call out as a limitation if asked ("in production I'd likely use a Map<userId, amount> for safety, but keeping it index-based here for simplicity").   

*/