package gitlet;

import java.io.Serializable;
import java.util.*;

import static gitlet.Repository.INDEX_FILE;
import static gitlet.Utils.readObject;

public class Stage implements Serializable {
    private TreeMap<String, String> fileToBlob; // 存放一个stage状态的各种file
    private Set<String> removedFileToBlob;

    public Stage() {
        fileToBlob = new TreeMap<>();
        removedFileToBlob = new HashSet<>();
    }

    public static Stage fromFile() {
        return readObject(INDEX_FILE, Stage.class);
    }

    public static void cleanStage() {
        Stage stage = new Stage();
        stage.saveStage();
    }

    public void saveStage() {
        Utils.writeObject(INDEX_FILE, this);
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

    public Set<String> getRemovedFileToBlob() {
        return removedFileToBlob;
    }

    public void addRm(String fileName) {
        removedFileToBlob.add(fileName);
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
}
