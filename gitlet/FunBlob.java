package gitlet;

import java.io.File;
import java.io.Serializable;

import static gitlet.Utils.readObject;
import static gitlet.Utils.writeObject;

public class FunBlob implements Serializable {
    int x;
    String msg;

    public static FunBlob fromFile(String path) {
        return readObject(new File(path), FunBlob.class);
    }

    public void saveFunBlob() {
        writeObject(new File("/Users/caizy/Desktop/test/fun"), this);
    }

    public void greeting() {
        System.out.println("hello~");
    }

}
