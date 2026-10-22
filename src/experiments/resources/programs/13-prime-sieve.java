//@rename sieve,limit,primes,count,sieveOf,collect,multiple,i,found,listing,upTo
import java.util.ArrayList;
import java.util.List;

public class Main {
    //@method-start
    // 에라토스테네스의 체
    static boolean[] sieveOf(int limit) {
        boolean[] sieve = new boolean[limit + 1];
        for (int i = 2; i <= limit; i++) {
            sieve[i] = true;
        }
        //@insert
        for (int i = 2; i * i <= limit; i++) {
            if (sieve[i]) {
                for (int multiple = i * i; multiple <= limit; multiple += i) {
                    sieve[multiple] = false;
                }
            }
        }
        return sieve;
    }
    //@method-end

    //@method-start
    static List<Integer> collect(boolean[] sieve) {
        List<Integer> primes = new ArrayList<>();
        for (int i = 2; i < sieve.length; i++) {
            if (sieve[i]) {
                primes.add(i);
            }
        }
        return primes;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int upTo = 60;
        String listing = "primes up to ";
        //@swap-end
        boolean[] sieve = sieveOf(upTo);
        List<Integer> primes = collect(sieve);
        //@insert
        int count = primes.size();
        System.out.println(listing + upTo + ": " + primes);
        System.out.println("count = " + count);
    }
}
