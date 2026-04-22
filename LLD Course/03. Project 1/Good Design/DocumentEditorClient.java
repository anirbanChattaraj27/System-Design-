import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
 
// Interface for document elements
interface DocumentElement {
    public abstract String render();
}

// Concrete implementation for text elements
class TextElement implements DocumentElement {
    private String text;

    public TextElement(String text) {
        this.text = text;
    }

    @Override
    public String render() {
        return text;
    }
}

// Concrete implementation for image elements
class ImageElement implements DocumentElement {
    private String imagePath;

    public ImageElement(String imagePath) {
        this.imagePath = imagePath;
    }

    @Override
    public String render() {
        return "[Image: " + imagePath + "]";
    }
}

// NewLineElement represents a line break in the document.
class NewLineElement implements DocumentElement {
    @Override
    public String render() {
        return "\n";
    }
}

// TabSpaceElement represents a tab space in the document.
class TabSpaceElement implements DocumentElement {
    @Override
    public String render() {
        return "\t";
    }
}

// Document class responsible for holding a collection of elements
class Document {
    private List<DocumentElement> documentElements = new ArrayList<>();

    public void addElement(DocumentElement element) {
        documentElements.add(element);
    }

    // Renders the document by concatenating the render output of all elements.
    public String render() {
        StringBuilder result = new StringBuilder();
        for (DocumentElement element : documentElements) {
            result.append(element.render());
        }
        return result.toString();
    }
}

// Persistence Interface
interface Persistence {
    void save(String data);
}

// FileStorage implementation of Persistence
class FileStorage implements Persistence {
    @Override
    public void save(String data) {
        try {
            FileWriter outFile = new FileWriter("document.txt");
            outFile.write(data);
            outFile.close();
            System.out.println("Document saved to document.txt");
        } catch (IOException e) {
            System.out.println("Error: Unable to open file for writing.");
        }
    }
}

// Placeholder DBStorage implementation
class DBStorage implements Persistence {
    @Override
    public void save(String data) {
        // Save to DB
    }
}

// DocumentEditor class managing client interactions
class DocumentEditor {
    private Document document;
    private Persistence storage;
    private String renderedDocument = "";

    public DocumentEditor(Document document, Persistence storage) {
        this.document = document;
        this.storage = storage;
    }

    public void addText(String text) {
        document.addElement(new TextElement(text));
    }

    public void addImage(String imagePath) {
        document.addElement(new ImageElement(imagePath));
    }

    // Adds a new line to the document.
    public void addNewLine() {
        document.addElement(new NewLineElement());
    }

    // Adds a tab space to the document.
    public void addTabSpace() {
        document.addElement(new TabSpaceElement());
    }

    public String renderDocument() {
        if (renderedDocument.isEmpty()) {
            renderedDocument = document.render();
        }
        return renderedDocument;
    }

    public void saveDocument() {
        storage.save(renderDocument());
    }
}

// Client usage example
public class DocumentEditorClient {
    public static void main(String[] args) {
        Document document = new Document();
        Persistence persistence = new FileStorage();

        DocumentEditor editor = new DocumentEditor(document, persistence);

        // Simulate a client using the editor with common text formatting features.
        editor.addText("Hello, world!");
        editor.addNewLine();
        editor.addText("This is a real-world document editor example.");
        editor.addNewLine();
        editor.addTabSpace();
        editor.addText("Indented text after a tab space.");
        editor.addNewLine();
        editor.addImage("picture.jpg");

        // Render and display the final document.
        System.out.println(editor.renderDocument());

        editor.saveDocument();
    }
}


/*
HOW SOLID PRINCIPALs ARE FOLLOWED HERE-------------------->
✅ 1. S — Single Responsibility Principle (SRP)

Each class has only one job:

TextElement → handles text rendering only
ImageElement → handles image rendering only
Document → manages collection of elements
FileStorage → saves to file
DocumentEditor → coordinates operations

✔ Example:

class Document {
    // SRP: Only responsible for managing elements and rendering
}


✅ 2. O — Open/Closed Principle (OCP)

Your system is:

Open for extension
Closed for modification

👉 You added:

NewLineElement
TabSpaceElement

WITHOUT changing existing code ✔

✔ Example:

interface DocumentElement {
    String render();
}

// New feature without modifying old code
class NewLineElement implements DocumentElement {
    public String render() { return "\n"; }
}


✅ 3. L — Liskov Substitution Principle (LSP)

Any subclass should work wherever parent is expected.

✔ Example:

List<DocumentElement> elements;
elements.add(new TextElement(...));
elements.add(new ImageElement(...));
elements.add(new NewLineElement(...));

👉 All behave correctly when render() is called


✅ 4. I — Interface Segregation Principle (ISP)

Your interfaces are small and specific:

DocumentElement → only render()
Persistence → only save()

✔ Good design:

interface Persistence {
    void save(String data);
}

👉 No unnecessary methods forced



✅ 5. D — Dependency Inversion Principle (DIP)

High-level class (DocumentEditor) does NOT depend on concrete classes.

✔ Instead of:

FileStorage storage = new FileStorage(); ❌

✔ You use:

Persistence storage;

✔ Injected via constructor:

public DocumentEditor(Document document, Persistence storage)

👉 This is Dependency Injection
*/