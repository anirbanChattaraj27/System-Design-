
import java.util.*;

// WHAT kind of event triggered this notification
// ── SOLID: Open/Closed ────────────────────────────────────────────────────
// Add PAYMENT_FAILED → add enum value. Zero changes to NotificationService logic.
enum NotificationType {
    ORDER_PLACED,
    PROMO,
    SECURITY_ALERT
}

// WHICH channel this notification travels through
// ── SOLID: Open/Closed ────────────────────────────────────────────────────
// Add WHATSAPP → add enum value + WhatsAppSender + factory case. Service untouched.
enum Channel {
    EMAIL,
    SMS,
    PUSH
}

// ── SOLID: Single Responsibility ──────────────────────────────────────────
// Carries notification data ONLY. No sending logic, no routing logic.
// Immutable — once created, a notification's content never changes.

class Notification {
    private final NotificationType type;
    private final String title;
    private final String message;

    public Notification(NotificationType type, String title, String message) {
        this.type    = type;
        this.title   = title;
        this.message = message;
    }

    public NotificationType getType()    { return type; }
    public String getTitle()             { return title; }
    public String getMessage()           { return message; }
}


// ── SOLID: Single Responsibility ──────────────────────────────────────────
// User knows about itself — contact info and channel preferences.
// It does NOT decide when or how to send — that's NotificationService's job.
// ── Key design point: preferredChannels is a List, not a single Channel ───
// Because a user can say "I want BOTH Email AND SMS for order updates".

class User {
    private final String userId;
    private final String name;
    private final String email;
    private final String phone;
    private final String deviceToken;          // for push notifications
    private final List<Channel> preferredChannels;

    public User(String userId, String name, String email,
                String phone, String deviceToken, List<Channel> preferredChannels) {
        this.userId            = userId;
        this.name              = name;
        this.email             = email;
        this.phone             = phone;
        this.deviceToken       = deviceToken;
        this.preferredChannels = preferredChannels;
    }

    public String getUserId()               { return userId; }
    public String getName()                 { return name; }
    public String getEmail()                { return email; }
    public String getPhone()                { return phone; }
    public String getDeviceToken()          { return deviceToken; }
    public List<Channel> getPreferredChannels() { return preferredChannels; }
}

// ── Design Pattern: STRATEGY ──────────────────────────────────────────────
// "How is the notification delivered?" varies by channel.
// Each implementation encapsulates one delivery mechanism completely.
// ── SOLID: Interface Segregation ──────────────────────────────────────────
// One method only — send(). No sender is forced to implement channel-specific
// methods it doesn't need (e.g. EmailSender doesn't implement getDeviceToken()).
// ── SOLID: Open/Closed ────────────────────────────────────────────────────
// Add WhatsAppSender → new class implementing this interface + factory case.
// NotificationService.notify() — ZERO changes.

interface NotificationSender {
    void send(User user, Notification notification);
}

// ── SOLID: SRP — EmailSender knows ONLY about email delivery ──────────────
class EmailSender implements NotificationSender {
    @Override
    public void send(User user, Notification notification) {
        // In production: integrate with SMTP / SendGrid / SES
        System.out.printf("[EMAIL]  To: %s | Subject: %s | Body: %s%n",
            user.getEmail(), notification.getTitle(), notification.getMessage());
    }
}

// ── SOLID: SRP — SmsSender knows ONLY about SMS delivery ──────────────────
class SmsSender implements NotificationSender {
    @Override
    public void send(User user, Notification notification) {
        // In production: integrate with Twilio / AWS SNS
        System.out.printf("[SMS]    To: %s | %s: %s%n",
            user.getPhone(), notification.getTitle(), notification.getMessage());
    }
}

// ── SOLID: SRP — PushSender knows ONLY about push notification delivery ───
class PushSender implements NotificationSender {
    @Override
    public void send(User user, Notification notification) {
        // In production: integrate with FCM (Firebase Cloud Messaging)
        System.out.printf("[PUSH]   To device: %s | %s: %s%n",
            user.getDeviceToken(), notification.getTitle(), notification.getMessage());
    }
}


// ── Design Pattern: FACTORY ───────────────────────────────────────────────
// The NotificationService says "I need a sender for EMAIL" — it doesn't
// say "new EmailSender()". The Factory encapsulates the creation decision.
// WHY both Strategy AND Factory? Strategy defines the CONTRACT (interface).
// Factory handles CREATION (instantiation). Different responsibilities.
// ── SOLID: Single Responsibility ──────────────────────────────────────────
// Factory's one job: map a Channel enum to the right NotificationSender.
// ── SOLID: Open/Closed ────────────────────────────────────────────────────
// Add WHATSAPP: add "case WHATSAPP: return new WhatsAppSender()". Done.
// ── SOLID: Dependency Inversion ───────────────────────────────────────────
// Returns NotificationSender (interface), never a concrete type to callers.

class NotificationSenderFactory {
    public static NotificationSender getSender(Channel channel) {
        switch (channel) {
            case EMAIL: return new EmailSender();
            case SMS:   return new SmsSender();
            case PUSH:  return new PushSender();
            default: throw new IllegalArgumentException("Unknown channel: " + channel);
        }
    }
}

