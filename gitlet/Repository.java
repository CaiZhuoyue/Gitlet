package gitlet;

//import com.puppycrawl.tools.checkstyle.utils.ScopeUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static gitlet.Utils.*;

public class Repository implements Serializable {
    private static File CWD;
    private static File GITLET_DIR;
    private static File OBJECTS_DIR;
    private static File COMMITS_DIR;
    private static File BLOBS_DIR;
    private static File HEAD_FILE;
    private static File REFS_DIR;
    private static File BRANCH_HEADS_DIR;
    private static File REMOTE_HEADS_DIR;
    private static File INDEX_FILE;
    private String currentCommit; // 当前commit的对应SHA1
    private String currentBranchPath = "refs/heads/master"; // "refs/heads/master"
    private String currentBranch = "master"; // "master"
    private Stage currentStage = new Stage();

    public Repository() {
        this(System.getProperty("user.dir"));
    }

    public Repository(String directory) {
        // 支持remote版本的构造函数
        CWD = new File(directory);
        GITLET_DIR = join(CWD, ".gitlet");
        OBJECTS_DIR = join(GITLET_DIR, "objects");
        COMMITS_DIR = join(OBJECTS_DIR, "commits");
        BLOBS_DIR = join(OBJECTS_DIR, "blobs");
        HEAD_FILE = join(GITLET_DIR, "HEAD");
        REFS_DIR = join(GITLET_DIR, "refs");
        BRANCH_HEADS_DIR = join(REFS_DIR, "heads");
        REMOTE_HEADS_DIR = join(REFS_DIR, "remotes");
        INDEX_FILE = join(GITLET_DIR, "index");

        if (GITLET_DIR.exists()) {
            currentBranchPath = Utils.readContentsAsString(HEAD_FILE);
            currentBranch = currentBranchPath.split("/")[2]; // refs/heads/master
            File f = join(GITLET_DIR, currentBranchPath);
            currentCommit = readContentsAsString(f);
            currentStage = Stage.fromFile(INDEX_FILE);
        }
    }

    public void remoteAdd(String remoteName, String remoteUrl) {

        String[] temp = remoteUrl.split("/");
        remoteUrl = "";
        for (int i = 0; i < temp.length - 1; i++) {
            remoteUrl += temp[i] + "/"; // 把.gitlet弄走了
        }

        String s = "";
        File configFile = join(GITLET_DIR, "config");
        if (configFile.exists()) {
            s = readContentsAsString(configFile);
        }

        if (s.contains("[remote " + remoteName + "]")) {
            System.out.println("A remote with that name already exists.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(s);
        sb.append("[remote " + remoteName + "]\n");
        sb.append(remoteUrl + "\n"); // 目前来讲存一下url应该就行了

        writeContents(configFile, sb.toString());
    }

    public void remoteRemove(String remoteName) {
        File configFile = join(GITLET_DIR, "config");

        String s = readContentsAsString(configFile);
        String nono = "[remote " + remoteName + "]";

        if (!s.contains(nono)) {
            System.out.println("A remote with that name does not exist.");
            return;
        }

        String newContent = "";

// 从config里面删除这一行
        try {
            Scanner scanner = new Scanner(configFile);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.contains(nono)) {
                    for (int i = 0; i < 2; i++) { // 跳过这2行的内容
                        scanner.nextLine();
                        if (!scanner.hasNextLine()) {
                            break;
                        }
                    }
                } else {
// 否则写到当前文件的config中
                    newContent += line;
                    newContent += "\n";
                }
            }
            scanner.close(); // close the scanner
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
        writeContents(configFile, newContent);
    }


    public String getUrlFromRemoteName(String remoteName) {
        File configFile = join(GITLET_DIR, "config");

        try {
            Scanner scanner = new Scanner(configFile);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.contains("[remote " + remoteName + "]")) {
                    line = scanner.nextLine();
                    return line;
                }
            }
            scanner.close(); // close the scanner
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
        return "";
    }

    /**
     * 把这个remote源的remoteBranch的所有内容都下载下来
     *
     * @param remoteName
     * @param remoteBranch
     */
    public void fetch(String remoteName, String remoteBranch) {
// 先搬运一些配置文件
// 判断remote branch是否在remote name这个仓库下存在
        String url = getUrlFromRemoteName(remoteName); // 不以.gitlet结尾

        File f = join(url, ".gitlet/refs/heads/" + remoteBranch);

        if (!f.exists()) {
            System.out.println("That remote does not have that branch.");
            return;
        }

        if (!REMOTE_HEADS_DIR.exists()) {
            REMOTE_HEADS_DIR.mkdir();
        }
// 获取remote的所有branch的头指针文件
//        System.out.println(REMOTE_HEADS_DIR);

        File outputDir = join(REMOTE_HEADS_DIR, remoteName);
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        // 输出到类似 xxx/.gitlet/refs/remotes/other
        String content = readContentsAsString(f);
        File outputFile = join(outputDir, remoteBranch);
        writeContents(outputFile, content);

        // 然后处理文件的复制操作等等
        // 把remote里面的所有blob都复制过来

        // 把和这个remoteBranch有关的所有（不在当前repo中的）commit都复制过来
        moveBlobs(url, 2);
        // remote的所有commit
        moveCommits(url, getRemoteCommits(url, content), 2);

        return;
    }

