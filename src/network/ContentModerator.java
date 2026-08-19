package network;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContentModerator {

    private static final Set<String> BLOCKED_WORDS = new CopyOnWriteArraySet<>();

    static {

        BLOCKED_WORDS.add("badword");
        BLOCKED_WORDS.add("badword1");
        BLOCKED_WORDS.add("badword2");
        BLOCKED_WORDS.add("hate");
        BLOCKED_WORDS.add("kill");
        BLOCKED_WORDS.add("stupid");
        BLOCKED_WORDS.add("vulgar");
        BLOCKED_WORDS.add("slang");
        BLOCKED_WORDS.add("spam");
        BLOCKED_WORDS.add("scam");
        BLOCKED_WORDS.add("idiot");
        BLOCKED_WORDS.add("crap");
    }

    public static void addBlockedWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            BLOCKED_WORDS.add(word.trim().toLowerCase());
        }
    }

    public static String moderateText(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        String moderatedText = input;

        for (String word : BLOCKED_WORDS) {

            String regex = "(?i)\\b" + Pattern.quote(word) + "\\b";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(moderatedText);

            if (matcher.find()) {

                StringBuilder mask = new StringBuilder();
                for (int i = 0; i < word.length(); i++) {
                    mask.append("*");
                }
                moderatedText = matcher.replaceAll(mask.toString());
            }
        }

        return moderatedText;
    }
}
