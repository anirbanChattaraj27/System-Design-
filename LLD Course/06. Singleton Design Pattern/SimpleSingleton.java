public class SimpleSingleton {

    // here I am creating instance variable of class type.
    private static SimpleSingleton instance = null;

    // private constructor, so that obj cant be created. 
    private SimpleSingleton() {
        System.out.println("Singleton Constructor called");
    }

    // as the constructor is private so I can not create the object of the class directly,
    // So I will use staic keyword in the method and call the function directly without new keyword 
    // I am using variable "instance" and if it is null i am calling the class, and class has its private constructor. so it will print the msg.
    // and as I have declared instance as null so once it will go thorugh if block it will not be null anymore, so it will return that instance
    public static SimpleSingleton getInstance() {
        if (instance == null) {
            instance = new SimpleSingleton();
        }
        return instance;
    }

    public static void main(String[] args) {
        SimpleSingleton s1 = SimpleSingleton.getInstance();
        SimpleSingleton s2 = SimpleSingleton.getInstance();

        System.out.println(s1 == s2); // when I am creating 2 obj of same class, it is giving true, as I can't create multiple objects here, so all obj refers to the same type
    }
}

// here I am not creating obj of the class, rather I am using static keyword in func and calling the method from class directly
// If I doesnt cteate constructor, default public constructor will create automatically