    /**
     * 从remote repo的url中获取某一个commit以及其所有的previous commits
     * url不是.gitlet结尾
     *
     * @param url
     * @param remoteHead
     * @return
     */
    public List<String> getRemoteCommits(String url, String remoteHead) {
        List<String> commits = new ArrayList<>();

        Commit commit = Commit.fromRemoteFile(url, remoteHead);

        while (!commit.getParent().equals("")) {
            commits.add(commit.getHash());
            commit = Commit.fromRemoteFile(url, commit.getParent());
        }
        return commits;
    }

    /**
     * Description: Attempts to append the current branch’s commits to the end
     * of the given branch at the given remote.
     * <p>
     * Details:
     * Only works if the remote branch’s head is in the history of the current local head,
     * which means that the local branch contains some commits in the future of the remote branch.
     * In this case, append the future commits to the remote branch.
     * Then, the remote should reset to the front of the appended commits
     * (so its head will be the same as the local head). This is called fast-forwarding.
     * <p>
     * If the Gitlet system on the remote machine exists but does not have the input branch,
     * then simply add the branch to the remote Gitlet.
     *
     * @param remoteName
     * @param remoteBranch
     */
    public void push(String remoteName, String remoteBranch) {
// step 1 找出remote branch的head，找出当前branch的head，如果没有branch就加这个branch
// step 2 判断remotehead是否是branchhead的祖先
// step 3 如果是的话，就把现在branch超前的commit和commit对应的内容放到remote的objects里面
// step 4 更新remotebranch的head内容

// 查找remoteName，获取directory的地址
        String url = getUrlFromRemoteName(remoteName);
//        System.out.println(url);
        File f = new File(url);

        if (!f.exists()) {
            System.out.println("Remote directory not found.");
            return;
        }

        String currentHead = currentCommit; // 应该是一个hash值吧
        String remoteHead = getRemoteBranchHead(url, remoteBranch);

// 找出所有超前的commit

        if (remoteHead.equals("")) { // 表示没有这个remoteHead
// 完全复制这个branch过去
//            System.out.println("没有这个branch，因此新建一个");
        }

        List<String> futureCommits = isAncestor(currentHead, remoteHead);


        if (futureCommits.isEmpty()) {
//        if (false) {
            System.out.println("Please pull down remote changes before pushing.");
            return;
        } else {
            // 所有blob
//            moveBlobs(url, 1);

            // 所有(超前的)commit
//            moveCommits(url, futureCommits, 1);

            File remoteHeadFile = join(url, ".gitlet/refs/heads/", remoteBranch);
            // 难道是这个？
            writeContents(remoteHeadFile, currentHead);
            // 然后根据那个commit把文件都弄进去
            // 根据commit的状态和文件夹的状态
            changeRemoteCommit(url, remoteHead, currentHead);
        }
        return;
    }

