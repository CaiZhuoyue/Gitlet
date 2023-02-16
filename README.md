# Gitlet Design Document

**Author:caizhuoyue**

一些要点

* final关键词表示赋值一次之后就不能改变，对于一些需要改动的map就不能用final关键词
* 不需要给外界使用的函数就可以设置为private

#### 使用方式：

首先`cd gitlet`,`javac *.java`,然后`cd ..`回到proj2的文件夹

**初始化gitlet仓库：**`java gitlet.Main init`

**add操作：**`java gitlet.Main add xxx`

**remove操作：**`java gitlet.Main remove xxx`

**commit操作：**`java gitlet.Main commit "你的message"`

**log操作：**`java gitlet.Main log`

**status操作：**`java gitlet.Main status`

**branch操作：**`java gitlet.Main branch "new branch的名称"`

**check操作：**

1. `java gitlet.Main checkout -- [file name]`
2. `java gitlet.Main checkout [commit id] -- [file name]`
3. `java gitlet.Main checkout [branch name]`



### Data

* .gitlet文件夹:存放所有的gitlet有关文件
    * HEAD文件:类似refs/heads/main的字符串
    * index文件
        * 存放stage有关信息
        * 如果当前的staging area为空就没有index文件
    * objects文件夹
        * blobs 存放各种blob
        * commits 存放各种commit
        * 文件名是SHA1 code
    * refs
        * heads（存放每个branch对应的头指针位置）
            * master文件:master的头对应的commit
            * ..其他branch文件
    * ~~staging 暂存区的文件~~
        * ~~add:存放各种进行修改之类的文件~~
        * ~~remove~~

### Classes

#### Repository

使用场景：在main函数当中初始化的时候会用到。

* Instance variables:

  * String currentBranchPath 表示目前在哪个分支，例:"master"

  * String currentCommit 表示当前commit的id


* Methods:
  * consturctor
  * init()
  * add(文件名)
  * remove(文件名)
  * commit(msg)
  * commit(msg和第二个parent)
  * checkout的入口
  * checkout branch
  * checkout commitId -- fileName
  * changeCommit(cid1, cid2) 被几个函数共用的核心逻辑
  * branch(branchname) 生成一个新的branch
  * removeBranch(branchname) 删除对应的branch文件
  * hasBranch 
  * getBranchHeads 根据对应的branch名字获得commitid
  * find
  * global log
  * status 对应status
  * showBranches,showRemoved,showStaged
  * writeRefs() 把commitid写到branch对应的文件中 比如把"dihuwe838923"写到"refs/head/master"中
  * writeBlobToFile 把某个blob对应的文件写到当前目录的fileName文件中
  * hasUntracked 找到这个状态下的文件
  * merge
  * findCommonAncestor （这里逻辑还有问题，要按照图的方式）
  * modeCode
  * judgeCondition

#### Blob

使用场景：在add文件的时候会用到，每次add都会生成一个新的blob文件存放在暂存区中。

* instance variables:

  * String id (暂时没用)

  * bytes[] content 文件内容

  * String blobHash 文件名（哈希码）

    


#### Stage

使用场景：在添加文件的时候会修改stage，对文件名-blob名的哈希表进行修改。

在commit之后应该就用不到之前的stage了，就删除stage对应的index文件。

add添加一项或者更新一项到stage的map中，rm删除一项，同时移除文件。

* instance variables:

  * TreeMap<String,String> fileToBlob add添加的文件名到blob名的映射

  * TreeMap<String,String> removefileToBlob rm添加的文件名到blob的映射 其实可以只变成Set\<String>




#### Commit

使用场景：在创建commit的时候会生成commit，需要可以记录commit文件。

* instance variables:

  * private final String message; // 提交的信息

  * private final String timestamp; // 时间戳

  * private final String parent;

  * private final String parent2; 

  * private final String commitHash;

  * private final TreeMap<String, String> fileToBlob; // 映射表

    

### 实现各种功能

#### git init

* 判断是否有.gitlet文件夹，存在就报错返回
* 创建一堆对应的文件夹
* 添加一个commit:"initial commit"
* 把头指针和master指针都指向对应位置

#### git add 文件名

* 判断这个文件是否存在，不存在就报错返回
* 判断这个文件在当前commit中是否有一模一样的，如果有一样的就不添加到staging
* _TODO:不知道怎么判断_
* 文件生成一个blob，放到staging/add文件夹中
* 更新stage，把更新之后的文件名和blob名的对应加进去

### git rm 文件名

* 类似git中的git rm -f(强制删除文件)
* 如果在当前commit被追踪，且在当前staging area中被修改，就删除stage add的记录，然后加入到stage remove中
* 如果在当前commit中没有被追踪，就直接删除文件，不需要加入removed中

