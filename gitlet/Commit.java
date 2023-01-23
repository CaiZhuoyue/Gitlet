package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

import static gitlet.Repository.COMMITS_DIR;
import static gitlet.Utils.*;

public class Commit implements Serializable {
    private final String message; // 提交的信息
    private final String timestamp; // 时间戳
    private final String parent;
    private final String parent2;
    // 表示这个commit中存放的blobs的地址
    private final String commitHash;
    // 表示这个commit的父母的地址
    private final TreeMap<String, String> fileToBlob;

    public Commit(String msg, String p, String q) {
        fileToBlob = new TreeMap<>();

        if (!p.equals("")) {
            // 复制parent commit中的东西
            File file = join(COMMITS_DIR, p);
            Commit lastCommit = readObject(file, Commit.class);
            add(lastCommit.getFileToBlob());
        }
        message = msg;
        parent = p;
        parent2 = q;
        timestamp = getTimeStamp();
        commitHash = generateHash();
    }

    public void add(TreeMap<String, String> ftb) {
        for (String fileName : ftb.keySet()) {
            add(fileName, ftb.get(fileName));
        }
    }

    public TreeMap<String, String> getFileToBlob() {
        return fileToBlob;
    }

    private String getTimeStamp() {
        String ptn = "EEE MMM d HH:mm:ss yyyy Z";
        DateFormat dateFormat = new SimpleDateFormat(ptn, Locale.US);
        if (parent.equals("")) {
            return dateFormat.format(new Date(0));
        } else {
            return dateFormat.format(new Date());
        }
    }

    private String generateHash() {
        // 获取所有内容的SHA1-commitHash
        return Utils.sha1(message, timestamp, parent, fileToBlob.toString());
    }

    public void add(String fileName, String blob) {
        fileToBlob.put(fileName, blob);
    }

    public Commit(String msg, String p) {
        fileToBlob = new TreeMap<>();

        if (!p.equals("")) {
            // 复制parent commit中的东西
            File file = join(COMMITS_DIR, p);
            Commit lastCommit = readObject(file, Commit.class);
            add(lastCommit.getFileToBlob());
        }
        message = msg;
        parent = p;
        parent2 = "";
        timestamp = getTimeStamp();
        commitHash = generateHash();
    }

    public static Commit fromFile(String commitName) {
        File file = join(COMMITS_DIR, commitName);
        Commit commit = readObject(file, Commit.class);
        return commit;
    }

    public String getHash() {
        return commitHash;
    }

    public void remove(Set<String> ftb) {
        for (String fileName : ftb) {
            remove(fileName);
        }
    }

    public void remove(String fileName) {
        fileToBlob.remove(fileName);
    }

    public void saveCommit() {
        writeObject(join(COMMITS_DIR, commitHash), this);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("===");
        sb.append("\ncommit " + commitHash);
        sb.append("\nDate: " + timestamp);
        sb.append("\n" + message + "\n");
        return sb.toString();
    }

    public String getBlob(String fileName) {
        return fileToBlob.get(fileName);
    }

    public Set<String> getFiles() {
        return fileToBlob.keySet();
    }

    public boolean hasFile(String fileName) {
        return fileToBlob.containsKey(fileName);
    }

    public String getParent() {
        return parent;
    }

    public String getParent2() {
        return parent2;
    }

    public String getMessage() {
        return message;
    }
}
