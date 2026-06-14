
/*
Virtual Proxy: Delays expensive object creation. It optimizes performance and memory by creating 
heavy objects only when needed.
*/
interface IImage {
    void display();
}

class RealImage implements IImage {
    private String filename;

    public RealImage(String file) {
        this.filename = file;
        System.out.println("[RealImage] Loading image from disk: " + filename);
    }

    @Override
    public void display() {
        System.out.println("[RealImage] Displaying " + filename);
    }
}

class ImageProxy implements IImage {
    private RealImage realImage;
    private String filename;

    public ImageProxy(String file) {
        this.filename = file;
        this.realImage = null;
    }

    @Override
    public void display() {
        if (realImage == null) {
            realImage = new RealImage(filename);
        }
        realImage.display();
    }
}

public class VirtualProxy {
    public static void main(String[] args) {
        IImage image1 = new ImageProxy("sample.jpg");
        image1.display();
    }
}

/*
The User (main method): Thinks it is talking directly to a regular image object. It calls .display().
The Proxy (ImageProxy): Intercepts that request. It acts as a middleman or "gatekeeper."The Real Object 
(RealImage): Does the actual hard work (loading and printing) only when the proxy tells it to
*/