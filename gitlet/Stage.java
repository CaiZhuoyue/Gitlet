package gitlet;

import java.io.Serializable;
import java.util.TreeMap;
import static gitlet.Repository.INDEX_FILE;
import static gitlet.Utils.readObject;

public class Stage implements Serializable {
    private final TreeMap<String, String> fileToBlob; // 存放一个stage状态的各种file
    private final TreeMap<String, String> removedFileToBlob;

    public Stage() {
        fileToBlob = new TreeMap<>();
        removedFileToBlob = new TreeMap<>();
    }

    public static Stage fromFile() {
        return readObject(INDEX_FILE, Stage.class);
    }

    public static void cleanStage() {
        Stage stage = new Stage();
        stage.saveStage();
    }

    public void add(String fileName, String hash) {
        if (fileToBlob.containsKey(fileName)) {
            fileToBlob.replace(fileName, hash);
        } else {
            fileToBlob.put(fileName, hash);
        }
    }

    public boolean empty() {
        return fileToBlob.isEmpty() && removedFileToBlob.isEmpty();
    }

    public boolean hasFile(String fileName) {
        return fileToBlob.containsKey(fileName);
    }

    public TreeMap<String, String> getRemovedFileToBlob() {
        return removedFileToBlob;
    }

    public void addRm(String fileName, String hash) {
        removedFileToBlob.put(fileName, hash);
    }

    public void removeRm(String fileName) {
        removedFileToBlob.remove(fileName);
    }

    public void removeAdd(String fileName) {
        fileToBlob.remove(fileName); // 删掉对应的blob文件
    }

    public TreeMap<String, String> getFileToBlob() {
        return fileToBlob;
    }

    public void saveStage() {
        Utils.writeObject(INDEX_FILE, this);
    }
}
