//@rename gcd,lcm,isPerfect,sumDivisors,first,second,rest,sum,limit,found,candidate,label
public class Main {
    //@method-start
    // 유클리드 호제법
    static int gcd(int first, int second) {
        while (second != 0) {
            int rest = first % second;
            first = second;
            second = rest;
        }
        return first;
    }
    //@method-end

    //@method-start
    static int lcm(int first, int second) {
        return first / gcd(first, second) * second;
    }
    //@method-end

    //@method-start
    // 자기 자신을 제외한 약수의 합
    static int sumDivisors(int candidate) {
        int sum = 0;
        //@insert
        for (int i = 1; i <= candidate / 2; i++) {
            if (candidate % i == 0) {
                sum += i;
            }
        }
        return sum;
    }
    //@method-end

    //@method-start
    static boolean isPerfect(int candidate) {
        return candidate > 1 && sumDivisors(candidate) == candidate;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int limit = 500;
        String label = "perfect numbers: ";
        int found = 0;
        //@swap-end
        System.out.println("gcd(84, 36) = " + gcd(84, 36));
        System.out.println("lcm(84, 36) = " + lcm(84, 36));
        StringBuilder out = new StringBuilder(label);
        for (int n = 1; n <= limit; n++) {
            if (isPerfect(n)) {
                out.append(n).append(' ');
                found++;
            }
        }
        //@insert
        System.out.println(out + "(" + found + ")");
    }
}
