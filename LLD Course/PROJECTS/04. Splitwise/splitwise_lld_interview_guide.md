# Splitwise LLD — The 60-Minute Interview Playbook

This is written the way you should actually *run* the interview, not just the way you'd document the answer afterward. Read Part 1–5 as your game plan, then Part 6 has the annotated code.

---

## PART 1: The clock — how 60 minutes should split

| Time | Phase | Goal |
|---|---|---|
| 0–10 min | Requirements gathering | Scope the problem, ask questions, write functional + non-functional reqs on the board |
| 10–18 min | Entity identification + quick diagram | Nouns → classes, relationships sketched fast (not a full ERD) |
| 18–25 min | Class design + patterns | Decide interfaces, relationships, which design pattern solves which flexibility problem |
| 25–50 min | Coding | Start with core domain model, then the algorithm (split strategy), then the orchestrator |
| 50–60 min | Wrap-up | Walk through a demo flow, discuss trade-offs, concurrency, extensibility, testing |

**The #1 mistake candidates make:** spending 20 minutes drawing a pretty diagram and leaving 15 minutes to code. Interviewers weight code + reasoning >> diagram polish. The diagram is a *thinking tool for you*, not a deliverable.

---

## PART 2: How the conversation should actually go (sample script)

**You (opening, don't wait to be asked):**
"Before I start, let me nail down scope — Splitwise is a big surface area. I'll assume: users can form groups, add expenses split equally/exactly/by percentage, and the system tracks who owes whom. I won't build payment gateway integration or currency conversion unless you want that. Sound right?"

*(This single move — proactively scoping — signals seniority. Interviewers almost always say "yes, that's fine, go ahead.")*

**Functional requirements to state out loud:**
- Users can be added to the system
- Users can be grouped (e.g., "Goa Trip")
- A user can add an expense paid by them, split among a set of users
- Splits supported: **EQUAL**, **EXACT** (fixed amounts), **PERCENT**
- System shows: how much a user owes / is owed, overall and per-user
- (Stretch, mention but defer) Settle-up / debt simplification, notifications

**Non-functional requirements (say these even if not asked — shows maturity):**
- Consistency of balances matters more than raw throughput (money-adjacent domain)
- Should be easy to add a new split type later (extensibility)
- Thread-safety is a bonus point, not core — mention it, don't over-engineer

**Then ask 1–2 clarifying questions, don't ask 10:**
- "Do we need debt simplification (minimizing number of transactions), or just raw pairwise balances?" — usually interviewer says "keep it simple, pairwise is fine, mention simplification as extension."
- "Single currency, correct?" — yes.

This whole phase should take **8–10 minutes**, no more. If you're still asking questions at minute 12, you're overdoing it.

---

## PART 3: The "quick ER diagram" — a 3-minute shortcut

You do **not** have time to draw a formal ER diagram with cardinalities, PK/FK boxes, etc. Use this shortcut instead — say it out loud while you jot boxes and arrows, it should take under 3 minutes:

```
User ------------------< Group >------------------ Expense
  |                                                    |
  | (paidBy)                                           | (has many)
  +----------------------------------------------------+
                                                         |
                                                         v
                                                      Split (list)
                                                    (user + share)
```

Say this as you draw it:
> "A **Group** has many **Users** and many **Expenses**. An **Expense** has one payer (a User) and a list of **Splits** — each Split ties a User to an owed amount. The split *calculation* itself is the one part that varies (equal/exact/percent), so that's a pluggable strategy, not a fixed field."

That one sentence is doing double duty — it's your ER diagram **and** your first hint of the Strategy pattern, which shows the interviewer you're already thinking about extensibility before you write a line of code.

**Rule of thumb for the shortcut:** draw only entities that will become actual classes. Skip attributes in the diagram entirely — you'll type them as fields in 30 seconds once you're coding. The diagram's only job is to nail down *relationships and cardinality*, nothing else.

---

## PART 4: Deciding classes — the checklist

1. **Underline nouns** in the requirements you just stated: User, Group, Expense, Split, Balance, Notification. Each becomes a candidate class.
2. **Underline verbs**: add expense, calculate split, update balance, notify user. Each becomes a candidate *method*, and clusters of related verbs hint at a *service* class (e.g., all balance-related verbs → `BalanceSheet`).
3. **Find the one thing that varies** — this is the single most important step in any LLD interview. Here it's the **split calculation logic**. Whatever varies = candidate for an interface + Strategy/Factory pattern. Interviewers are almost always testing whether you can spot this, not whether you can spell "Splitwise" in Java.
4. **Find the one-to-many / many-to-many relationships** — Group↔User (many-to-many), Expense↔Split (one-to-many), User↔Expense as payer (one-to-many). This tells you which classes hold `List<X>` references.
5. **Separate data from orchestration** — `User`, `Expense`, `Split` are dumb data holders (POJOs). `BalanceSheet` and `ExpenseManager` are the classes with logic. Say this split out loud — it's literally the Single Responsibility Principle in action and interviewers like hearing the SOLID vocabulary used naturally, not recited.

---

## PART 5: Which class to code first, and why

**Order matters — code in this sequence:**

1. **Enums** (`SplitType`) — zero dependencies, gets a quick win on the board.
2. **Plain data classes** (`User`, `Split`) — no logic, just fields + constructor. Fast to write, and everything else depends on these.
3. **The varying algorithm first** (`SplitStrategy` interface + `Equal/Exact/PercentSplitStrategy`) — do this *before* `Expense` or `ExpenseManager`. This is the heart of the problem; interviewers want to see this early, not as an afterthought at minute 55.
4. **`Expense`** — now that Split exists, Expense is just a container.
5. **`Group`** — container for Users + Expenses.
6. **`BalanceSheet`** — the class that actually mutates state (who-owes-whom map). This is where bugs live, so write it carefully and narrate your edge cases (self-payment, zero amounts).
7. **`ExpenseManager`** (Facade + Singleton) — wire everything together last. This is also naturally where you demo `main()`.

**Why this order:** you're front-loading the *hard, interesting* part (the strategy pattern) while you have full mental bandwidth, and back-loading the *glue code* (Facade) which is fast to write and hard to get "wrong" — a good hedge if you're running low on time.

---

## PART 6: Full annotated Java code

```java
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
    void validate(List<Split> splits, double totalAmount);
    List<Split> calculate(List<User> involvedUsers, double totalAmount, Map<String, Double> shareInputs);
}

class EqualSplitStrategy implements SplitStrategy {
    @Override
    public void validate(List<Split> splits, double totalAmount) {
        // no upfront validation needed for equal split
    }

    @Override
    public List<Split> calculate(List<User> users, double totalAmount, Map<String, Double> shareInputs) {
        List<Split> result = new ArrayList<>();
        double share = totalAmount / users.size();
        for (User u : users) {
            result.add(new Split(u, share));
        }
        return result;
    }
}

class ExactSplitStrategy implements SplitStrategy {
    @Override
    public void validate(List<Split> splits, double totalAmount) {
        double sum = splits.stream().mapToDouble(Split::getAmount).sum();
        if (Math.abs(sum - totalAmount) > 0.01) {
            throw new IllegalArgumentException("Exact split amounts don't add up to total");
        }
    }

    @Override
    public List<Split> calculate(List<User> users, double totalAmount, Map<String, Double> shareInputs) {
        List<Split> result = new ArrayList<>();
        for (User u : users) {
            result.add(new Split(u, shareInputs.get(u.getUserId())));
        }
        validate(result, totalAmount);
        return result;
    }
}

class PercentSplitStrategy implements SplitStrategy {
    @Override
    public void validate(List<Split> splits, double totalAmount) {
        double sumPercent = splits.stream()
                .mapToDouble(s -> (s.getAmount() / totalAmount) * 100)
                .sum();
        if (Math.abs(sumPercent - 100.0) > 0.01) {
            throw new IllegalArgumentException("Percentages don't add up to 100");
        }
    }

    @Override
    public List<Split> calculate(List<User> users, double totalAmount, Map<String, Double> shareInputs) {
        List<Split> result = new ArrayList<>();
        for (User u : users) {
            double percent = shareInputs.get(u.getUserId());
            result.add(new Split(u, (percent / 100.0) * totalAmount));
        }
        validate(result, totalAmount);
        return result;
    }
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

    public Expense addExpense(double amount, User paidBy, List<User> involvedUsers,
                               SplitType type, Map<String, Double> shareInputs) {
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
public class SplitwiseDemo {
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
```

---

## PART 7: SOLID — one-line justification per class

| Class | Principle | Why |
|---|---|---|
| `User`, `Split`, `Expense` | **S**RP | Pure data holders — no business logic mixed in |
| `SplitStrategy` + implementations | **O**CP | Add `NoSplitStrategy` or `SplitByShares` later without touching `ExpenseManager` |
| Any `Split*Strategy` | **L**SP | Every strategy is fully substitutable wherever `SplitStrategy` is expected — no strategy throws on inputs the interface contract allows |
| `SplitStrategy`, `ExpenseObserver` | **I**SP | Small, focused interfaces — a notifier doesn't need split methods and vice versa |
| `ExpenseManager` | **D**IP | Depends on `SplitStrategy` / `ExpenseObserver` abstractions, constructed via `SplitFactory` — never `new EqualSplitStrategy()` directly inside business logic |

---

## PART 8: Design patterns — what and why

| Pattern | Where | Why this pattern specifically |
|---|---|---|
| **Strategy** | `SplitStrategy` hierarchy | The split *algorithm* is the one axis of variation in the whole problem — textbook Strategy use case |
| **Factory** | `SplitFactory` | Centralizes "which strategy for which enum" so `ExpenseManager` stays clean and this logic is testable in isolation |
| **Observer** | `ExpenseObserver` / `NotificationService` | Decouples "an expense happened" from "who cares and how they're told" — lets you add SMS/email/push notifiers later with zero changes to `ExpenseManager` |
| **Singleton** | `ExpenseManager` | Only one global ledger/manager should exist per app instance — avoids scattered, inconsistent state |
| **Facade** | `ExpenseManager.addExpense()` | Client just calls one method instead of manually invoking factory → strategy → balance sheet → observers each time |

*(If the interviewer pushes on Singleton — a fair critique — say: "In a real backend this would be a Spring-managed singleton bean via DI rather than a hand-rolled static instance; I used the classic Singleton here just to keep the demo self-contained.")*

---

## PART 9: If asked to extend for SDE-2 level

Mention these even if you don't code all of them — this is often the "extend the design" follow-up in the last 5–10 minutes:

1. **Debt simplification** — minimize number of transactions to settle a group (greedy: repeatedly match max creditor with max debtor — heap-based, O(n log n)).
2. **Concurrency** — `BalanceSheet` map mutations need locking or `ConcurrentHashMap` + atomic merge if multiple expenses can be added simultaneously.
3. **Persistence** — swap in-memory maps for a repository interface (DIP again) backed by a DB.
4. **Currency support** — add a `Money` value object instead of raw `double` (also avoids floating-point rounding bugs — worth flagging proactively).
5. **Idempotency** — expense IDs should be generated/validated to avoid duplicate-add on retry.

---

## PART 10: Things to say out loud during coding (signals seniority)

- "I'm keeping `User`/`Split`/`Expense` as anemic data classes on purpose — logic lives in the manager/strategy classes."
- "I'm using `double` for money here for brevity, but flagging that production code should use `BigDecimal` or integer cents to avoid rounding errors."
- "I validate *after* calculating for Exact/Percent because I want one code path — but I could validate raw input earlier too if you want fail-fast behavior."
- "This Singleton would become a Spring `@Service` bean in a real system — I'm hand-rolling it here for a self-contained demo."

That's the full loop: scope → shortcut diagram → class checklist → code in dependency order → patterns/SOLID narration → extensions. Running this script keeps you inside 60 minutes with a working demo and a clear articulation of *why* every design choice was made — which is what's actually being graded.