### git commit "message"

* 添加一个commit信息
* 如果staging中没有东西，就说没有什么好commit的
* 对staging/add的文件，都移动到objects文件夹去，清空add文件夹
* 然后生成一个commit的消息，commit消息也都保存到objects文件夹

### git branch

* 在refs/heads里新建一个文件，内容是当前头指针的commit id

### git checkout branch

* 判断这个分支是否存在
* 如果存在就把currentBranchPath, currentCommit这些都切换为新的branch
* 把新的branch指向的头commit的文件复制过来



### Extra credit

实现git的几个remote操作

To get extra credit, implement some basic remote commands: namely `add-remote`, `rm-remote`, `push`, `fetch`, and `pull` You will get 3 extra-credit points for completing them. Don't attempt or plan for extra credit until you have completed the rest of the project.



A few notes about the remote commands:

- Execution time will not be graded. For your own edification, please don’t do anything ridiculous, though.
- All the commands are significantly simplified from their git equivalents, so specific differences from git are usually not notated. Be aware they are there, however.

So now let’s go over the commands:

#### add-remote

- **Usage**: `java gitlet.Main add-remote [remote name] [name of remote directory]/.gitlet`
- **Description**: Saves the given login information under the given remote name. Attempts to push or pull from the given remote name will then attempt to use this `.gitlet` directory. By writing, e.g., java gitlet.Main add-remote other ../testing/otherdir/.gitlet you can provide tests of remotes that will work from all locations (on your home machine or within the grading program’s software). Always use forward slashes in these commands. Have your program convert all the forward slashes into the path separator character (forward slash on Unix and backslash on Windows). Java helpfully defines the class variable `java.io.File.separator` as this character.
- **Failure cases**: If a remote with the given name already exists, print the error message: `A remote with that name already exists.` You don’t have to check if the user name and server information are legit.
- **Dangerous?**: No.

#### rm-remote

- **Usage**: `java gitlet.Main rm-remote [remote name]`
- **Description**: Remove information associated with the given remote name. The idea here is that if you ever wanted to change a remote that you added, you would have to first remove it and then re-add it.
- **Failure cases**: If a remote with the given name does not exist, print the error message: `A remote with that name does not exist.`
- **Dangerous?**: No.

#### push

- **Usage**: `java gitlet.Main push [remote name] [remote branch name]`

- **Description**: Attempts to append the current branch’s commits to the end of the given branch at the given remote. Details:

  This command only works if the remote branch’s head is in the history of the current local head, which means that the local branch contains some commits in the future of the remote branch. In this case, append the future commits to the remote branch. Then, the remote should reset to the front of the appended commits (so its head will be the same as the local head). This is called fast-forwarding.

  If the Gitlet system on the remote machine exists but does not have the input branch, then simply add the branch to the remote Gitlet.

- **Failure cases**: If the remote branch’s head is not in the history of the current local head, print the error message `Please pull down remote changes before pushing.` If the remote `.gitlet` directory does not exist, print `Remote directory not found.`

- **Dangerous?**: No.

#### fetch

- **Usage**: `java gitlet.Main fetch [remote name] [remote branch name]`
- **Description**: Brings down commits from the remote Gitlet repository into the local Gitlet repository. Basically, this copies all commits and blobs from the given branch in the remote repository (that are not already in the current repository) into a branch named `[remote name]/[remote branch name]` in the local `.gitlet` (just as in real Git), changing `[remote name]/[remote branch name]` to point to the head commit (thus copying the contents of the branch from the remote repository to the current one). This branch is created in the local repository if it did not previously exist.
- **Failure cases**: If the remote Gitlet repository does not have the given branch name, print the error message `That remote does not have that branch.` If the remote `.gitlet` directory does not exist, print `Remote directory not found.`
- **Dangerous?** No

#### pull

- **Usage**: `java gitlet.Main pull [remote name] [remote branch name]`
- **Description**: Fetches branch `[remote name]/[remote branch name]` as for the `fetch` command, and then merges that fetch into the current branch.
- **Failure cases**: Just the failure cases of `fetch` and `merge` together.
- **Dangerous?** Yes!



### git merge

什么时候有conflict？

* the contents of both are changed and different from other
* the contents of one are changed and the other file is deleted
* the file was absent at the split point and has different contents in the given and current branches.

and stage the result.

* A deleted file in a branch as an empty file.
* Use straight concatenation here.



