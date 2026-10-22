//@rename reverse,countVowels,capitalize,source,result,total,vowels,word,parts,first,rest,sample,title
public class Main {
    //@method-start
    // 문자열을 뒤집는다
    static String reverse(String source) {
        String result = "";
        //@insert
        for (int i = source.length() - 1; i >= 0; i--) {
            result += source.charAt(i);
        }
        return result;
    }
    //@method-end

    //@method-start
    // 모음의 개수를 센다
    static int countVowels(String source) {
        int total = 0;
        String vowels = "aeiouAEIOU";
        for (int i = 0; i < source.length(); i++) {
            if (vowels.indexOf(source.charAt(i)) >= 0) {
                total++;
            }
        }
        return total;
    }
    //@method-end

    //@method-start
    // 각 단어의 첫 글자를 대문자로 바꾼다
    static String capitalize(String source) {
        String[] parts = source.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : parts) {
            if (word.isEmpty()) {
                continue;
            }
            String first = word.substring(0, 1).toUpperCase();
            String rest = word.substring(1);
            result.append(first).append(rest).append(' ');
        }
        return result.toString().trim();
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        String sample = "hello java programming world";
        String title = "string utils";
        //@swap-end
        System.out.println(title);
        System.out.println(reverse(sample));
        //@insert
        System.out.println("vowels = " + countVowels(sample));
        System.out.println(capitalize(sample));
    }
}
