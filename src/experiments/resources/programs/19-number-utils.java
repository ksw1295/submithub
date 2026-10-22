//@rename digitSum,collatzSteps,isArmstrong,power,number,steps,current,digits,temp,sum,limit,start,found,tag
public class Main {
    //@method-start
    // 각 자릿수의 합
    static int digitSum(int number) {
        int sum = 0;
        while (number > 0) {
            sum += number % 10;
            number /= 10;
        }
        return sum;
    }
    //@method-end

    //@method-start
    // 콜라츠 추측: 1 에 도달할 때까지 걸린 횟수
    static int collatzSteps(long start) {
        int steps = 0;
        long current = start;
        //@insert
        while (current != 1) {
            if (current % 2 == 0) {
                current = current / 2;
            } else {
                current = 3 * current + 1;
            }
            steps++;
        }
        return steps;
    }
    //@method-end

    //@method-start
    static int power(int base, int exp) {
        int result = 1;
        for (int i = 0; i < exp; i++) {
            result *= base;
        }
        return result;
    }
    //@method-end

    //@method-start
    // 암스트롱 수: 각 자릿수의 (자릿수)제곱의 합이 자기 자신
    static boolean isArmstrong(int number) {
        int digits = String.valueOf(number).length();
        int temp = number;
        int sum = 0;
        while (temp > 0) {
            sum += power(temp % 10, digits);
            temp /= 10;
        }
        return sum == number;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int limit = 1000;
        String tag = "armstrong:";
        int found = 0;
        //@swap-end
        System.out.println("digitSum(98765) = " + digitSum(98765));
        System.out.println("collatz(27) = " + collatzSteps(27));
        StringBuilder out = new StringBuilder(tag);
        for (int n = 1; n < limit; n++) {
            if (isArmstrong(n)) {
                out.append(' ').append(n);
                found++;
            }
        }
        //@insert
        System.out.println(out + " [" + found + "]");
    }
}
