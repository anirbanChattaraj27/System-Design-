// Component Interface: defines a common interface for Mario and all power-up decorators.
interface Character {
    String getAbilities();
}

// Concrete Component: Basic Mario character with no power-ups.
class Mario implements Character {
    public String getAbilities() {
        return "Mario";
    }
}

// Abstract Decorator: CharacterDecorator "is-a" Character and "has-a" Character.
    // why is-a: bcz implementing Character class.
    // why has-a: bcz I have created object of Character class. and in his constructor it is just assigning that character
abstract class CharacterDecorator implements Character {
    protected Character character;  // Wrapped component

    public CharacterDecorator(Character c) {
        this.character = c;
    }
}

// Concrete Decorator: Height-Increasing Power-Up.
/// means here we are creating class with body implementation
class HeightUp extends CharacterDecorator {
    public HeightUp(Character c) {
        super(c);
    }

    public String getAbilities() {
        return character.getAbilities() + " with HeightUp";
    }
}

// Concrete Decorator: Gun Shooting Power-Up.
class GunPowerUp extends CharacterDecorator {
    public GunPowerUp(Character c) {
        super(c);
    }

    public String getAbilities() {
        return character.getAbilities() + " with Gun";
    }
}

// Concrete Decorator: Star Power-Up (temporary ability).
class StarPowerUp extends CharacterDecorator {
    public StarPowerUp(Character c) {
        super(c);
    }

    public String getAbilities() {
        return character.getAbilities() + " with Star Power (Limited Time)";
    }
}

public class DecoratorPattern {
    public static void main(String[] args) {
        // Create a basic Mario character.
        Character mario = new Mario();
        System.out.println("Basic Character: " + mario.getAbilities());

        // Decorate Mario with a HeightUp power-up.
        mario = new HeightUp(mario);
        System.out.println("After HeightUp: " + mario.getAbilities());

        // Decorate Mario further with a GunPowerUp.
        mario = new GunPowerUp(mario);
        System.out.println("After GunPowerUp: " + mario.getAbilities());

        // Finally, add a StarPowerUp decoration.
        mario = new StarPowerUp(mario);
        System.out.println("After StarPowerUp: " + mario.getAbilities());
    }
}

/*

at first I have created a Character Interface

then in Mario class I am implementing that Charater Interface and created a common method which is returning Mario

Now, Creating an absract decorator class, which is implenting Charater Interface and creating object of that class. 
and creating a constructor and passing Character obj

NOW.....
    here I am creating Concrete classess....
        HeightUp, GunPowerUp, StarPowerUp
        in these class, I am using Inheritance to call the Decorator class.
        and creating constructor and use the and provided Character object and use super (c) ----> this will return "mario" from the mario class
        O/P: "mario with HeightUp"
             "mario with GunPowerUp"
             "mario with StarPowerUp"

    NOW....
        If I want I can give other powers to mario by creating more concrete classes like flyingMario class or driveMario class etc and those will do the same thing as these HeightUp, GunPowerUp, StarPowerUp classes has done


MAIN class()
    at first I am creating obj of Mario class ----> Character mario = new Mario();

    then I started wrapping mario class with other concrete classes.
        1) wrap mario with HeightUp-----> passing mario object inside HeightUp class, in HeightUp class it has a constructor with a parameter Charater c, now mario object will become that c; and mario objecet will return "mario" || so O/P: "mario with HeightUp"
        2) wrap mario with GunPowerUp---> passing mario objecct inside GunPowerUp class, in GunPowerUp class it has a constructor with a parameter Charater c, now mario object will become that c; and mario objecet will return "mario" || so O/P: "mario with GunPowerUp"
*/