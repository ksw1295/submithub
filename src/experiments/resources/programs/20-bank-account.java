//@rename Account,owner,balance,deposit,withdraw,transfer,amount,history,account,source,target,log,report,first,second
import java.util.ArrayList;
import java.util.List;

public class Main {
    static class Account {
        private final String owner;
        private int balance;
        private final List<String> history = new ArrayList<>();

        Account(String owner, int balance) {
            this.owner = owner;
            this.balance = balance;
        }

        //@method-start
        // 입금
        void deposit(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            balance += amount;
            history.add("deposit " + amount);
        }
        //@method-end

        //@method-start
        // 출금: 잔액이 모자라면 예외
        void withdraw(int amount) {
            if (amount > balance) {
                throw new IllegalStateException("insufficient funds");
            }
            balance -= amount;
            //@insert
            history.add("withdraw " + amount);
        }
        //@method-end

        //@method-start
        String report() {
            return owner + ": " + balance + " " + history;
        }
        //@method-end
    }

    //@method-start
    static void transfer(Account source, Account target, int amount) {
        source.withdraw(amount);
        target.deposit(amount);
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        Account first = new Account("kim", 1000);
        Account second = new Account("lee", 500);
        //@swap-end
        first.deposit(200);
        for (int i = 1; i <= 3; i++) {
            second.deposit(i * 10);
        }
        transfer(first, second, 700);
        try {
            transfer(second, first, 5000);
        } catch (IllegalStateException e) {
            System.out.println("failed: " + e.getMessage());
        }
        //@insert
        System.out.println(first.report());
        System.out.println(second.report());
    }
}
