//@rename Node,value,next,head,size,addLast,addFirst,removeValue,printList,cur,prev,node,list,total,sumList
public class Main {
    static class Node {
        int value;
        Node next;

        Node(int value) {
            this.value = value;
        }
    }

    //@swap-start
    static Node head = null;
    static int size = 0;
    //@swap-end

    //@method-start
    // 리스트 끝에 값을 추가한다
    static void addLast(int value) {
        Node node = new Node(value);
        if (head == null) {
            head = node;
        } else {
            Node cur = head;
            while (cur.next != null) {
                cur = cur.next;
            }
            cur.next = node;
        }
        size++;
    }
    //@method-end

    //@method-start
    static void addFirst(int value) {
        Node node = new Node(value);
        node.next = head;
        head = node;
        size++;
    }
    //@method-end

    //@method-start
    // 값이 같은 첫 노드를 삭제한다
    static boolean removeValue(int value) {
        Node prev = null;
        Node cur = head;
        //@insert
        while (cur != null) {
            if (cur.value == value) {
                if (prev == null) {
                    head = cur.next;
                } else {
                    prev.next = cur.next;
                }
                size--;
                return true;
            }
            prev = cur;
            cur = cur.next;
        }
        return false;
    }
    //@method-end

    //@method-start
    static int sumList() {
        int total = 0;
        for (Node cur = head; cur != null; cur = cur.next) {
            total += cur.value;
        }
        return total;
    }
    //@method-end

    //@method-start
    static void printList() {
        StringBuilder list = new StringBuilder();
        for (Node cur = head; cur != null; cur = cur.next) {
            list.append(cur.value).append(" -> ");
        }
        System.out.println(list + "null (size " + size + ")");
    }
    //@method-end

    public static void main(String[] args) {
        addLast(10);
        addLast(20);
        addFirst(5);
        printList();
        removeValue(20);
        //@insert
        printList();
        System.out.println("sum = " + sumList());
    }
}
