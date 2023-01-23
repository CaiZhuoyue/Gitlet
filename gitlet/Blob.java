package gitlet;

import java.io.Serializable;

import static gitlet.Repository.BLOBS_DIR;
import static gitlet.Utils.*;

public class Blob implements Serializable {
    private final String id; // blob都有对应的id，从1开始
    private final byte[] content; // 存放的内容
    private final String blobHash; // 存放的blob的SHA1名称，是根据内容content进行计算SHA1的

    public Blob(String fileName, String blobHash, byte[] content) {
        id = "1";
        this.content = content;
        this.blobHash = blobHash;
    }

    public static String getContentFromFile(String blobName) {
        Blob blob = readObject(join(BLOBS_DIR, blobName), Blob.class);
        return blob.getContent();
    }

    public static Blob fromFile(String blobName) {
        Blob blob = readObject(join(BLOBS_DIR, blobName), Blob.class);
        return blob;
    }

    public String toString() {
        return "Blob id:" + id + "\nContent:" + (new String(content));
    }

    public void saveBlob() {
        writeObject(join(BLOBS_DIR, blobHash), this); // 当前commit写到文件中
    }

    public String getContent() {
        String s = new String(content);
        return s;
    }
}