|            |           | 祖先分支 | 当前分支        | 合并分支 | -表示啥都不做         |      |
| ---------- | :-------- | -------- | --------------- | -------- | --------------------- | ---- |
| case 0     | 0 0 0     | +        | +               | +        | -                     | 成功 |
| ~~case 2~~ | 1 0 1     | +        | +改了           | +        | -                     | 成功 |
| ~~case 1~~ | **0 1 1** | +        | +               | +改了    | 用合并分支的文件，add | 成功 |
| case 8     | 1 1 1     | +        | + 2分支修改不同 | +改了    | 写新的冲突文件，add   | 成功 |
| ~~case 3~~ | 1 1 0     | +        | + 2分支修改相同 | +改了    | -                     | 成功 |
| ~~case 6~~ | 0 3 3     | +        | +               | -删除    | 删掉文件（？）remove  | 成功 |
| case 8     | 1 3 3     | +        | +改了           | -删除    | 写新的冲突文件，add   | 成功 |
| case 7     | 3 0 4     | +        | -删除           | +        | 不管                  | 成功 |
| case 8     | 3 1 4     | +        | -删除           | +改了    | 写新的冲突文件，add   |      |
| ~~case 3~~ | 4 4 0     | -        | +               | +        | -                     | 成功 |
| case 8     | 4 4 1     | -        | + 内容不一样    | +        | 写新的冲突文件，add   | 成功 |
| ~~case 3~~ | 3 3 2     | +        | -               | -        | -                     |      |
| ~~case 4~~ | 4 2 3     | -        | +添加           | -        | -                     | 成功 |
| ~~case 5~~ | **2 4 4** | -        | -               | +添加    | 用合并分支的文件，add | 成功 |

有时候会显示no changes added to the commit...





第二列：当前和祖先 合并和祖先 当前和合并

|        |           | 祖先分支 | 当前分支        | 合并分支 | -表示啥都不做         |
| ------ | :-------- | -------- | --------------- | -------- | --------------------- |
| 1      | 0 0 1     | +        | +               | +        | -                     |
| case 2 | 1 0 1     | +        | +改了           | +        | -                     |
| case 1 | **0 1 1** | +        | +               | +改了    | 用合并分支的文件 add  |
| case 8 | 1 1 1     | +        | + 2分支修改不同 | +改了    | 写新的冲突文件，add   |
| case 3 | 1 1 0     | +        | + 2分支修改相同 | +改了    | -                     |
| case 6 | 0 3 3     | +        | +               | -删除    | 删掉文件（？）remove  |
| case 8 | 1 3 3     | +        | +改了           | -删除    | 写新的冲突文件，add   |
| case 7 | 3 0 4     | +        | -删除           | +        | 不管                  |
| case 8 | 3 1 4     | +        | -删除           | +改了    | 写新的冲突文件，add   |
| case 3 | 4 4 0     | -        | +               | +        | -                     |
| case 8 | 4 4 1     | -        | + 内容不一样    | +        | 写新的冲突文件，add   |
| case 3 | 3 3 2     | +        | -               | -        | -                     |
| case 4 | 4 2 3     | -        | +               | -        | -                     |
| case 5 | **2 4 4** | -        | -               | +        | 用合并分支的文件，add |







merge的8个规则

![img](https://pic2.zhimg.com/80/v2-5b2470a6def367603e617ea9c709c231_1440w.webp)

**1.modified in `other` but not `HEAD`,用`other`的文件,需要stage for add**

checkout新branch的东西

These files should then all be automatically staged. 

* “modified in the given branch since the split point”

  this means the version of the file as it exists in the commit at the front of the given branch has different content from the version of the file at the split point. Remember: blobs are content addressable!

**2.modified in `HEAD` but not `other`->`HEAD`,保持原样**

**3.在`HEAD`和`other`中都修改了，且修改一致（可以同时添加，修改为同一份文件或者删除）**

<u>Untracked的情况：</u>

If a file was removed from both the current and given branch, but a file of the same name is present in the working directory, it is left alone and continues to be absent (not tracked nor staged) in the merge.

**8.在`HEAD`和`other`都修改了，但是修改方式不同**

可能修改成不同的内容，或者一个改一个删...

Any files modified in different ways in the current and given branches are *in conflict*. 

“Modified in different ways” can mean that the contents of both are changed and different from other, or the contents of one are changed and the other file is deleted, or the file was absent at the split point and has different contents in the given and current branches. In this case, replace the contents of the conflicted file with

**4.文件只在`HEAD`中有**，不用管

**5.文件只在`other`中有**，添加`other`的文件并add for stage

**6.在`HEAD`中没有修改，在`other`中被删除了**，remove这个文件（同时untract）

**7.在`other`中没有修改，在`HEAD`中被删除了，**不用改，remain absent