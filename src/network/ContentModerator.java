package network;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ContentModerator.java
 * =====================
 *
 * This class implements the Automated Content Moderation (Vulgar Text & Slang Detection)
 * feature from the project proposal.
 *
 * It uses an NLP-style text-filtering module based on regex word boundaries and a compiled
 * dictionary of offensive slang, profanities, and inappropriate keywords.
 *
 * Any detected inappropriate terms are automatically replaced with a redacted placeholder (e.g., "****")
 * before they are drawn on the local canvas or broadcasted over the network.
 */
public class ContentModerator {

    // A thread-safe set of offensive slang/vulgar keywords to check against.
    private static final Set<String> BLOCKED_WORDS = new CopyOnWriteArraySet<>();

    static {
        // Common placeholder offensive words and typical inappropriate slang
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

    /**
     * Dynamically registers a new custom slang or word to be moderated.
     */
    public static void addBlockedWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            BLOCKED_WORDS.add(word.trim().toLowerCase());
        }
    }

    /**
     * Scans the input text for inappropriate words or vulgar slang, and replaces them
     * with redaction characters (asterisks) matching the length of the words.
     *
     * We use regex pattern "\\b(word)\\b" to ensure we only match whole words
     * (preventing false positives like "assessment" matching "ass").
     *
     * @param input The raw input text entered by the user
     * @return The moderated, safe text
     */
    public static String moderateText(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        String moderatedText = input;

        // Iterate through all blocked terms and replace occurrences using word-boundary regex
        for (String word : BLOCKED_WORDS) {
            // Case-insensitive matching at word boundaries
            String regex = "(?i)\\b" + Pattern.quote(word) + "\\b";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(moderatedText);

            if (matcher.find()) {
                // Generate a mask of asterisks matching the word length
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
