package gitlet;


public class Test {
    public static void main(String[] args) {
        testRemoteFetchPush();
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
        repo.log();
    }
}