    /**
     * 把当前文件夹的所有blobs复制到url对应的blobs文件夹去
     *
     * @param url
     */
    public void moveBlobs(String url, int type) {
        List<String> blobs;
        if (type == 1) {
            blobs = plainFilenamesIn(BLOBS_DIR);
        } else {
            blobs = plainFilenamesIn(join(url, ".gitlet/objects/blobs"));
        }
        for (String blob : blobs) {
            File oldBlob = join(BLOBS_DIR, blob);
            File newBlob = join(url, ".gitlet/objects/blobs", blob);

            if (type == 1 && !newBlob.exists()) {
                try {
                    Files.copy(oldBlob.toPath(), newBlob.toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (type == 2 && !oldBlob.exists()) {
                try {
                    Files.copy(newBlob.toPath(), oldBlob.toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * 把当前文件夹的一些commits复制到url对应的commits文件夹去
     *
     * @param url
     * @param commits
     */
    public void moveCommits(String url, List<String> commits, int type) {
        for (String commit : commits) {
            File oldCommit = join(COMMITS_DIR, commit);
            File newCommit = join(url, ".gitlet/objects/commits", commit);
            if (type == 1) {
                writeContents(newCommit, readContents(oldCommit));
            } else if (type == 2) {
                writeContents(oldCommit, readContents(newCommit));
            }
        }
    }

    public String getRemoteBranchHead(String url, String remoteBranch) {
        File f = join(url, ".gitlet/refs/heads", remoteBranch);
        if (!f.exists()) {
            return "";
        } else {
            return readContentsAsString(f);
        }
    }

    /**
     * 判断remoteHead是不是curHead的祖先
     * 遍历curHead的所有祖先，看看有没有remoteHead的hash
     *
     * @param curHead
     * @param remoteHead
     * @return
     */
    public List<String> isAncestor(String curHead, String remoteHead) {
        List<String> futureCommits = new ArrayList<>();

        int length = commitLength(curHead);

        Commit commit1 = Commit.fromFile(COMMITS_DIR, curHead);
        String parent1;

        for (int i = 0; i < length - 1; i++) {
            if (commit1.getHash().equals(remoteHead)) {
                break;
            } else {
//                System.out.println("future commit: " + commit1.getHash());
                futureCommits.add(commit1.getHash());
            }
            parent1 = commit1.getParent();
            commit1 = Commit.fromFile(COMMITS_DIR, parent1);
        }
        return futureCommits;
    }

    public void pull(String remoteName, String remoteBranch) {
// fetch之后merge
        fetch(remoteName, remoteBranch);
        merge(remoteBranch, 2);
// 感觉会很复杂
        return;
    }

    public boolean check(String command) {
        return command.equals("init") || GITLET_DIR.exists();
    }

    /**
     * 初始化gitlet仓库
     */
    public void init() {
        if (GITLET_DIR.exists()) {
            String m;
            m = "A Gitlet version-control system already exists in the current directory.";
            System.out.println(m);
            return;
        } else {
            GITLET_DIR.mkdir();
        }

        if (!OBJECTS_DIR.exists()) {
            OBJECTS_DIR.mkdir();
        }
        if (!HEAD_FILE.exists()) {
            try {
                HEAD_FILE.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!REFS_DIR.exists()) {
            REFS_DIR.mkdir();
        }
        if (!BRANCH_HEADS_DIR.exists()) {
            BRANCH_HEADS_DIR.mkdir();
        }
        if (!COMMITS_DIR.exists()) {
            COMMITS_DIR.mkdir();
        }
        if (!BLOBS_DIR.exists()) {
            BLOBS_DIR.mkdir();
        }
// 生成初始的commit
        Commit commit = new Commit(COMMITS_DIR, "initial commit", "", "");
// HEAD文件里面存放这个初始commit的文件位置
        Utils.writeContents(HEAD_FILE, "refs/heads/master");
// heads/master文件存放这个commit的SHA1
        writeRefs("master", commit.getHash());
        commit.saveCommit(COMMITS_DIR); // 保存当前commit
        Stage.cleanStage(INDEX_FILE);
    }

    public void writeRefs(String branch, String commitID) {
        writeContents(join(BRANCH_HEADS_DIR, branch), commitID);
    }

    /**
     * 输出当前commit和所有的parent commit
     */
    public void log() {
        Commit commit = Commit.fromFile(COMMITS_DIR, currentCommit);

        System.out.println(commit);
        String curCommit = commit.getParent();
        while (!curCommit.equals("")) {
            commit = Commit.fromFile(COMMITS_DIR, curCommit);
            System.out.println(commit);
            curCommit = commit.getParent();
        }
    }

    /**
     * 按照字典序输出所有的commit信息
     */
    public void globalLog() {
        List<String> commits = plainFilenamesIn(COMMITS_DIR);
        for (String id : commits) {
            System.out.println(readObject(join(COMMITS_DIR, id), Commit.class));
        }
    }

    /**
     * 找到和message匹配的commits
     *
     * @param message
     */
    public void find(String message) {
        List<String> commits = plainFilenamesIn(COMMITS_DIR);
        int count = 0;

        for (String id : commits) {
            File file = join(COMMITS_DIR, id);
            Commit commit = readObject(file, Commit.class);
            if (message.equals(commit.getMessage())) { // 注意不能用==
                System.out.println(id);
                count++;
            }
        }
        if (count <= 0) {
            System.out.println("Found no commit with that message.");
        }
    }

    /**
     * 根据参数个数 调用不同checkout函数
     *
     * @param args
     */
    public void checkout(String[] args) {
// 不同个数的参数哦
        if (args.length == 2) { // checkout [branch]
            checkout(args[1]);
        } else if (args.length == 3) { // checkout -- [file name]
            if (!args[1].equals("--")) {
                System.out.println("Incorrect operands.");
                return;
            }
            String fileName = args[2];
            checkout(getBranchHead(currentBranch, 1, ""), fileName);
        } else { // checkout [commit id] -- [file name]
            if (!args[2].equals("--")) {
                System.out.println("Incorrect operands.");
                return;
            }
            String commitID = args[1];
            String fileName = args[3];
            checkout(commitID, fileName);
        }
    }

    /**
     * checkout一个分支
     *
     * @param branch
     */
    public void checkout(String branch) {
        String[] temp = branch.split("/");
        int type = temp.length;

        if (branch.equals(currentBranch)) {
            System.out.println("No need to checkout the current branch. ");
            return;
        }
        if (type == 1 && !hasBranch(branch, 1, "")) {
            System.out.println("No such branch exists.");
            return; // 没有这个branch
        }

        if (type == 2 && !hasBranch(temp[1], 2, temp[0])) {
            System.out.println("No such branch exists.");
            return; // 没有这个branch
        }

        String commitID;
        if (type == 1) {
            commitID = getBranchHead(branch, 1, ""); // new branch的HEAD指向的commitid
        } else {
            commitID = getBranchHead(temp[1], 2, temp[0]); // new branch的HEAD指向的commitid
        }

        changeCommit(currentCommit, commitID);

        if (type == 1) {
            writeContents(HEAD_FILE, "refs/heads/" + branch);
        } else {
            writeContents(HEAD_FILE, "refs/remotes/" + branch);
        }
    }

    /**
     * 查看commitID号commit中的fileName文件
     *
     * @param commitID
     * @param fileName
     */
    public void checkout(String commitID, String fileName) {
// 如果使用的是uid（比较短的id）需要找到原始的id
        if (commitID.length() < 40) {
            List<String> fileList = plainFilenamesIn(COMMITS_DIR);
            for (String tmp : fileList) {
                if (tmp.startsWith(commitID)) {
                    commitID = tmp;
                    break;
                }
            }
        }
        File f = join(COMMITS_DIR, commitID);

        if (!f.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }

        Commit commit = Commit.fromFile(COMMITS_DIR, commitID);

        if (!commit.hasFile(fileName)) { // 没有这个文件
            System.out.println("File does not exist in that commit.");
            return;
        }
        writeBlobToFile(fileName, commit.getBlob(fileName));
    }

    /**
     * 获得branch分支的头commit
     *
     * @param branch
     * @return
     */
    public String getBranchHead(String branch, int type, String remote) {
        File file;
        if (type == 1) {
            file = join(BRANCH_HEADS_DIR, branch);
        } else {
            file = join(REMOTE_HEADS_DIR, remote, branch);
        }
        return readContentsAsString(file);
    }

    public boolean hasBranch(String branch, int type, String remote) {
        File file;
        if (type == 1) {
            file = join(BRANCH_HEADS_DIR, branch);
        } else {
            file = join(REMOTE_HEADS_DIR, remote, branch);
        }
        return file.exists();
    }

    /**
     * 从第一个commit的状态切换为第二个commit的状态
     * 修改文件+改变HEAD对应文件的内容
     *
     * @param commitID1
     * @param commitID2
     */
    public void changeCommit(String commitID1, String commitID2) {
        Commit commit1 = Commit.fromFile(COMMITS_DIR, commitID1);
        Commit commit2 = Commit.fromFile(COMMITS_DIR, commitID2);

// 在另一个分支中有这个文件，在这个分支中被修改了但是没有add也没有commit
// 在当前working directory是否有修改（是否和上个commit不同）
        Set<String> files1 = commit1.getFiles();
        Set<String> files2 = commit2.getFiles();
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(files1);
        allFiles.addAll(files2);

        String m = "There is an untracked file in the way;";
        m += " delete it, or add and commit it first.";

        if (hasUntracked(commitID1, commitID2)) {
            System.out.printf(m);
            return;
        }

        for (String fileName : allFiles) {
// 判断是否有untracked files的逻辑有问题
            if (files1.contains(fileName) && !files2.contains(fileName)) {
                File f = join(CWD, fileName);
                if (f.exists()) {
                    f.delete();
                }
//                restrictedDelete(fileName);
            } else {
                writeBlobToFile(fileName, commit2.getBlob(fileName));
            }
        }
        Stage.cleanStage(INDEX_FILE);
    }

    public void changeRemoteCommit(String url, String commitID1, String commitID2) {
        Commit commit1 = Commit.fromRemoteFile(url, commitID1);
        Commit commit2 = Commit.fromRemoteFile(url, commitID2);

// 在另一个分支中有这个文件，在这个分支中被修改了但是没有add也没有commit
// 在当前working directory是否有修改（是否和上个commit不同）
        Set<String> files1 = commit1.getFiles();
        Set<String> files2 = commit2.getFiles();
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(files1);
        allFiles.addAll(files2);

        String m = "There is an untracked file in the way;";
        m += " delete it, or add and commit it first.";

/*
if (hasUntracked(commitID1, commitID2)) {
System.out.printf(m);
return;
}
*/

// 问题在这里！！！！！！！！！！！
/**
 * 哈哈哈哈哈
 */

//
//        System.out.println("开始处理文件啦～～～");

        for (String fileName : allFiles) {
            if (files1.contains(fileName) && !files2.contains(fileName)) {
                File f = join(url, fileName);
                if (f.exists()) {
                    f.delete();
                }
//                restrictedDelete(fileName);
            } else {
                writeBlobToRemoteFile(url, fileName, commit2.getBlob(fileName));
            }
        }
//        System.out.println("文件处理完啦～～～");

        Stage.cleanRemoteStage(url);
    }

    /**
     * 把blob中的内容写到file中去，是安全的，会判断是否有这个文件
     *
     * @param fileName
     * @param blobHash
     */
    public void writeBlobToFile(String fileName, String blobHash) {
        File f = join(CWD, fileName);

        writeContents(f, Blob.getContentFromFile(BLOBS_DIR, blobHash));
    }

    /**
     * “Untracked Files” is for files present in the working directory
     * but neither staged for addition nor tracked.
     * This includes files that have been staged for removal,
     * but then re-created without Gitlet’s knowledge.
     *
     * @return
     */
    public Set<String> getUntrackedFiles() {
        Commit c = Commit.fromFile(COMMITS_DIR, currentCommit);
        List<String> curFiles = plainFilenamesIn(CWD);
        Set<String> files = new TreeSet<>();

        for (String file : curFiles) {
            if (!c.hasFile(file) && !currentStage.hasFile(file)) {
                files.add(file);
            } else if (currentStage.hasRemoved(file)) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * 1:Tracked in the current commit, changed in the working directory, but not staged
     * 2:Staged for addition, but with different contents than in the working directory
     * 3:Staged for addition, but deleted in the working directory; or
     * 4:Not staged for removal, but tracked in the current commit and
     * deleted from the working directory.
     *
     * @return
     */
    public Set<String> getModifiedFiles() {
        Set<String> files = new HashSet<>();
        List<String> curFiles = plainFilenamesIn(CWD);
        Commit c = Commit.fromFile(COMMITS_DIR, currentCommit);
        Set<String> trackedFiles = c.getFiles();

        for (String file : curFiles) {
            if (!currentStage.hasFile(file)) { // case 1:
                if (c.hasFile(file)) {
                    Blob b = Blob.fromFile(BLOBS_DIR, c.getBlob(file));
                    String content1 = readContentsAsString(join(CWD, file));
                    if (!content1.equals(b.getContent())) {
                        files.add(file + " (modified)");
                    }
                }
            } else { // case 2:
                String content1 = readContentsAsString(join(CWD, file));
                Blob b = Blob.fromFile(BLOBS_DIR, currentStage.getBlob(file));
                if (!content1.equals(b.getContent())) {
                    files.add(file + " (modified)");
                }
            }
        }
        Set<String> addFiles = currentStage.getFiles();
        Set<String> removeFiles = currentStage.getRemovedFiles();

        for (String file : addFiles) {
            if (!curFiles.contains(file)) {
                files.add(file + " (deleted)");
            }
        }

        for (String file : trackedFiles) {
            if (!curFiles.contains(file) && !removeFiles.contains(file)) {
                files.add(file + " (deleted)");
            }
        }

        return files;
    }


    public void writeBlobToRemoteFile(String url, String fileName, String blobHash) {
        File f = join(url, fileName);
        writeContents(f, Blob.getContentFromFile(BLOBS_DIR, blobHash));
    }

    private boolean hasUntracked(String cid1, String cid2) {
        Commit c1 = Commit.fromFile(COMMITS_DIR, cid1);
        Commit c2 = Commit.fromFile(COMMITS_DIR, cid2);
        List<String> files = plainFilenamesIn(CWD);

        for (String f : c2.getFiles()) {
            if (!c1.hasFile(f) && files.contains(f)) {
                return true;
            }
        }

        for (String f : c1.getFiles()) {
            if (!c2.hasFile(f) && files.contains(f)) {
                String curFile = sha1(readContents(join(CWD, f)));
                if (!c1.getBlob(f).equals(curFile)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 移除一个branch
     *
     * @param branch
     */
    public void removeBranch(String branch) {
        if (branch.equals(currentBranch)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        if (!hasBranch(branch, 1, "")) {
            System.out.println("A branch with that name does not exist.");
            return; // 没有这个branch
        }
        File file = join(BRANCH_HEADS_DIR, branch);
        file.delete();
    }

    public void reset(String commitID) {
        File file = join(COMMITS_DIR, commitID);

        if (!file.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }
// 如果有untracked等情况会在这个函数中处理
        changeCommit(currentCommit, commitID);

        writeRefs(currentBranch, commitID); // 头指针指向这个位置
    }

    /**
     * 新建一条commit，HEAD指针往后移动到这个commit
     *
     * @param message
     */
    public void commit(String message, String parent2) {
        if (message.equals("")) {
            System.out.println("Please enter a commit message.");
            return;
        }
        if (currentStage.empty() && parent2.equals("")) { // staging area为空
            System.out.println("No changes added to the commit.");
            return;
        }
        Commit commit = new Commit(COMMITS_DIR, message, currentCommit, parent2);
        commit.add(currentStage.getFileToBlob()); // 把stage里面的blob都加进去
        commit.remove(currentStage.getRemovedFiles()); // 把要remove的blob都remove
        String newCommit = commit.getHash();
        commit.saveCommit(COMMITS_DIR);

        writeRefs(currentBranch, newCommit); // 头指针移动到当前commit
        Stage.cleanStage(INDEX_FILE);
    }

    /**
     * 新建一个名为branch的分支
     *
     * @param branch
     */
    public void branch(String branch) { // 新建一个分支
        File file = join(BRANCH_HEADS_DIR, branch);
        if (file.exists()) { // 已经存在这个分支
            System.out.println("A branch with that name already exists.");
            return;
        }
        writeContents(file, currentCommit); //
    }

    /**
     * 对应git add操作
     * 判断这个文件和当前commit中的版本是否相同，如果相同就从staging area把它请出来
     * 否则生成新的blob，然后在stage中加入/修改 fileName:blobHash
     *
     * @param fileName
     */
    public void add(String fileName) {
        File file = join(CWD, fileName);

        if (!file.exists()) { // 如果文件不存在就报错
            System.out.println("File does not exist.");
            return;
        }

        Commit c = Commit.fromFile(COMMITS_DIR, currentCommit);
        String blob = sha1(readContents(file)); // 存放文件内容
        if (c.hasFile(fileName) && c.getBlob(fileName).equals(blob)) {
            if (currentStage.empty()) {
                return;
            }
            currentStage.removeAdd(fileName); // 移走关于这个fileName的add项
            currentStage.removeRm(fileName); // 移走关于这个fileName的remove项
            currentStage.saveStage(INDEX_FILE);
            return;
        }

        String blobHash = sha1(readContents(file)); // 存放文件内容
        Blob b;

        try {
            b = new Blob(blobHash, Files.readAllBytes(Path.of(fileName)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        b.saveBlob(BLOBS_DIR);

// 在stage里面加入当前文件和blob名的对应
        currentStage.add(fileName, blobHash);
        currentStage.saveStage(INDEX_FILE);
    }

    /**
     * 对应git rm -f 操作
     * 1.在staging area中删除这个文件
     * 2.在CWD中删除这个文件
     * 3.在stage文件对应的removedFiles里面加入这个文件(前提是在当前commit被追踪)
     *
     * @param fileName
     */
    public void remove(String fileName) {
// 判断这个文件是否在当前文件中被跟踪
        Commit commit = Commit.fromFile(COMMITS_DIR, currentCommit);

        if (!commit.hasFile(fileName) && !currentStage.hasFile(fileName)) {
// stage中没有加入该文件，在之前的commit中也没有这个文件
            System.out.println("No reason to remove the file.");
            return;
        }
        currentStage.removeAdd(fileName); // 如果添加到了暂存区 从暂存区删掉
// 如果上一个commit中追踪了这个文件，就添加到removed列表
        if (commit.hasFile(fileName)) {
            currentStage.addRm(fileName);
// 如果之前都有这个文件 才需要删掉这个文件并加入removed
            File file = join(CWD, fileName);
            restrictedDelete(file);
        }
        currentStage.saveStage(INDEX_FILE);
    }

    /**
     * 打印status信息
     */
    public void status() {
        showBranches();
        System.out.println();
        showStaged();
        System.out.println();
        showRemoved();
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        showModifiedButNotStaged();
        System.out.println();
        System.out.println("=== Untracked Files ===");
        showUntracked();
        System.out.println();
    }

    /**
     * 帮助status的函数
     */
    public void showBranches() {
        System.out.println("=== Branches ===");

        String[] branches = BRANCH_HEADS_DIR.list();

        Arrays.sort(branches);

        for (String b : branches) {
            if (currentBranch.equals(b)) {
                System.out.println("*" + b);
            } else {
                System.out.println(b);
            }
        }
    }

    /**
     * 帮助status的函数
     */
    public void showStaged() {
        System.out.println("=== Staged Files ===");
        TreeMap<String, String> map = currentStage.getFileToBlob();
        for (String file : map.keySet()) {
            System.out.println(file);
        }
    }

    /**
     * 帮助status的函数
     */
    public void showRemoved() {
        System.out.println("=== Removed Files ===");

        Set<String> removed = currentStage.getRemovedFiles();
        for (String file : removed) {
            System.out.println(file);
        }
    }

    /**
     * “modified but not staged”
     * Tracked in the current commit, changed in the cwd, but not staged;
     * Staged for addition, but with different contents than in cwd;
     * Staged for addition, but deleted in cwd;
     * Not staged for removal, but tracked in the current commit
     * and deleted from the working directory.
     */
    public void showModifiedButNotStaged() {
        Set<String> files = getModifiedFiles();
        for (String file : files) {
            System.out.println(file);
        }
    }

    /**
     * “Untracked Files”
     * files present in the working directory but
     * neither staged for addition nor tracked.
     * This includes files that have been staged for removal,
     * but then re-created without Gitlet’s knowledge.
     * Ignore any subdirectories that may have been introduced,
     * since Gitlet does not deal with them.
     */
    public void showUntracked() {
        Set<String> files = getUntrackedFiles();
        for (String file : files) {
            System.out.println(file);
        }
    }

    /**
     * 找到两个分支commit的最近公共祖先/split point
     *
     * @param commitID1
     * @param commitID2
     * @return
     */
    public String findCommonAncestor(String commitID1, String commitID2) {
        int length1 = commitLength(commitID1);
        int length2 = commitLength(commitID2);

        Commit commit1 = Commit.fromFile(COMMITS_DIR, commitID1);
        Commit commit2 = Commit.fromFile(COMMITS_DIR, commitID2);

// 处理两个parent的情况
        if (!commit1.getParent2().equals("")) {
            return findCommonAncestor(commit1.getParent2(), commitID2);
        }

        String parent1;

// 让length1>=length2,这样commit1永远在commit2前面
        if (length2 > length1) {
            Commit temp = commit1;
            commit1 = commit2;
            commit2 = temp;
        }

        for (int i = 0; i < Math.abs(length1 - length2); i++) {
            parent1 = commit1.getParent();
            commit1 = Commit.fromFile(COMMITS_DIR, parent1);
        }

        while (!commit1.getHash().equals(commit2.getHash())) {
            commit1 = Commit.fromFile(COMMITS_DIR, commit1.getParent());
            commit2 = Commit.fromFile(COMMITS_DIR, commit2.getParent());
        }

        return commit1.getHash();
    }

    /**
     * commit到initial commit的距离（如果加上inital commit是第五个就返回5）
     *
     * @return
     */
    public int commitLength(String commitID) {
        int length = 0;
        Commit commit = Commit.fromFile(COMMITS_DIR, commitID);
        String curCommit = commit.getParent();
        length++;
        while (!curCommit.equals("")) {
            commit = Commit.fromFile(COMMITS_DIR, curCommit);
            curCommit = commit.getParent();
            length++;
        }
        return length;
    }

    /**
     * @param cid1
     * @param cid2
     * @param fileName
     * @return 0表示在两个commit内容一致 1表示在两个commit内容不同
     * 2表示在两个commit都不存在 3表示在commit1存在 在commit2中不存在
     * 4表示在commit2存在 在commit1中不存在
     */
    public int modCode(String cid1, String cid2, String fileName) {
        Commit c1 = Commit.fromFile(COMMITS_DIR, cid1);
        Commit c2 = Commit.fromFile(COMMITS_DIR, cid2);
        boolean h1 = c1.hasFile(fileName);
        boolean h2 = c2.hasFile(fileName);
        if (!h1 && !h2) {
            return 2; // 都不在
        } else if (h1 && !h2) {
            return 3; // 删除
        } else if (!h1 && h2) {
            return 4; // 添加文件
        } else {
            if (c1.getBlob(fileName).equals(c2.getBlob(fileName))) {
                return 0; // 都在且内容一样
            }
            return 1; // 都在但是内容不同
        }
    }

    /**
     * 根据前面的modCode来判断当前分支、合并分支和祖先分支的关系
     *
     * @param sid
     * @param cid
     * @param bid
     * @param f
     * @return 返回的值对应给出的详细功能中的case 1-8
     */
    public int judgeCondition(String sid, String cid, String bid, String f) {
        int code1 = modCode(sid, cid, f);
        int code2 = modCode(sid, bid, f);
        int code3 = modCode(cid, bid, f);

        if (code1 == 0 && code2 == 0) { // 都毫无修改
            return 0;
        } else if (code1 == 0 && code2 == 1 && code3 == 1) { // 只有other修改
            return 1;
        } else if (code1 == 1 && code2 == 0 && code3 == 1) { // 只有当前修改
            return 2;
        } else if (code1 == 1 && code2 == 1 && code3 == 0) { // 都改了但是目前一样
            return 3;
        } else if (code1 == 3 && code2 == 3) {
            return 3;
        } else if (code1 == 4 && code2 == 4 && code3 == 0) {
            return 3;
        } else if (code1 == 4 && code2 == 2) {
            return 4;
        } else if (code1 == 2 && code2 == 4 && code3 == 4) {
            return 5;
        } else if (code1 == 0 && code2 == 3) {
            return 6;
        } else if (code1 == 3 && code2 == 0) {
            return 7;
        } else if (code1 == 1 && code2 == 1 && code3 == 1) {
            return 8;
        } else if (code1 == 1 && code2 == 3) {
            return 8;
        } else if (code1 == 3 && code2 == 1) {
            return 8;
        } else if (code1 == 4 && code2 == 4 && code3 == 1) {
            return 8;
        }
        return -1;
    }

    public void merge(String branch, int type) {
        String remote = "";

        if (type == 2) {
            String[] temp = branch.split("/");
            branch = temp[1];
            remote = temp[0];
//            System.out.println("remote:" + remote);
//            System.out.println("branch" + branch);
        }

        if (!currentStage.empty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }
        if (!hasBranch(branch, type, remote)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (type != 2 && branch.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself. ");
            return;
        }

        String branchCommit = getBranchHead(branch, type, remote);
        String splitPoint = findCommonAncestor(currentCommit, branchCommit);

        if (splitPoint.equals(currentCommit)) { // 分支点=等于当前分支 切换到给定分支
            checkout(branch); // 切换到给定分支
            System.out.println("Current branch fast-forwarded.");
            return;
        }

// 判断是否有untrackedFiles会被覆盖
        if (hasUntracked(currentCommit, branchCommit)) {
            String m = "There is an untracked file in the way;";
            m += " delete it, or add and commit it first.";
            System.out.println(m);
            return;
        }

        Set<String> allFiles = getAllFiles(currentCommit, branchCommit, splitPoint);
// 分割点是可以不存在的！！！

// 复用add,rm,commit命令

        boolean conflict = false;
        for (String f : allFiles) { // 根据八个规律进行判断
            int code = judgeCondition(splitPoint, currentCommit, branchCommit, f);
            switch (code) {
                case 1: // 用合并的文件，add
                case 5: // 新建合并文件，add
                    writeCommitToFile(branchCommit, f);
                    add(f);
                    break;
                case 6: // 删除文件，remove
                    remove(f);
                    break;
                case 8: // 写冲突文件，add
                    conflict = true;
                    writeConflictFile(currentCommit, branchCommit, f);
                    add(f);
                    break;
                default:
            }
        }
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
        String msg = "Merged " + branch + " into " + currentBranch + ".";
        commit(msg, branchCommit);

        if (splitPoint.equals(branchCommit)) { // 分支点=给定分支 不做任何处理
            System.out.println("Given branch is an ancestor of the current branch.");
        }
    }

    private Set<String> getAllFiles(String cid, String bid, String sid) {
        Commit bCommit = Commit.fromFile(COMMITS_DIR, bid);
        Commit cCommit = Commit.fromFile(COMMITS_DIR, cid);
        Commit sCommit = Commit.fromFile(COMMITS_DIR, sid);
        Set<String> bFiles = bCommit.getFiles();
        Set<String> cFiles = cCommit.getFiles();
        Set<String> sFiles = sCommit.getFiles();
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(bFiles);
        allFiles.addAll(cFiles);
        allFiles.addAll(sFiles);
        return allFiles;
    }

    private void writeConflictFile(String cid1, String cid2, String fileName) {
        Commit c1 = Commit.fromFile(COMMITS_DIR, cid1);
        Commit c2 = Commit.fromFile(COMMITS_DIR, cid2);
        String blob1 = "";
        String blob2 = "";
        if (c1.hasFile(fileName)) {
            blob1 = c1.getBlob(fileName);
        }
        if (c2.hasFile(fileName)) {
            blob2 = c2.getBlob(fileName);
        }

        String res = "<<<<<<< HEAD\n";
        if (!blob1.equals("")) {
            res += Blob.fromFile(BLOBS_DIR, blob1).getContent();
        }
        res += "=======\n";


        if (!blob2.equals("")) {
            res += Blob.fromFile(BLOBS_DIR, blob2).getContent();
        }
        res += ">>>>>>>\n";

        File f = join(CWD, fileName);
        writeContents(f, res);
    }

    private void writeCommitToFile(String cid, String fileName) {
        Commit c = Commit.fromFile(COMMITS_DIR, cid);
        writeBlobToFile(fileName, c.getBlob(fileName));
    }

}
