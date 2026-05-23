// --- Strategy Interface ---
interface PaymentStrategy {
    void pay(int amount);
}

// --- Concrete Strategy 1 ---
class CreditCardPayment implements PaymentStrategy {

    private String cardNumber;

    public CreditCardPayment(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    @Override
    public void pay(int amount) {
        System.out.println(amount + " paid using Credit Card.");
        System.out.println("Card Number: " + cardNumber);
    }
}

// --- Concrete Strategy 2 ---
class UpiPayment implements PaymentStrategy {

    private String upiId;

    public UpiPayment(String upiId) {
        this.upiId = upiId;
    }

    @Override
    public void pay(int amount) {
        System.out.println(amount + " paid using UPI.");
        System.out.println("UPI ID: " + upiId);
    }
}

// --- Concrete Strategy 3 ---
class PaypalPayment implements PaymentStrategy {

    private String email;

    public PaypalPayment(String email) {
        this.email = email;
    }

    @Override
    public void pay(int amount) {
        System.out.println(amount + " paid using PayPal.");
        System.out.println("Email: " + email);
    }
}

// --- Concrete Strategy 4 ---
class CashOnDelivery implements PaymentStrategy {

    @Override
    public void pay(int amount) {
        System.out.println(amount + " will be paid using Cash On Delivery.");
    }
}

// --- Context Class ---
class ShoppingCart {

    private PaymentStrategy paymentStrategy;

    // Set payment method at runtime
    public void setPaymentStrategy(PaymentStrategy paymentStrategy) {
        this.paymentStrategy = paymentStrategy;
    }

    // Checkout
    public void checkout(int amount) {

        if (paymentStrategy == null) {
            System.out.println("Please select a payment method.");
            return;
        }

        paymentStrategy.pay(amount);
    }
}

// --- Main Class ---
public class StrategyPatternDemo {

    public static void main(String[] args) {

        ShoppingCart cart = new ShoppingCart();

        // Pay using Credit Card
        cart.setPaymentStrategy(new CreditCardPayment("1234-5678-9999"));
        cart.checkout(5000);

        System.out.println("----------------");

        // Pay using UPI
        cart.setPaymentStrategy(new UpiPayment("anirban@upi"));
        cart.checkout(2000);

        System.out.println("----------------");

        // Pay using PayPal
        cart.setPaymentStrategy(new PaypalPayment("test@gmail.com"));
        cart.checkout(7000);

        System.out.println("----------------");

        // Cash on Delivery
        cart.setPaymentStrategy(new CashOnDelivery());
        cart.checkout(1500);
    }
}