package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import static gitlet.Utils.join;
import static gitlet.Utils.readObject;

public class Stage implements Serializable {
    private TreeMap<String, String> fileToBlob; // 存放一个stage状态的各种file
    private Set<String> removedFileToBlob;

    public Stage() {
        fileToBlob = new TreeMap<>();
        removedFileToBlob = new HashSet<>();
    }

    public static Stage fromFile(File indexFile) {
        return readObject(indexFile, Stage.class);
    }

    public static void cleanStage(File indexFile) {
        Stage stage = new Stage();
        stage.saveStage(indexFile);
    }

    public static void cleanRemoteStage(String url) {
        Stage stage = new Stage();
        stage.saveRemoteStage(url);
    }

    public String getBlob(String fileName) {
        return fileToBlob.get(fileName);
    }

    public void saveStage(File indexFile) {
        Utils.writeObject(indexFile, this);
    }

    public void saveRemoteStage(String url) {
        File temp = join(url, ".gitlet/index");
        Utils.writeObject(temp, this);
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

    public boolean hasRemoved(String fileName) {
        return removedFileToBlob.contains(fileName);
    }

    public Set<String> getRemovedFiles() {
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

    public Set<String> getFiles() {
        return fileToBlob.keySet();
    }
}
