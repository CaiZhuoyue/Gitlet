package gitlet;

import java.io.File;
import java.io.Serializable;

import static gitlet.Utils.*;

public class Blob implements Serializable {
    private final String blobHash; // 存放的blob的SHA1名称，是根据内容content进行计算SHA1的
    private final byte[] content; // 存放的内容

    public Blob(String blobHash, byte[] content) {
        this.blobHash = blobHash;
        this.content = content;
    }

    public static String getContentFromFile(File blobsDir, String blobName) {
        Blob blob = readObject(join(blobsDir, blobName), Blob.class);
        return blob.getContent();
    }

    public static Blob fromFile(File blobsDir, String blobName) {
        Blob blob = readObject(join(blobsDir, blobName), Blob.class);
        return blob;
    }

    public static Blob fromRemoteFile(String url, String blobName) {
        File temp = join(url, ".gitlet/objects/blobs");
        Blob blob = readObject(join(temp, blobName), Blob.class);
        return blob;
    }

    public String getContent() {
        String s = new String(content);
        return s;
    }

    public void saveBlob(File blobsDir) {
        writeObject(join(blobsDir, blobHash), this); // 当前commit写到文件中
    }
}
