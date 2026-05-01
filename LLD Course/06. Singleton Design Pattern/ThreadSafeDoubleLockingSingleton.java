public class ThreadSafeDoubleLockingSingleton {
    private static ThreadSafeDoubleLockingSingleton instance = null;

    private ThreadSafeDoubleLockingSingleton() {
        System.out.println("Singleton Constructor Called!");
    }

    // Double check locking..
    // let say, I am using Multi-Threading, and I have 2 threads t1 and t2, 
    // so both threads will go inside the function and return obj 2 times, so it prevenet this I have used Synchronized.
    // so that 11 therad will go at a time
    public static ThreadSafeDoubleLockingSingleton getInstance() {

        if (instance == null) { // First check (no locking)

            synchronized (ThreadSafeDoubleLockingSingleton.class) { // Lock only if needed

                if (instance == null) { // Second check (after acquiring lock)
                    
                    instance = new ThreadSafeDoubleLockingSingleton();
                }
            }
        }
        return instance;
    }

    public static void main(String[] args) {
        ThreadSafeDoubleLockingSingleton s1 = ThreadSafeDoubleLockingSingleton.getInstance();
        ThreadSafeDoubleLockingSingleton s2 = ThreadSafeDoubleLockingSingleton.getInstance();

        System.out.println(s1 == s2);
    }
}