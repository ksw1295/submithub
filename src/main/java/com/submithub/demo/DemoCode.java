package com.submithub.demo;

import java.util.List;

/** 시연용 학생 코드 샘플. 베낀 쌍(원본/변형)과 서로 다른 구현, 피드백 반영 시나리오용 버전들. */
public final class DemoCode {

    private DemoCode() {
    }

    /** 교수가 나눠준 뼈대 코드 (표절 검사에서 제외됨) */
    public static final String BASELINE = """
            public class LinkedList {
                private static class Node {
                    int value;
                    Node next;
                    Node(int value) { this.value = value; }
                }

                private Node head;
                private int size;

                // TODO: add, get, remove, size 를 구현하세요
            }
            """;

    /** 박서연의 풀이 (원본) */
    public static final String ORIGINAL = """
            public class LinkedList {
                private static class Node {
                    int value;
                    Node next;
                    Node(int value) { this.value = value; }
                }

                private Node head;
                private int size;

                public void add(int value) {
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

                public void addFirst(int value) {
                    Node node = new Node(value);
                    node.next = head;
                    head = node;
                    size++;
                }

                public int get(int index) {
                    if (index < 0 || index >= size) {
                        throw new IndexOutOfBoundsException("index: " + index);
                    }
                    Node cur = head;
                    for (int i = 0; i < index; i++) {
                        cur = cur.next;
                    }
                    return cur.value;
                }

                public boolean remove(int value) {
                    if (head == null) {
                        return false;
                    }
                    if (head.value == value) {
                        head = head.next;
                        size--;
                        return true;
                    }
                    Node cur = head;
                    while (cur.next != null && cur.next.value != value) {
                        cur = cur.next;
                    }
                    if (cur.next == null) {
                        return false;
                    }
                    cur.next = cur.next.next;
                    size--;
                    return true;
                }

                public void reverse() {
                    Node prev = null;
                    Node cur = head;
                    while (cur != null) {
                        Node next = cur.next;
                        cur.next = prev;
                        prev = cur;
                        cur = next;
                    }
                    head = prev;
                }

                public int size() {
                    return size;
                }

                @Override
                public String toString() {
                    StringBuilder sb = new StringBuilder("[");
                    for (Node cur = head; cur != null; cur = cur.next) {
                        sb.append(cur.value);
                        if (cur.next != null) sb.append(", ");
                    }
                    return sb.append("]").toString();
                }
            }
            """;

    /** 최지호의 풀이: 원본의 변수명·메서드명을 바꾸고 주석을 달고 들여쓰기를 탭으로 바꾼 것 */
    public static String disguised(String original) {
        String s = original;
        String[][] renames = {
                {"Node", "Item"}, {"value", "data"}, {"next", "link"}, {"head", "first"}, {"size", "count"},
                {"cur", "p"}, {"node", "fresh"}, {"prev", "before"}, {"addFirst", "prepend"}, {"add", "append"},
                {"get", "at"}, {"remove", "delete"}, {"reverse", "flip"}, {"index", "pos"}, {"sb", "buf"}};
        for (String[] r : renames) {
            s = s.replaceAll("\\b" + r[0] + "\\b", r[1]);
        }
        s = s.replace("\"index: \"", "\"bad position: \"").replace("    ", "\t");
        s = s.replace("\tpublic void append(", "\t// 맨 뒤에 추가\n\tpublic void append(")
                .replace("\tpublic void flip(", "\t// 순서 뒤집기\n\tpublic void flip(");
        return s;
    }

    /** 한서준의 풀이: 센티넬 노드 + tail 포인터. 구조가 다르다 */
    public static final String SENTINEL_STYLE = """
            public class LinkedList {
                static class Node {
                    final int val;
                    Node next;
                    Node(int val) { this.val = val; }
                }

                private final Node sentinel = new Node(Integer.MIN_VALUE);
                private Node tail = sentinel;
                private int length = 0;

                void push(int v) {
                    tail.next = new Node(v);
                    tail = tail.next;
                    length++;
                }

                int indexOf(int v) {
                    int i = 0;
                    for (Node n = sentinel.next; n != null; n = n.next, i++) {
                        if (n.val == v) return i;
                    }
                    return -1;
                }

                boolean contains(int v) {
                    return indexOf(v) >= 0;
                }

                int pop() {
                    if (length == 0) throw new IllegalStateException("empty");
                    Node prev = sentinel;
                    while (prev.next != tail) prev = prev.next;
                    int out = tail.val;
                    prev.next = null;
                    tail = prev;
                    length--;
                    return out;
                }

                int[] toArray() {
                    int[] arr = new int[length];
                    int k = 0;
                    for (Node n = sentinel.next; n != null; n = n.next) arr[k++] = n.val;
                    return arr;
                }

                int length() { return length; }
            }
            """;

