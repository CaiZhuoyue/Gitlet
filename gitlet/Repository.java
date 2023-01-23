package gitlet;

//import com.puppycrawl.tools.checkstyle.utils.ScopeUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static gitlet.Utils.*;

/**
 * Represents a gitlet repository.
 * 每次想使用任何命令都通过repository来调用
 * does at a high level.
 *
 * @author caizhuoyue
 */
public class Repository implements Serializable {
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    public static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File REFS_HEADS_DIR = join(REFS_DIR, "heads");
    public static final File INDEX_FILE = join(GITLET_DIR, "index");
    String currentCommit; // 当前commit的对应SHA1
    String currentBranchPath; // "refs/heads/master"
    String currentBranch; // "master"
    Stage currentStage;

    public Repository() {
        if (GITLET_DIR.exists()) {
            currentBranchPath = Utils.readContentsAsString(HEAD_FILE);
            currentBranch = currentBranchPath.split("/")[2];
            File f = join(GITLET_DIR, currentBranchPath);
            currentCommit = readContentsAsString(f);
            currentStage = Stage.fromFile();
        }
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
        if (!REFS_HEADS_DIR.exists()) {
            REFS_HEADS_DIR.mkdir();
        }
        if (!COMMITS_DIR.exists()) {
            COMMITS_DIR.mkdir();
        }
        if (!BLOBS_DIR.exists()) {
            BLOBS_DIR.mkdir();
        }
        // 生成初始的commit
        Commit commit = new Commit("initial commit", "");
        // HEAD文件里面存放这个初始commit的文件位置
        Utils.writeContents(HEAD_FILE, "refs/heads/master");
        // refs/heads/master文件存放这个commit的SHA1
        writeRefs("master", commit.getHash());
        commit.saveCommit(); // 保存当前commit
        Stage.cleanStage();
    }

    /**
     * 输出当前commit和所有的parent commit
     */
    public void log() {
        Commit commit = Commit.fromFile(currentCommit);
        System.out.println(commit);
        String curCommit = commit.getParent();
        while (!curCommit.equals("")) {
            commit = Commit.fromFile(curCommit);
            System.out.println(commit);
            curCommit = commit.getParent();
        }
    }

