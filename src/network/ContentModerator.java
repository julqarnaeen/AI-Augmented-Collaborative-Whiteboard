package network;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.Gson;

public class ContentModerator {

    private static final Set<String> BLOCKED_WORDS = new java.util.concurrent.CopyOnWriteArraySet<>();
    private static final String MODERATE_URL = "http://localhost:8000/moderate_text";
    private static final String ADD_SLANG_URL = "http://localhost:8000/add_slang";

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

    private static class ModerationRequest {
        String text;
        ModerationRequest(String text) { this.text = text; }
    }

    private static class ModerationResponse {
        String moderated_text;
    }

    private static class SlangRequest {
        String word;
        SlangRequest(String word) { this.word = word; }
    }

    public static void addBlockedWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            String target = word.trim().toLowerCase();
            BLOCKED_WORDS.add(target);
            syncSlangToPython(target);
        }
    }

    private static void syncSlangToPython(String word) {
        try {
            Gson gson = new Gson();
            SlangRequest req = new SlangRequest(word);
            String jsonPayload = gson.toJson(req);

            URL url = new URL(ADD_SLANG_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(1000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            conn.getResponseCode(); // Fire and forget trigger
        } catch (Exception e) {
            // Silently fail, local copy is still updated
        }
    }

    private static String makeBypassRegex(String word) {
        String lower = word.toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            String part;
            switch (c) {
                case 'a': part = "[a@4]"; break;
                case 'b': part = "[b8]"; break;
                case 'c': part = "[c(]"; break;
                case 'e': part = "[e3]"; break;
                case 'g': part = "[g9]"; break;
                case 'i': part = "[i1!|]"; break;
                case 'l': part = "[l1!|]"; break;
                case 'o': part = "[o0]"; break;
                case 's': part = "[s5$]"; break;
                case 't': part = "[t7+]"; break;
                case 'u': part = "[uv]"; break;
                default: part = Pattern.quote(String.valueOf(c)); break;
            }
            sb.append(part);
            if (i < lower.length() - 1) {
                sb.append("[^a-zA-Z0-9]*");
            }
        }
        if (word.length() >= 4) {
            return "(?i)\\b" + sb.toString() + "\\w*";
        } else {
            return "(?i)\\b" + sb.toString() + "\\b";
        }
    }

    public static String moderateText(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        // Try Python AI service first
        try {
            Gson gson = new Gson();
            ModerationRequest req = new ModerationRequest(input);
            String jsonPayload = gson.toJson(req);

            URL url = new URL(MODERATE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] in = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(in, 0, in.length);
            }

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    ModerationResponse resp = gson.fromJson(response.toString(), ModerationResponse.class);
                    if (resp != null && resp.moderated_text != null) {
                        return resp.moderated_text;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ContentModerator] Python AI service offline. Falling back to local regex moderation.");
        }

        // Fallback local regex matching
        String moderatedText = input;
        for (String word : BLOCKED_WORDS) {
            String regex = makeBypassRegex(word);
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
