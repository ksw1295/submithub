//@rename isPrime,divisor,candidate,found,total,primeCount,firstN,nth,line,perLine,bound
public class Main {
    //@method-start
    // 약수 후보를 홀수만 시도하는 소수 판별
    static boolean isPrime(int candidate) {
        if (candidate < 2) {
            return false;
        }
        if (candidate % 2 == 0) {
            return candidate == 2;
        }
        int divisor = 3;
        //@insert
        while (divisor * divisor <= candidate) {
            if (candidate % divisor == 0) {
                return false;
            }
            divisor += 2;
        }
        return true;
    }
    //@method-end

    //@method-start
    // 처음 firstN 개의 소수의 합
    static long total(int firstN) {
        long sum = 0;
        int found = 0;
        int candidate = 2;
        while (found < firstN) {
            if (isPrime(candidate)) {
                sum += candidate;
                found++;
            }
            candidate++;
        }
        return sum;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int bound = 60;
        int perLine = 10;
        //@swap-end
        int primeCount = 0;
        StringBuilder line = new StringBuilder();
        for (int n = 2; n <= bound; n++) {
            if (isPrime(n)) {
                line.append(n).append(primeCount % perLine == perLine - 1 ? "\n" : " ");
                primeCount++;
            }
        }
        //@insert
        System.out.println(line.toString().trim());
        System.out.println(primeCount + " primes, sum of first 10 = " + total(10));
    }
}