    /** 강도윤의 풀이 v1: 이중 연결 리스트 (prev 포인터 갱신이 빠진 부분이 있다) */
    public static final String DOUBLY_V1 = """
            public class LinkedList {
                private class Entry {
                    int item;
                    Entry prev, next;
                    Entry(int item) { this.item = item; }
                }

                private Entry front, back;
                private int n;

                public void addLast(int item) {
                    Entry e = new Entry(item);
                    if (back == null) {
                        front = back = e;
                    } else {
                        back.next = e;
                        e.prev = back;
                        back = e;
                    }
                    n++;
                }

                public void addFirst(int item) {
                    Entry e = new Entry(item);
                    if (front == null) {
                        front = back = e;
                    } else {
                        e.next = front;
                        front = e;
                    }
                    n++;
                }

                public int removeLast() {
                    int out = back.item;
                    back = back.prev;
                    if (back == null) front = null; else back.next = null;
                    n--;
                    return out;
                }

                public int count() { return n; }
            }
            """;

    /** 강도윤의 풀이 v2: 지적받은 addFirst 의 prev 갱신을 고침 */
    public static final String DOUBLY_V2 = DOUBLY_V1.replace(
            "        e.next = front;\n        front = e;",
            "        e.next = front;\n        front.prev = e;\n        front = e;");

    /** 정하은의 풀이 (지각 제출): 제네릭 + Iterable */
    public static final String GENERIC_STYLE = """
            import java.util.Iterator;

            public class LinkedList<T> implements Iterable<T> {
                private class Cell {
                    T payload;
                    Cell after;
                    Cell(T payload) { this.payload = payload; }
                }

                private Cell start;
                private int total;

                public void insert(T item) {
                    Cell c = new Cell(item);
                    c.after = start;
                    start = c;
                    total++;
                }

                public T first() {
                    return start == null ? null : start.payload;
                }

                public int length() { return total; }

                @Override
                public Iterator<T> iterator() {
                    return new Iterator<T>() {
                        Cell at = start;
                        public boolean hasNext() { return at != null; }
                        public T next() {
                            T v = at.payload;
                            at = at.after;
                            return v;
                        }
                    };
                }
            }
            """;

    // ---- 이민준(학생 1): 피드백 반영 시나리오 ----

    public static final String S1_V1_LIST = """
            public class LinkedList {
                private Node head;

                private static class Node {
                    int data;
                    Node next;
                    Node(int data) { this.data = data; }
                }

                public void add(int data) {
                    Node n = new Node(data);
                    if (head == null) {
                        head = n;
                        return;
                    }
                    Node last = head;
                    while (last.next != null) {
                        last = last.next;
                    }
                    last.next = n;
                }

                public int get(int index) {
                    Node cur = head;
                    for (int i = 0; i < index; i++) cur = cur.next;
                    return cur.data;
                }

                public int size() {
                    int count = 0;
                    Node cur = head;
                    while (cur != null) {
                        count++;
                        cur = cur.next;
                    }
                    return count;
                }

                public void removeFirst() {
                    System.out.println("debug: removeFirst called");
                    if (head != null) head = head.next;
                }

                public int sumAll() {
                    int x = 0;
                    for (Node cur = head; cur != null; cur = cur.next) {
                        x += cur.data;
                    }
                    return x;
                }
            }
            """;

    /** v2: get 의 범위 문제 수정, 디버그 출력 삭제, addFirst 추가(줄 번호가 밀린다). size() 는 그대로 */
    public static final String S1_V2_LIST = S1_V1_LIST
            .replace("        for (int i = 0; i < index; i++) cur = cur.next;",
                    "        for (int i = 0; i < index && cur != null; i++) cur = cur.next;")
            .replace("        System.out.println(\"debug: removeFirst called\");\n", "")
            .replace("    public void add(int data) {",
                    "    public void addFirst(int data) {\n        Node n = new Node(data);\n        n.next = head;\n        head = n;\n    }\n\n    public void add(int data) {");

    /** v3: 변수명 x 를 total 로, get 에 범위 검사(IndexOutOfBoundsException) 추가. size() 는 그대로 */
    public static final String S1_V3_LIST = S1_V2_LIST
            .replace("        int x = 0;", "        int total = 0;")
            .replace("            x += cur.data;", "            total += cur.data;")
            .replace("        return x;", "        return total;")
            .replace("    public int get(int index) {\n        Node cur = head;",
                    "    public int get(int index) {\n        if (index < 0 || index >= size()) {\n            throw new IndexOutOfBoundsException(\"index: \" + index);\n        }\n        Node cur = head;");

