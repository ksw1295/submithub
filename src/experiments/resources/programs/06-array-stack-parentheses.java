//@rename stack,top,capacity,push,pop,peek,isEmpty,isBalanced,text,ch,open,close,i,tests,verdict
public class Main {
    static char[] stack = new char[100];
    static int top = -1;

    //@method-start
    static void push(char ch) {
        top++;
        stack[top] = ch;
    }
    //@method-end

    //@method-start
    static char pop() {
        char ch = stack[top];
        top--;
        return ch;
    }
    //@method-end

    //@method-start
    static boolean isEmpty() {
        return top == -1;
    }
    //@method-end

    //@method-start
    // 괄호가 올바르게 짝지어졌는지 검사한다
    static boolean isBalanced(String text) {
        top = -1;
        //@insert
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') {
                push(ch);
            } else if (ch == ')' || ch == ']' || ch == '}') {
                if (isEmpty()) {
                    return false;
                }
                char open = pop();
                if ((ch == ')' && open != '(') || (ch == ']' && open != '[') || (ch == '}' && open != '{')) {
                    return false;
                }
            }
        }
        return isEmpty();
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        String[] tests = {"(a+b)*[c-d]", "{[()]}", "(]", "((())", ""};
        String verdict = "balanced";
        //@swap-end
        for (int t = 0; t < tests.length; t++) {
            boolean result = isBalanced(tests[t]);
            System.out.println("\"" + tests[t] + "\" is " + (result ? verdict : "NOT " + verdict));
        }
    }
}
