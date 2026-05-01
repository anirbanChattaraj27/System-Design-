public class NoSingleton {
    
    public NoSingleton() {
        System.out.println("Singleton Constructor called. New Object created.");
    }

    public static void main(String[] args) {
        NoSingleton s1 = new NoSingleton();
        NoSingleton s2 = new NoSingleton();

        System.out.println(s1 == s2);
    }
}

// S2 and S1 are different object so it should print false.
// so here we can create infinity object
// so if we make it private we can not create object