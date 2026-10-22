//@rename Cell,item,before,after,first,last,count,push,pop,contains,render,walker,found,demo
public class Main {
    static class Cell {
        int item;
        Cell before;
        Cell after;

        Cell(int item) {
            this.item = item;
        }
    }

    //@swap-start
    static Cell first;
    static Cell last;
    static int length;
    //@swap-end

    //@method-start
    // 양방향 연결 리스트의 끝에 넣는다
    static void push(int item) {
        Cell cell = new Cell(item);
        if (last == null) {
            first = cell;
            last = cell;
        } else {
            cell.before = last;
            last.after = cell;
            last = cell;
        }
        length++;
    }
    //@method-end

    //@method-start
    // 끝에서 꺼낸다
    static int pop() {
        if (last == null) {
            throw new RuntimeException("nothing to pop");
        }
        int item = last.item;
        last = last.before;
        //@insert
        if (last == null) {
            first = null;
        } else {
            last.after = null;
        }
        length--;
        return item;
    }
    //@method-end

    //@method-start
    static boolean contains(int item) {
        Cell walker = first;
        while (walker != null && walker.item != item) {
            walker = walker.after;
        }
        return walker != null;
    }
    //@method-end

    //@method-start
    // 뒤에서부터 거꾸로 문자열을 만든다
    static String render() {
        StringBuilder out = new StringBuilder("[");
        for (Cell walker = last; walker != null; walker = walker.before) {
            out.append(walker.item);
            if (walker.before != null) {
                out.append(" <- ");
            }
        }
        return out.append("]").toString();
    }
    //@method-end

    public static void main(String[] args) {
        for (int i = 1; i <= 4; i++) {
            push(i * 11);
        }
        System.out.println(render() + " len=" + length);
        int removed = pop();
        //@insert
        System.out.println("popped " + removed + ", has 22? " + contains(22) + ", " + render());
    }
}
