//@rename sentence,words,freq,word,best,bestCount,countWords,isPalindrome,cleaned,text,left,right,entry,report
import java.util.HashMap;
import java.util.Map;

public class Main {
    //@method-start
    // 단어별 등장 횟수를 센다
    static Map<String, Integer> countWords(String sentence) {
        Map<String, Integer> freq = new HashMap<>();
        String[] words = sentence.toLowerCase().split("\\s+");
        //@insert
        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^a-z]", "");
            if (word.isEmpty()) {
                continue;
            }
            freq.put(word, freq.getOrDefault(word, 0) + 1);
        }
        return freq;
    }
    //@method-end

    //@method-start
    // 알파벳만 남기고 대소문자를 무시해서 회문인지 검사한다
    static boolean isPalindrome(String text) {
        String cleaned = text.toLowerCase().replaceAll("[^a-z0-9]", "");
        int left = 0;
        int right = cleaned.length() - 1;
        while (left < right) {
            if (cleaned.charAt(left) != cleaned.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }
    //@method-end

    //@method-start
    static String report(Map<String, Integer> freq) {
        String best = "";
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : freq.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best + " x" + bestCount;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        String sentence = "The quick brown fox jumps over the lazy dog the end";
        String banner = "word statistics";
        String famous = "A man, a plan, a canal: Panama";
        //@swap-end
        System.out.println(banner);
        Map<String, Integer> freq = countWords(sentence);
        System.out.println("most frequent: " + report(freq));
        //@insert
        System.out.println(isPalindrome(famous));
        System.out.println(isPalindrome("not a palindrome"));
    }
}
