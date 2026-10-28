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

    public static final String RAG_V1_ASK = """
import os
import sys
import argparse
import yaml
import chromadb
from sentence_transformers import SentenceTransformer
from src.llm import call_llm

CHROMA_PATH = r"D:\\dev\\devSandbox\\univ-rag-chatbot\\chroma_db"
COLLECTION_NAME = "univ_regulations"
MODEL_NAME = "nlpai-lab/KURE-v1"
SCHOOLS_DIR = r"D:\\dev\\devSandbox\\univ-rag-chatbot\\schools"
DISTANCE_THRESHOLD = 0.35  # 코사인 거리 기준 (이보다 크면 검색 유사도 부족으로 판정하여 LLM 미호출)



def load_school_config(school_id: str) -> dict:
    yaml_path = os.path.join(SCHOOLS_DIR, f"{school_id}.yaml")
    if not os.path.exists(yaml_path):
        raise FileNotFoundError(f"학교 설정 파일을 찾을 수 없습니다: {yaml_path}")
    with open(yaml_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def ask_question(school_id: str, query: str, top_k: int = 5) -> str:
    # 1. 학교 설정 로드
    cfg = load_school_config(school_id)
    school_name = cfg.get("name", school_id)
    contact = cfg.get("contact", "").strip() or "해당 부서"

    # 2. Chroma DB 및 임베딩 모델 연결
    client = chromadb.PersistentClient(path=CHROMA_PATH)
    collection = client.get_collection(name=COLLECTION_NAME)
    model = SentenceTransformer(MODEL_NAME, device="cpu")

    # 3. 질문 임베딩 생성 및 검색 (where={'school_id': school_id} 필수 필터)
    query_emb = model.encode([query], normalize_embeddings=True).tolist()
    results = collection.query(
        query_embeddings=query_emb,
        n_results=top_k,
        where={"school_id": {"$eq": school_id}}
    )

    docs = results.get("documents", [[]])[0]
    metas = results.get("metadatas", [[]])[0]
    distances = results.get("distances", [[]])[0]

    if not docs:
        return f"자료에서 찾지 못함, {contact}로 문의"

    # 4. 검색 점수 임계값 검사 (가장 유사한 문서의 코사인 거리)
    best_distance = distances[0]
    if best_distance > DISTANCE_THRESHOLD:
        return (
            f"자료에서 찾지 못함, {contact}로 문의\\n\\n"
            f"(최고 유사도 거리 {best_distance:.4f}가 임계값 {DISTANCE_THRESHOLD}보다 커서 LLM 호출을 생략했습니다.)"
        )

    # 5. 프롬프트 구성 (학교 정보, 컨텍스트, 질문)
    contexts = []
    sources_summary = []
    for idx, (doc, meta, dist) in enumerate(zip(docs, metas, distances), start=1):
        src = meta.get("source", "")
        art = meta.get("article", "")
        yr = meta.get("year", "")
        pg = meta.get("page", "")
        ref = f"문서: {src}, 조항: {art or '본문/표'}, 학년도: {yr}년, 쪽: {pg}쪽"
        contexts.append(f"[자료 {idx}] ({ref})\\n{doc}")
        sources_summary.append(ref)

    context_str = "\\n\\n".join(contexts)

    prompt = f\"\"\"당신은 {school_name} 규정 및 학사/장학 안내 전문 챗봇입니다.
반드시 아래 제공된 [참고자료]의 내용에만 근거하여 질문에 사실대로 명확하게 답변하십시오.
제공된 자료에 언급되지 않은 사실을 추측하여 답변하거나 외부 지식을 지어내지 마십시오.
만약 주어진 자료만으로 질문에 대한 명확한 근거를 찾을 수 없다면 반드시 "자료에서 찾지 못함, {contact}로 문의"라고 답변하십시오.

답변 마지막에는 반드시 답변의 근거가 된 출처(문서명, 조항, 학년도, 쪽)를 명시하십시오.

[참고자료]
{context_str}

[질문]
{query}

[답변]\"\"\"

    # 6. LLM 호출
    answer = call_llm(prompt)
    return answer.strip()


def main():
    if sys.stdout.encoding.lower() != 'utf-8':
        try:
            sys.stdout.reconfigure(encoding='utf-8')
        except Exception:
            pass

    parser = argparse.ArgumentParser(description="대학 규정/학사 RAG 챗봇 질의")
    parser.add_argument("--school", required=True, help="학교 ID (예: kornu, cnsu)")
    parser.add_argument("query", help="질문 내용")
    args = parser.parse_args()

    answer = ask_question(args.school, args.query)
    print(f"\\n[학교]: {args.school}")
    print(f"[질문]: {args.query}")
    print(f"[답변]:\\n{answer}")


if __name__ == "__main__":
    main()
""";
    public static final String RAG_V1_LLM = """
import os
from dotenv import load_dotenv
from google import genai

# .env 파일이 있으면 환경 변수로 로드 (파일 내용을 직접 출력하거나 열어보지 않음)
load_dotenv()

DEFAULT_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")


def call_llm(prompt: str, model_name: str = DEFAULT_MODEL) -> str:
    \"\"\"Gemini LLM 호출 모듈 (단일 진입점)\"\"\"
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        return "[오류] GEMINI_API_KEY 환경변수가 설정되지 않았습니다."
    
    try:
        client = genai.Client(api_key=api_key)
        response = client.models.generate_content(
            model=model_name,
            contents=prompt,
        )
        return response.text or ""
    except Exception as e:
        return f"[LLM 호출 오류] {e}"
""";
    public static final String RAG_V2_ASK = """
import argparse

from src.common import (
    DISTANCE_THRESHOLD,
    get_collection,
    get_model,
    load_school_config,
    setup_stdout,
)
from src.llm import call_llm


def retrieve(school_id: str, query: str, top_k: int = 5) -> list[dict]:
    \"\"\"school_id 필터로 상위 top_k 조각을 찾는다. 가까운 순서이며 distance는 코사인 거리.\"\"\"
    query_emb = get_model().encode([query], normalize_embeddings=True).tolist()
    results = get_collection().query(
        query_embeddings=query_emb,
        n_results=top_k,
        where={"school_id": {"$eq": school_id}},
    )
    docs = results.get("documents", [[]])[0]
    metas = results.get("metadatas", [[]])[0]
    distances = results.get("distances", [[]])[0]
    return [{"text": d, "meta": m, "distance": x} for d, m, x in zip(docs, metas, distances)]


def is_answerable(hits: list[dict]) -> bool:
    \"\"\"가장 가까운 조각이 임계값 안이면 LLM에 넘길 만한 근거가 있다고 본다.\"\"\"
    return bool(hits) and hits[0]["distance"] <= DISTANCE_THRESHOLD


def ask_question(school_id: str, query: str, top_k: int = 5) -> str:
    cfg = load_school_config(school_id)
    school_name = cfg.get("name", school_id)
    contact = cfg.get("contact", "").strip() or "해당 부서"

    hits = retrieve(school_id, query, top_k)
    if not hits:
        return f"자료에서 찾지 못함, {contact}로 문의"

    if not is_answerable(hits):
        return (
            f"자료에서 찾지 못함, {contact}로 문의\\n\\n"
            f"(최고 유사도 거리 {hits[0]['distance']:.4f}가 임계값 {DISTANCE_THRESHOLD}보다 커서 LLM 호출을 생략했습니다.)"
        )

    contexts = []
    for idx, h in enumerate(hits, start=1):
        meta = h["meta"]
        ref = (
            f"문서: {meta.get('source', '')}, 조항: {meta.get('article') or '본문/표'}, "
            f"학년도: {meta.get('year', '')}년, 쪽: {meta.get('page', '')}쪽"
        )
        contexts.append(f"[자료 {idx}] ({ref})\\n{h['text']}")
    context_str = "\\n\\n".join(contexts)

    prompt = f\"\"\"당신은 {school_name} 규정 및 학사/장학 안내 전문 챗봇입니다.
반드시 아래 제공된 [참고자료]의 내용에만 근거하여 질문에 사실대로 명확하게 답변하십시오.
제공된 자료에 언급되지 않은 사실을 추측하여 답변하거나 외부 지식을 지어내지 마십시오.
만약 주어진 자료만으로 질문에 대한 명확한 근거를 찾을 수 없다면 반드시 "자료에서 찾지 못함, {contact}로 문의"라고 답변하십시오.

답변 마지막에는 반드시 답변의 근거가 된 출처(문서명, 조항, 학년도, 쪽)를 명시하십시오.

[참고자료]
{context_str}

[질문]
{query}

[답변]\"\"\"

    return call_llm(prompt).strip()


def format_sources(hits: list[dict]) -> str:
    lines = []
    for i, h in enumerate(hits, start=1):
        m = h["meta"]
        lines.append(f"  {i}. 거리 {h['distance']:.4f} | {m.get('source')} {m.get('article') or '-'} (p{m.get('page')}, {m.get('year')}학년도)")
    return "\\n".join(lines)


def main():
    setup_stdout()
    parser = argparse.ArgumentParser(description="대학 규정/학사 RAG 챗봇 질의")
    parser.add_argument("--school", required=True, help="학교 ID (예: kornu, cnsu)")
    parser.add_argument("query", help="질문 내용")
    parser.add_argument("--sources", action="store_true", help="검색된 상위 조각(거리·문서·조항)도 함께 출력")
    args = parser.parse_args()

    answer = ask_question(args.school, args.query)
    print(f"\\n[학교]: {args.school}")
    print(f"[질문]: {args.query}")
    if args.sources:
        print("[검색된 조각]:\\n" + format_sources(retrieve(args.school, args.query)))
    print(f"[답변]:\\n{answer}")


if __name__ == "__main__":
    main()
""";
    public static final String RAG_V2_LLM = """
import os
from dotenv import load_dotenv
from google import genai

# .env 파일이 있으면 환경 변수로 로드 (파일 내용을 직접 출력하거나 열어보지 않음)
load_dotenv()

DEFAULT_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.0-flash")


def get_api_key() -> str | None:
    \"\"\"Streamlit Secrets 및 환경 변수에서 GEMINI_API_KEY를 탐색 (폴백 지원)\"\"\"
    try:
        import streamlit as st
        if hasattr(st, "secrets") and "GEMINI_API_KEY" in st.secrets:
            return str(st.secrets["GEMINI_API_KEY"]).strip()
    except Exception:
        pass
    return os.environ.get("GEMINI_API_KEY")


def get_model_name(default_model: str | None = None) -> str:
    \"\"\"Streamlit Secrets 및 환경 변수에서 GEMINI_MODEL을 탐색\"\"\"
    try:
        import streamlit as st
        if hasattr(st, "secrets") and "GEMINI_MODEL" in st.secrets:
            return str(st.secrets["GEMINI_MODEL"]).strip()
    except Exception:
        pass
    return os.environ.get("GEMINI_MODEL") or default_model or DEFAULT_MODEL


def call_llm(prompt: str, model_name: str | None = None) -> str:
    \"\"\"Gemini LLM 호출 모듈 (단일 진입점)\"\"\"
    api_key = get_api_key()
    if not api_key:
        return "[오류] GEMINI_API_KEY가 환경 변수 또는 Streamlit Secrets에 설정되지 않았습니다."
    
    target_model = model_name or get_model_name()
    try:
        client = genai.Client(api_key=api_key)
        response = client.models.generate_content(
            model=target_model,
            contents=prompt,
        )
        return response.text or ""
    except Exception as e:
        return f"[LLM 호출 오류] {e}"
""";
    public static final String RAG_V2_COMMON = """
\"\"\"경로, 임베딩 모델, Chroma 컬렉션 등 여러 모듈이 같이 쓰는 설정과 자원 (프로세스당 한 번만 로드).\"\"\"
import os
import sys
from functools import lru_cache
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
DATA_RAW_DIR = ROOT / "data" / "raw"
SCHOOLS_DIR = ROOT / "schools"
CHUNKS_FILE = ROOT / "data" / "chunks.jsonl"
EVAL_DIR = ROOT / "eval"
CHROMA_PATH = Path(os.environ.get("UNIV_RAG_CHROMA_DIR") or ROOT / "chroma_db")

COLLECTION_NAME = "univ_regulations"
MODEL_NAME = "nlpai-lab/KURE-v1"
MAX_SEQ_LENGTH = 512  # 조각 대부분은 이보다 짧음. 긴 표 조각의 꼬리만 잘리고 CPU 시간이 크게 줄어듦
DISTANCE_THRESHOLD = 0.35  # 코사인 거리 기준 (이보다 크면 검색 유사도 부족으로 판정하여 LLM 미호출)


def setup_stdout():
    \"\"\"Windows 콘솔(cp949)에서 특수문자 출력 오류가 나지 않도록 utf-8로 맞춘다.\"\"\"
    enc = (getattr(sys.stdout, "encoding", None) or "").lower()
    if enc != "utf-8":
        try:
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass


def resolve_device() -> str:
    \"\"\"UNIV_RAG_DEVICE 환경변수가 있으면 그 값, 없으면 CUDA가 쓸 수 있을 때 cuda, 아니면 cpu.\"\"\"
    forced = os.environ.get("UNIV_RAG_DEVICE")
    if forced:
        return forced
    import torch

    return "cuda" if torch.cuda.is_available() else "cpu"


@lru_cache(maxsize=1)
def get_model():
    \"\"\"임베딩 모델은 프로세스에서 한 번만 로드한다.\"\"\"
    from sentence_transformers import SentenceTransformer

    model = SentenceTransformer(MODEL_NAME, device=resolve_device())
    model.max_seq_length = MAX_SEQ_LENGTH
    return model


@lru_cache(maxsize=1)
def get_collection():
    import chromadb

    client = chromadb.PersistentClient(path=str(CHROMA_PATH))
    return client.get_collection(name=COLLECTION_NAME)


def list_school_ids() -> list[str]:
    return sorted(p.stem for p in SCHOOLS_DIR.glob("*.yaml"))


def load_school_config(school_id: str) -> dict:
    yaml_path = SCHOOLS_DIR / f"{school_id}.yaml"
    if not yaml_path.exists():
        raise FileNotFoundError(f"학교 설정 파일을 찾을 수 없습니다: {yaml_path}")
    with open(yaml_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)
""";
    public static final String RAG_README = """
# 대학 RAG 챗봇 (univ-rag-chatbot)

여러 대학의 공개 규정 자료(학칙, 장학 규정, 학사 규정)를 근거로 질문에 답변하는 RAG(검색 증강 생성) 챗봇입니다.

---

## 1. 개요 및 설계 원칙
* **학교 설정 기반 확장**: 각 학교는 `schools/<school_id>.yaml` 설정 파일 하나로 관리됩니다.
* **단일 벡터 컬렉션 & 메타데이터 필터**: 모든 문서는 단일 Chroma 컬렉션에 적재되며, 질의 시 `school_id` 메타데이터 필터링을 통해 학교 간 데이터 혼선을 원천 차단합니다.
* **근거 기반 답변**: 주어진 참고자료에 명시된 내용으로만 답변하며, 자료에 없거나 검색 유사도가 낮을 경우 답변을 지어내지 않고 학교별 문의처로 안내합니다.
* **표(Table) 구조 보존**: `pdfplumber`를 이용해 격자형 표를 Markdown Table 형식의 독립 청크로 추출합니다.

---

## 2. 설치 방법 (Python 3.11 이상 권장)

### (1) 가상환경 생성 및 패키지 설치
```bash
# 가상환경 생성 (uv 또는 python venv 사용)
python -m uv venv .venv --python 3.12
# 또는: python -m venv .venv

# 가상환경 활성화 (Windows)
.venv\\Scripts\\activate

# 의존성 설치
pip install -r requirements.txt
```

### (2) 환경 변수 설정
LLM 응답 생성을 위해 프로젝트 루트 디렉터리에 `.env` 파일을 생성하고 Gemini API 키를 설정합니다.
```env
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-2.0-flash
```
*(주의: 검색 유사도가 임계값 미만인 경우 LLM을 호출하지 않고 즉시 문의처를 안내하므로, API 키 없이도 필터링 기능은 정상 작동합니다.)*

---

## 3. 자료 넣는 법
자료는 학교 홈페이지의 `robots.txt` 준수 여부를 확인한 후, 직접 수동 다운로드하여 아래 경로 규칙에 맞춰 배치합니다.
```
data/raw/<school_id>/
├── original/               # 수동 다운로드한 원본 HWP/HWPX 파일
└── <doc_type>_<학년도>.pdf # PDF 변환본 (doc_type: rule / scholarship / academic)
```

예시:
* `data/raw/kornu/rule_2026.pdf` (나사렛대학교 학칙)
* `data/raw/kornu/scholarship_2026.pdf` (나사렛대학교 장학 규정)
* `data/raw/kornu/academic_2026.pdf` (나사렛대학교 학사에 관한 규정)
* `data/raw/cnsu/rule_2026.pdf` (충남도립대학교 학칙)
* `data/raw/cnsu/scholarship_2024.pdf` (충남도립대학교 장학 규정)
* `data/raw/cnsu/academic_2025.pdf` (충남도립대학교 학칙 시행세칙)

---

## 4. 실행 순서

### Step 1: 문서 파싱 및 조각(청킹) 생성
PDF 파일에서 본문(`제N조` 단위 분할) 및 표(Markdown 형식)를 추출하여 `data/chunks.jsonl`에 저장합니다.
```bash
python -m src.ingest
```
* 출력: 학교별 총 조각 수, 본문 조각 수, 표 조각 수

### Step 2: 벡터 임베딩 및 Chroma DB 인덱싱
로컬 한국어 임베딩 모델(`nlpai-lab/KURE-v1`)을 통해 조각별 벡터를 생성하고 Chroma DB에 저장합니다. CUDA를 쓸 수 있으면 GPU, 아니면 CPU로 돌며 `UNIV_RAG_DEVICE=cpu` 처럼 직접 지정할 수도 있습니다. CPU에서는 수 분이 걸립니다.
```bash
python -m src.index            # 자료(학교·파일) 단위로 옛 기록을 지우고 다시 넣음
python -m src.index --reset    # 컬렉션을 통째로 지우고 처음부터
```
* 출력: 진행률(경과 초), 최종 저장된 레코드 수, 걸린 시간

### Step 3: 질문 및 답변 질의 (ask)
특정 학교 ID를 지정하여 질문하고, 검색된 문맥을 바탕으로 답변을 받습니다.
```bash
# 나사렛대학교 질의 예시
python -m src.ask --school kornu "나사렛대학교의 3대 교육목표는 무엇인가요?"

# 충남도립대학교 질의 예시
python -m src.ask --school cnsu "충남도립대학교 본교의 위치는 어디인가요?"

# 검색된 조각(거리·문서·조항)도 같이 보기
python -m src.ask --school cnsu --sources "충남도립대학교 본교의 위치는 어디인가요?"
```

### Step 4: 검색 정확도 평가 (Hit@3)
골든 질문 세트(`eval/questions_<학교ID>.jsonl`)를 대상으로 상위 3개 검색 결과에 **정답 문서(`expected_source`)의 정확한 조항(`expected_article`)**이 있는지(Hit@3) 평가합니다. 자료에 없는 질문(`type: unanswerable`)은 임계값(0.35)에서 실제로 걸러지는지 함께 보여 줍니다.
> **참고**: 현재 평가 문항은 규정 원문을 기반으로 생성된 **사람 확인 전 초안**입니다.
```bash
python -m eval.run_eval
```
""";

    public static final List<String> NAMES = List.of("이민준", "박서연", "최지호", "정하은", "강도윤", "윤지우", "한서준");
}