// ── Design Pattern: OBSERVER ──────────────────────────────────────────────
// NotificationService IS the "Subject" in Observer pattern.
// Users are the "Observers" — they register interest in specific event types.
// When an event fires, the subject notifies ALL registered observers for
// that type. The event source doesn't know anything about its subscribers.
// ── Design Pattern: SINGLETON ─────────────────────────────────────────────
// One global service manages all subscriptions across the app.
// ── SOLID: Single Responsibility ──────────────────────────────────────────
// NotificationService manages subscriptions and routes notifications.
// It does NOT know HOW to send — it delegates to Factory + Senders.
// ── SOLID: Dependency Inversion ───────────────────────────────────────────
// Calls NotificationSender (interface) — never a concrete sender directly.

class NotificationService {

    private static volatile NotificationService instance;

    // Observer registry: NotificationType → List of subscribed Users
    private final Map<NotificationType, List<User>> subscribers;

    private NotificationService() {
        subscribers = new HashMap<>();
    }

    public static NotificationService getInstance() {
        if (instance == null) {
            synchronized (NotificationService.class) {
                if (instance == null) {
                    instance = new NotificationService();
                }
            }
        }
        return instance;
    }

    // ── OBSERVER: subscribe ──────────────────────────────────────────
    // User says: "I want to receive ORDER_PLACED notifications."
    public void subscribe(NotificationType type, User user) {
        subscribers
            .computeIfAbsent(type, t -> new ArrayList<>())
            .add(user);
        System.out.printf("[SUBSCRIBE] %s subscribed to %s%n", user.getName(), type);
    }

    // ── OBSERVER: unsubscribe ────────────────────────────────────────
    public void unsubscribe(NotificationType type, User user) {
        List<User> list = subscribers.get(type);
        if (list != null) list.remove(user);
        System.out.printf("[UNSUBSCRIBE] %s unsubscribed from %s%n", user.getName(), type);
    }

    // ── OBSERVER: notify (the core fan-out method) ────────────────────
    // 1. Find all users subscribed to this notification's type
    // 2. For each user, loop through their preferred channels
    // 3. Ask Factory for the right sender per channel
    // 4. Delegate the actual sending to that sender (Strategy)
    public void notify(Notification notification) {
        List<User> targets = subscribers.getOrDefault(
            notification.getType(), Collections.emptyList());

        if (targets.isEmpty()) {
            System.out.println("[NOTIFY] No subscribers for: " + notification.getType());
            return;
        }

        System.out.printf("%n[NOTIFY] Sending '%s' to %d subscriber(s)...%n",
            notification.getTitle(), targets.size());

        for (User user : targets) {
            for (Channel channel : user.getPreferredChannels()) {
                // Factory picks the right Strategy implementation
                NotificationSender sender = NotificationSenderFactory.getSender(channel);
                sender.send(user, notification);  // Strategy executes
            }
        }
    }
}

public class Main {
    public static void main(String[] args) {

        NotificationService service = NotificationService.getInstance();

        // User 1: wants Email + SMS for orders, Email for promos
        User alice = new User("u1", "Alice", "alice@mail.com", "+91-9999", "dev-token-A",
            Arrays.asList(Channel.EMAIL, Channel.SMS));

        // User 2: wants only Push for orders
        User bob = new User("u2", "Bob", "bob@mail.com", "+91-8888", "dev-token-B",
            Arrays.asList(Channel.PUSH));

        // Subscribe both to ORDER_PLACED; only Alice to PROMO
        service.subscribe(NotificationType.ORDER_PLACED, alice);
        service.subscribe(NotificationType.ORDER_PLACED, bob);
        service.subscribe(NotificationType.PROMO, alice);

        // Fire an ORDER_PLACED event — both users notified, different channels
        service.notify(new Notification(
            NotificationType.ORDER_PLACED,
            "Order Confirmed!",
            "Your order has been placed."));

        // Fire a PROMO event — only Alice gets it
        service.notify(new Notification(
            NotificationType.PROMO,
            "50% Off Today!",
            "Use code SAVE50 at checkout."));

        // Bob unsubscribes from ORDER_PLACED
        service.unsubscribe(NotificationType.ORDER_PLACED, bob);

        // Fire another order — only Alice gets it now
        service.notify(new Notification(
            NotificationType.ORDER_PLACED,
            "Order Shipped!",
            "Your order is on the way."));
    }
}

/* Expected output:
[SUBSCRIBE] Alice subscribed to ORDER_PLACED
[SUBSCRIBE] Bob subscribed to ORDER_PLACED
[SUBSCRIBE] Alice subscribed to PROMO

[NOTIFY] Sending 'Order Confirmed!' to 2 subscriber(s)...
[EMAIL]  To: alice@mail.com | Subject: Order Confirmed! | Body: Your order has been placed.
[SMS]    To: +91-9999 | Order Confirmed!: Your order has been placed.
[PUSH]   To device: dev-token-B | Order Confirmed!: Your order has been placed.

[NOTIFY] Sending '50% Off Today!' to 1 subscriber(s)...
[EMAIL]  To: alice@mail.com | Subject: 50% Off Today! | Body: Use code SAVE50 at checkout.

[UNSUBSCRIBE] Bob unsubscribed from ORDER_PLACED

[NOTIFY] Sending 'Order Shipped!' to 1 subscriber(s)...
[EMAIL]  To: alice@mail.com | Subject: Order Shipped! | Body: Your order is on the way.
[SMS]    To: +91-9999 | Order Shipped!: Your order is on the way.
*/