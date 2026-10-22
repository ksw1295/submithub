//@rename Tree,key,left,right,root,insert,inorder,height,contains,node,keys,depth,result,build
public class Main {
    static class Tree {
        int key;
        Tree left, right;

        Tree(int key) {
            this.key = key;
        }
    }

    //@method-start
    // 이진 탐색 트리에 키를 삽입한다
    static Tree insert(Tree node, int key) {
        if (node == null) {
            return new Tree(key);
        }
        if (key < node.key) {
            node.left = insert(node.left, key);
        } else if (key > node.key) {
            node.right = insert(node.right, key);
        }
        return node;
    }
    //@method-end

    //@method-start
    static boolean contains(Tree node, int key) {
        while (node != null) {
            if (key == node.key) {
                return true;
            }
            node = key < node.key ? node.left : node.right;
        }
        return false;
    }
    //@method-end

    //@method-start
    // 중위 순회: 키가 오름차순으로 출력된다
    static void inorder(Tree node, StringBuilder result) {
        if (node == null) {
            return;
        }
        inorder(node.left, result);
        result.append(node.key).append(' ');
        inorder(node.right, result);
    }
    //@method-end

    //@method-start
    static int height(Tree node) {
        if (node == null) {
            return 0;
        }
        int depth = Math.max(height(node.left), height(node.right));
        //@insert
        return depth + 1;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] keys = {50, 30, 70, 20, 40, 60, 80, 35};
        Tree root = null;
        StringBuilder result = new StringBuilder();
        //@swap-end
        for (int i = 0; i < keys.length; i++) {
            root = insert(root, keys[i]);
        }
        inorder(root, result);
        //@insert
        System.out.println("inorder: " + result);
        System.out.println("height = " + height(root) + ", has 60 = " + contains(root, 60));
    }
}
