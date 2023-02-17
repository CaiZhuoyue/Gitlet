package gitlet;

/**
 * Driver class for Gitlet, a subset of the Git version-control system.
 *
 * @author Caizhuoyue
 */
public class Main {
    public static void main(String[] args) {
        Repository repo = new Repository();

        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }

        String firstArg = args[0];

        if (!repo.check(args[0])) { // 不过关
            System.out.println("Not in an initialized Gitlet directory.");
            return;
        }

        switch (firstArg) {
            case "init":
                repo.init();
                break;
            case "log":
                repo.log();
                break;
            case "add":
                repo.add(args[1]);
                break;
            case "rm":
                repo.remove(args[1]);
                break;
            case "commit":
                if (args.length >= 2) {
                    repo.commit(args[1], "");
                } else {
                    repo.commit("", "");
                }
                break;
            case "status":
                repo.status();
                break;
            case "checkout":
                repo.checkout(args);
                break;
            case "branch":
                repo.branch(args[1]);
                break;
            case "rm-branch":
                repo.removeBranch(args[1]);
                break;
            case "reset":
                repo.reset(args[1]);
                break;
            case "find":
                repo.find(args[1]);
                break;
            case "global-log":
                repo.globalLog();
                break;
            case "common":
                repo.findCommonAncestor(args[1], args[2]);
                break;
            case "merge":
                repo.merge2(args[1]);
                break;
            case "add-remote":
                repo.remoteAdd(args[1], args[2]);
                break;
            case "remove-remote":
                repo.remoteRemove(args[1]);
                break;
            case "fetch":
                repo.fetch(args[1], args[2]);
                break;
            case "push":
                repo.push(args[1], args[2]);
                break;
            default:
                System.out.println("No command with that name exists.");
                System.exit(0);
        }
    }
}