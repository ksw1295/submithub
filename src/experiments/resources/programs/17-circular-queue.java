//@rename items,front,rear,count,capacity,enqueue,dequeue,isFull,isEmpty,value,show,tickets,served,label
public class Main {
    static int capacity = 5;
    static int[] items = new int[capacity];
    static int front = 0;
    static int rear = 0;
    static int count = 0;

    //@method-start
    static boolean isFull() {
        return count == capacity;
    }
    //@method-end

    //@method-start
    static boolean isEmpty() {
        return count == 0;
    }
    //@method-end

    //@method-start
    // 원형 큐의 뒤에 값을 넣는다
    static boolean enqueue(int value) {
        if (isFull()) {
            return false;
        }
        items[rear] = value;
        rear = (rear + 1) % capacity;
        count++;
        return true;
    }
    //@method-end

    //@method-start
    static int dequeue() {
        if (isEmpty()) {
            throw new IllegalStateException("queue is empty");
        }
        int value = items[front];
        front = (front + 1) % capacity;
        count--;
        //@insert
        return value;
    }
    //@method-end

    //@method-start
    static void show(String label) {
        StringBuilder sb = new StringBuilder(label + ": ");
        for (int i = 0; i < count; i++) {
            sb.append(items[(front + i) % capacity]).append(' ');
        }
        System.out.println(sb);
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] tickets = {101, 102, 103, 104, 105, 106};
        int served = 0;
        //@swap-end
        for (int i = 0; i < tickets.length; i++) {
            if (!enqueue(tickets[i])) {
                System.out.println("full, rejected " + tickets[i]);
            }
        }
        show("waiting");
        served = dequeue();
        //@insert
        enqueue(tickets[5]);
        show("after serving " + served);
    }
}