    /**
     * 按照字典序输出所有的commit信息
     */
    public void globalLog() {
// 遍历所有的commit
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
            checkout(getBranchHead(currentBranch), fileName);
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
     * 移除一个branch
     *
     * @param branch
     */
    public void removeBranch(String branch) {
        if (branch.equals(currentBranch)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        if (!hasBranch(branch)) {
            System.out.println("A branch with that name does not exist.");
            return; // 没有这个branch
        }
        File file = join(REFS_HEADS_DIR, branch);
        file.delete();
    }

    public boolean hasBranch(String branch) {
        File file = join(REFS_HEADS_DIR, branch);
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
        Commit commit1 = Commit.fromFile(commitID1);
        Commit commit2 = Commit.fromFile(commitID2);

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
        Stage.cleanStage();
    }

    /**
     * checkout一个分支
     *
     * @param branch
     */
    public void checkout(String branch) {
        if (branch.equals(currentBranch)) {
            System.out.println("No need to checkout the current branch. ");
            return;
        }
        if (!hasBranch(branch)) {
            System.out.println("No such branch exists.");
            return; // 没有这个branch
        }

        String commitID = getBranchHead(branch); // new branch的HEAD指向的commitid

        changeCommit(currentCommit, commitID);

        writeContents(HEAD_FILE, "refs/heads/" + branch);
    }

    /**
     * 把blob中的内容写到file中去，是安全的，会判断是否有这个文件
     *
     * @param fileName
     * @param blobHash
     */
    public void writeBlobToFile(String fileName, String blobHash) {
        File f = join(CWD, fileName);

        writeContents(f, Blob.getContentFromFile(blobHash));
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

    public void writeRefs(String branch, String commitID) {
        writeContents(join(REFS_HEADS_DIR, branch), commitID);
        return;
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

        Commit commit = Commit.fromFile(commitID);

        if (!commit.hasFile(fileName)) { // 没有这个文件
            System.out.println("File does not exist in that commit.");
            return;
        }
        writeBlobToFile(fileName, commit.getBlob(fileName));
    }

    /**
     * 新建一条commit，HEAD指针往后移动到这个commit
     *
     * @param message
     */
    public void commit(String message) {
        if (message.equals("")) {
            System.out.println("Please enter a commit message.");
            return;
        }
        if (currentStage.empty()) { // staging area为空
            System.out.println("No changes added to the commit.");
            return;
        }
        Commit commit = new Commit(message, currentCommit);
        commit.add(currentStage.getFileToBlob()); // 把stage里面的blob都加进去
        commit.remove(currentStage.getRemovedFileToBlob());
        String newCommit = commit.getHash();
        commit.saveCommit();

        writeRefs(currentBranch, newCommit);
        Stage.cleanStage();
    }

    public void commit(String message, String parent2) {
        Commit commit = new Commit(message, currentCommit, parent2);
        commit.add(currentStage.getFileToBlob()); // 把stage里面的blob都加进去
        commit.remove(currentStage.getRemovedFileToBlob());
        String newCommit = commit.getHash();
        commit.saveCommit();

        writeRefs(currentBranch, newCommit);
        Stage.cleanStage();
    }

    /**
     * 新建一个名为branch的分支
     *
     * @param branch
     */
    public void branch(String branch) { // 新建一个分支
        File file = join(REFS_HEADS_DIR, branch);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.exit(0);
            }
        } else { // 已经存在这个分支
            System.out.println("A branch with that name already exists.");
            return;
        }
        writeContents(file, currentCommit);
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

        Commit c = Commit.fromFile(currentCommit);
        String blob = sha1(readContents(file)); // 存放文件内容
        if (c.hasFile(fileName) && c.getBlob(fileName).equals(blob)) {
            if (currentStage.empty()) {
                return;
            }
            currentStage.removeAdd(fileName); // 移走关于这个fileName的add项
            currentStage.removeRm(fileName); // 移走关于这个fileName的remove项
            currentStage.saveStage();
            return;
        }

        String blobHash = sha1(readContents(file)); // 存放文件内容
        Blob b;

        try {
            b = new Blob(fileName, blobHash, Files.readAllBytes(Path.of(fileName)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        b.saveBlob();

        // 在stage里面加入当前文件和blob名的对应
        currentStage.add(fileName, blobHash);
        currentStage.saveStage();
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
        Commit commit = Commit.fromFile(currentCommit);

        if (!commit.hasFile(fileName) && !currentStage.hasFile(fileName)) {
// stage中没有加入该文件，在之前的commit中也没有这个文件
            System.out.println("No reason to remove the file.");
            return;
        }

        currentStage.removeAdd(fileName); // 如果添加到了暂存区 从暂存区删掉

// 如果上一个commit中追踪了这个文件，就添加到removed列表
        if (commit.hasFile(fileName)) {
            currentStage.addRm(fileName, "lalalla");
// 如果之前都有这个文件 才需要删掉这个文件并加入removed
            File file = join(CWD, fileName);
            restrictedDelete(file);
        }

        currentStage.saveStage();
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
        System.out.println();
        System.out.println("=== Untracked Files ===");
        System.out.println();
    }

    /**
     * 帮助status的函数
     */
    public void showBranches() {
        System.out.println("=== Branches ===");

        String[] branches = REFS_HEADS_DIR.list();

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

        TreeMap<String, String> map = currentStage.getRemovedFileToBlob();
        for (String file : map.keySet()) {
            System.out.println(file);
        }
    }

    public boolean check() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
        }
        return GITLET_DIR.exists();
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

        Commit commit1 = Commit.fromFile(commitID1);
        Commit commit2 = Commit.fromFile(commitID2);

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
            commit1 = Commit.fromFile(parent1);
        }

        while (!commit1.getHash().equals(commit2.getHash())) {
            commit1 = Commit.fromFile(commit1.getParent());
            commit2 = Commit.fromFile(commit2.getParent());
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
        Commit commit = Commit.fromFile(commitID);
        String curCommit = commit.getParent();
        length++;
        while (!curCommit.equals("")) {
            commit = Commit.fromFile(curCommit);
            curCommit = commit.getParent();
            length++;
        }
        return length;
    }

    /**
     * 获得branch分支的头commit
     *
     * @param branch
     * @return
     */
    public String getBranchHead(String branch) {
        File file = join(REFS_HEADS_DIR, branch);
        return readContentsAsString(file);
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
        Commit c1 = Commit.fromFile(cid1);
        Commit c2 = Commit.fromFile(cid2);
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

    public void merge2(String branch) {
        if (!currentStage.empty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }
        if (!hasBranch(branch)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (branch.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself. ");
            return;
        }
        String branchCommit = getBranchHead(branch);
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
        Commit bCommit = Commit.fromFile(bid);
        Commit cCommit = Commit.fromFile(cid);
        Commit sCommit = Commit.fromFile(sid);
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
        Commit c1 = Commit.fromFile(cid1);
        Commit c2 = Commit.fromFile(cid2);
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
            res += Blob.fromFile(blob1).getContent();
        }
        res += "=======\n";


        if (!blob2.equals("")) {
            res += Blob.fromFile(blob2).getContent();
        }
        res += ">>>>>>>\n";

        File f = join(CWD, fileName);
        writeContents(f, res);
    }

    private void writeCommitToFile(String cid, String fileName) {
        Commit c = Commit.fromFile(cid);
        writeBlobToFile(fileName, c.getBlob(fileName));
    }

    private boolean hasUntracked(String cid1, String cid2) {
        Commit c1 = Commit.fromFile(cid1);
        Commit c2 = Commit.fromFile(cid2);
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

}
