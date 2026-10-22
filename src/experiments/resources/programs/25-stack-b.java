//@rename Link,symbol,below,peak,put,take,empty,check,expr,sym,expected,cases,labels
public class Main {
    static class Link {
        char symbol;
        Link below;

        Link(char symbol, Link below) {
            this.symbol = symbol;
            this.below = below;
        }
    }

    static Link peak = null;

    //@method-start
    static void put(char symbol) {
        peak = new Link(symbol, peak);
    }
    //@method-end

    //@method-start
    static char take() {
        char symbol = peak.symbol;
        peak = peak.below;
        return symbol;
    }
    //@method-end

    //@method-start
    static boolean empty() {
        return peak == null;
    }
    //@method-end

    //@method-start
    // switch 로 여는 괄호는 짝이 되는 닫는 괄호를 쌓아 두고, 닫는 괄호가 나오면 꺼내서 비교한다
    static boolean check(String expr) {
        peak = null;
        //@insert
        for (char sym : expr.toCharArray()) {
            switch (sym) {
                case '(':
                    put(')');
                    break;
                case '[':
                    put(']');
                    break;
                case '{':
                    put('}');
                    break;
                case ')':
                case ']':
                case '}':
                    if (empty() || take() != sym) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        return empty();
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        String[] cases = {"([]{})", "(()", "{[}]", "x + (y * z)"};
        String labels = "ok?";
        //@swap-end
        for (int k = 0; k < cases.length; k++) {
            String expected = cases[k];
            System.out.println(labels + " " + expected + " => " + check(expected));
        }
    }
}
