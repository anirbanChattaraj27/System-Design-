
// here we are reducing the code.
// Eager Initialization simplefies the code, no use of if-else
// disadvantage is like, it is very cost effective. if we use this and at the end of gh code if we do not use that block of code, this is a memory wastage.
// not used much for implementation

public class ThreadSafeEagerSingleton {
    private static ThreadSafeEagerSingleton instance = new ThreadSafeEagerSingleton();

    private ThreadSafeEagerSingleton() {
        System.out.println("Singleton Constructor Called!");
    }

    public static ThreadSafeEagerSingleton getInstance() {
        return instance;
    }

    public static void main(String[] args) {
        ThreadSafeEagerSingleton s1 = ThreadSafeEagerSingleton.getInstance();
        ThreadSafeEagerSingleton s2 = ThreadSafeEagerSingleton.getInstance();

        System.out.println(s1 == s2);
    }
}