package gitlet;


public class Test {
    public static void main(String[] args) {
        testRemoteFetchPull();
    }

    @org.junit.Test
    public static void testRemoteFetchPull() {
        Repository repo = new Repository();
        // 在D1中
//        repo.init();

//        repo.add("f.txt");
//        repo.add("g.txt");

//        repo.commit("Two files", "");
//        repo.log();
        // 记录一些two files的hash值
//        // 在D2中
//        repo.init();
//
//        repo.remoteAdd("R1", "../D1/.gitlet");
//
//        repo.fetch("R1", "master");
//
        // 切换回D2
//
//        repo.reset("f64b44492a2a6c1e434b8c44548f05029faa1ca0"); // two files
//
//        repo.add("h.txt");
//
//        repo.commit("Add h.txt","");
//
//        // 回到D1
//        repo.add("k.txt");
//        repo.commit("Add k.txt","");

//        repo.log();
        // 最后回到D2
//        repo.pull("R1","master");
        repo.log();
    }

    @org.junit.Test
    public static void testRemoteFetchPush() {
        Repository repo = new Repository();
        // 在D1中
//        repo.init();

//        repo.add("f.txt");
//        repo.add("g.txt");
//
//        repo.commit("Two files", "");
//        // 在D2中
//        repo.init();
//
//        repo.add("k.txt");
//
//        repo.commit("Add k.txt", "");
//
//        repo.remoteAdd("R1", "../D1/.gitlet");
//
//        repo.fetch("R1", "master");
//
//        repo.checkout("R1/master");
//
//        repo.log();
//
//        repo.checkout("master");
//
//        repo.reset("bcf8e7273523370d9b159a8d527776921420947d"); // two files
//
//        repo.add("h.txt");
//
//        repo.commit("Add h.txt","");
//
//        repo.push("R1", "master"); // 出错
//
//        // 回到D1
        repo.pull("R1", "master");
        repo.log();
        repo.status();
    }
}