    // ---- 자동 채점용 Main.java (표준입력 명령: add N / first N / get I / remove / size / sum) ----
    // 같은 명령 처리 뼈대를 교수가 나눠준 것으로 보고 BASELINE_DRIVER 로 표절 검사에서 제외한다.

    /** 교수가 나눠준 명령 처리 뼈대 (표절 검사에서 제외됨). 학생은 자기 LinkedList API 에 맞게 호출만 채운다 */
    public static final String BASELINE_DRIVER = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    LinkedList list = new LinkedList();
                    Scanner sc = new Scanner(System.in);
                    while (sc.hasNext()) {
                        String cmd = sc.next();
                        if (cmd.equals("add")) {
                            list.add(sc.nextInt());
                        } else if (cmd.equals("first")) {
                            list.addFirst(sc.nextInt());
                        } else if (cmd.equals("get")) {
                            int index = sc.nextInt();
                            try {
                                System.out.println(list.get(index));
                            } catch (IndexOutOfBoundsException e) {
                                System.out.println("ERROR");
                            }
                        } else if (cmd.equals("remove")) {
                            list.removeFirst();
                        } else if (cmd.equals("size")) {
                            System.out.println(list.size());
                        } else if (cmd.equals("sum")) {
                            int total = 0;
                            for (int i = 0; i < list.size(); i++) {
                                total += list.get(i);
                            }
                            System.out.println(total);
                        }
                    }
                }
            }
            """;

    /** 이민준 v1: first 명령이 없다 (addFirst 가 아직 없음) */
    public static final String S1_MAIN_V1 = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    LinkedList list = new LinkedList();
                    Scanner sc = new Scanner(System.in);
                    while (sc.hasNext()) {
                        String cmd = sc.next();
                        if (cmd.equals("add")) {
                            list.add(sc.nextInt());
                        } else if (cmd.equals("get")) {
                            int index = sc.nextInt();
                            try {
                                System.out.println(list.get(index));
                            } catch (IndexOutOfBoundsException e) {
                                System.out.println("ERROR");
                            }
                        } else if (cmd.equals("remove")) {
                            list.removeFirst();
                        } else if (cmd.equals("size")) {
                            System.out.println(list.size());
                        } else if (cmd.equals("sum")) {
                            System.out.println(list.sumAll());
                        }
                    }
                }
            }
            """;

    /** 이민준 v2, v3: first 명령 추가 */
    public static final String S1_MAIN = S1_MAIN_V1.replace(
            "            } else if (cmd.equals(\"get\")) {",
            "            } else if (cmd.equals(\"first\")) {\n                list.addFirst(sc.nextInt());\n            } else if (cmd.equals(\"get\")) {");

    /** 박서연: ORIGINAL 의 API(add, addFirst, get, remove(값), size)에 맞춘 Main */
    public static final String MAIN_S2 = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    LinkedList list = new LinkedList();
                    Scanner sc = new Scanner(System.in);
                    while (sc.hasNext()) {
                        String cmd = sc.next();
                        if (cmd.equals("add")) {
                            list.add(sc.nextInt());
                        } else if (cmd.equals("first")) {
                            list.addFirst(sc.nextInt());
                        } else if (cmd.equals("get")) {
                            int index = sc.nextInt();
                            try {
                                System.out.println(list.get(index));
                            } catch (IndexOutOfBoundsException e) {
                                System.out.println("ERROR");
                            }
                        } else if (cmd.equals("remove")) {
                            list.remove(list.get(0));
                        } else if (cmd.equals("size")) {
                            System.out.println(list.size());
                        } else if (cmd.equals("sum")) {
                            int total = 0;
                            for (int i = 0; i < list.size(); i++) {
                                total += list.get(i);
                            }
                            System.out.println(total);
                        }
                    }
                }
            }
            """;

    /** 최지호: 박서연의 Main 을 위장한 LinkedList(append, prepend, at, delete, count)에 맞춰 그대로 옮겨 쓴 것 */
    public static final String MAIN_S3 = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    LinkedList items = new LinkedList();
                    Scanner in = new Scanner(System.in);
                    while (in.hasNext()) {
                        String command = in.next();
                        if (command.equals("add")) {
                            items.append(in.nextInt());
                        } else if (command.equals("first")) {
                            items.prepend(in.nextInt());
                        } else if (command.equals("get")) {
                            int pos = in.nextInt();
                            try {
                                System.out.println(items.at(pos));
                            } catch (IndexOutOfBoundsException e) {
                                System.out.println("ERROR");
                            }
                        } else if (command.equals("remove")) {
                            items.delete(items.at(0));
                        } else if (command.equals("size")) {
                            System.out.println(items.count());
                        } else if (command.equals("sum")) {
                            int acc = 0;
                            for (int k = 0; k < items.count(); k++) {
                                acc += items.at(k);
                            }
                            System.out.println(acc);
                        }
                    }
                }
            }
            """;

    /** 정하은: 제네릭 LinkedList(insert 는 앞에 넣는다, remove 없음) */
    public static final String MAIN_S4 = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    LinkedList<Integer> list = new LinkedList<>();
                    Scanner sc = new Scanner(System.in);
                    while (sc.hasNext()) {
                        String cmd = sc.next();
                        if (cmd.equals("add") || cmd.equals("first")) {
                            list.insert(sc.nextInt());
                        } else if (cmd.equals("get")) {
                            int index = sc.nextInt();
                            if (index < 0 || index >= list.length()) {
                                System.out.println("ERROR");
                            } else {
                                int k = 0;
                                for (int v : list) {
                                    if (k++ == index) {
                                        System.out.println(v);
                                        break;
                                    }
                                }
                            }
                        } else if (cmd.equals("size")) {
                            System.out.println(list.length());
                        } else if (cmd.equals("sum")) {
                            int total = 0;
                            for (int v : list) {
                                total += v;
                            }
                            System.out.println(total);
                        }
                    }
                }
            }
            """;

    /** 강도윤: 이중 연결 리스트(addLast, addFirst, removeLast, count). get/sum 은 아직 지원하지 않는다 */
    public static final String MAIN_S5 = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    LinkedList list = new LinkedList();
                    Scanner sc = new Scanner(System.in);
                    while (sc.hasNext()) {
                        String cmd = sc.next();
                        if (cmd.equals("add")) {
                            list.addLast(sc.nextInt());
                        } else if (cmd.equals("first")) {
                            list.addFirst(sc.nextInt());
                        } else if (cmd.equals("remove")) {
                            list.removeLast();
                        } else if (cmd.equals("size")) {
                            System.out.println(list.count());
                        }
                    }
                }
            }
            """;

    /** 한서준: 센티넬 리스트(push, pop, length, toArray). first 는 지원하지 않는다 */
    public static final String MAIN_S7 = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    LinkedList list = new LinkedList();
                    Scanner sc = new Scanner(System.in);
                    while (sc.hasNext()) {
                        String cmd = sc.next();
                        if (cmd.equals("add")) {
                            list.push(sc.nextInt());
                        } else if (cmd.equals("get")) {
                            int index = sc.nextInt();
                            int[] all = list.toArray();
                            System.out.println(index >= 0 && index < all.length ? String.valueOf(all[index]) : "ERROR");
                        } else if (cmd.equals("remove")) {
                            list.pop();
                        } else if (cmd.equals("size")) {
                            System.out.println(list.length());
                        } else if (cmd.equals("sum")) {
                            int total = 0;
                            for (int v : list.toArray()) {
                                total += v;
                            }
                            System.out.println(total);
                        }
                    }
                }
            }
            """;

    // ---- 과제 2: 이진 탐색 트리 ----

    public static final String BST_RECURSIVE = """
            public class Bst {
                private static class Node {
                    int key;
                    Node left, right;
                    Node(int key) { this.key = key; }
                }

                private Node root;

                public void insert(int key) {
                    root = insert(root, key);
                }

                private Node insert(Node node, int key) {
                    if (node == null) return new Node(key);
                    if (key < node.key) node.left = insert(node.left, key);
                    else if (key > node.key) node.right = insert(node.right, key);
                    return node;
                }

                public boolean contains(int key) {
                    Node cur = root;
                    while (cur != null) {
                        if (key == cur.key) return true;
                        cur = key < cur.key ? cur.left : cur.right;
                    }
                    return false;
                }
            }
            """;

    public static final String BST_ITERATIVE = """
            public class Bst {
                class Cell {
                    int v;
                    Cell lo, hi;
                    Cell(int v) { this.v = v; }
                }

                Cell top;
                int count;

                void put(int v) {
                    Cell fresh = new Cell(v);
                    count++;
                    if (top == null) { top = fresh; return; }
                    Cell at = top;
                    while (true) {
                        if (v < at.v) {
                            if (at.lo == null) { at.lo = fresh; return; }
                            at = at.lo;
                        } else {
                            if (at.hi == null) { at.hi = fresh; return; }
                            at = at.hi;
                        }
                    }
                }

                int height(Cell c) {
                    return c == null ? 0 : 1 + Math.max(height(c.lo), height(c.hi));
                }
            }
            """;

    public static final List<String> NAMES = List.of("이민준", "박서연", "최지호", "정하은", "강도윤", "윤지우", "한서준");
}
