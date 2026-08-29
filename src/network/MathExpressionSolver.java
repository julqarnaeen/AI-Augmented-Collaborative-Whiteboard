// Evaluates math expressions typed on the canvas with a self-contained recursive-descent parser.
package network;

import java.util.List;
import network.WhiteboardPanel.TextElement;

public class MathExpressionSolver {

    // Result of a solve: the expression, its value, and where to place it.
    public static class MathSolveResponse {
        public String expression;
        public String result;
        public int text_x;
        public int text_y;
    }

    // Finds the newest canvas text that parses as math and returns its result.
    public static MathSolveResponse solve(List<TextElement> texts) {
        if (texts == null) return null;
        for (int i = texts.size() - 1; i >= 0; i--) {
            TextElement te = texts.get(i);
            Double value = eval(te.getText());
            if (value != null) {
                MathSolveResponse r = new MathSolveResponse();
                r.expression = te.getText().trim();
                r.result = format(value);
                r.text_x = te.getX() + Math.max(40, te.getText().length() * te.getFontSize() / 2);
                r.text_y = te.getY();
                return r;
            }
        }
        return null;
    }

    // Formats a value as a whole number when it is one, otherwise to six decimals.
    public static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "undefined";
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.valueOf(Math.round(v * 1e6) / 1e6);
    }

    // Parses and evaluates an expression string, returning null if it is not valid math.
    public static Double eval(String raw) {
        if (raw == null) return null;
        String expr = raw.trim()
                .replace('\u00d7', '*').replace('\u00f7', '/')
                .replace('\u2212', '-').replace("^", "^");
        while (expr.endsWith("=") || expr.endsWith("?")) expr = expr.substring(0, expr.length() - 1).trim();
        if (expr.isEmpty()) return null;
        try {
            Parser p = new Parser(expr);
            double v = p.parseExpression();
            p.skipSpaces();
            if (p.pos < p.s.length()) return null;
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Recursive-descent parser over one expression string.
    private static class Parser {
        final String s;
        int pos = 0;

        // Starts a parser at the beginning of the given text.
        Parser(String s) { this.s = s; }

        // Advances past any whitespace.
        void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        // Consumes the next character if it matches, reporting whether it did.
        boolean eat(char c) {
            skipSpaces();
            if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; }
            return false;
        }

        // Parses addition and subtraction, the lowest precedence level.
        double parseExpression() {
            double x = parseTerm();
            while (true) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        // Parses multiplication, division, and remainder.
        double parseTerm() {
            double x = parseFactor();
            while (true) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) x /= parseFactor();
                else if (eat('%')) x %= parseFactor();
                else return x;
            }
        }

        // Parses signs and exponentiation.
        double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();
            double x = parseAtom();
            if (eat('^')) x = Math.pow(x, parseFactor());
            return x;
        }

        // Parses a number, a bracketed expression, a constant, or a function call.
        double parseAtom() {
            skipSpaces();
            if (pos >= s.length()) throw new IllegalStateException("unexpected end");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                double v = parseExpression();
                if (!eat(')')) throw new IllegalStateException("missing )");
                return v;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
                return Double.parseDouble(s.substring(start, pos));
            }
            if (Character.isLetter(c)) {
                int start = pos;
                while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
                String name = s.substring(start, pos).toLowerCase();
                if (name.equals("pi")) return Math.PI;
                if (name.equals("e")) return Math.E;
                double arg;
                if (eat('(')) {
                    arg = parseExpression();
                    if (!eat(')')) throw new IllegalStateException("missing )");
                } else {
                    arg = parseFactor();
                }
                switch (name) {
                    case "sqrt": return Math.sqrt(arg);
                    case "abs": return Math.abs(arg);
                    case "sin": return Math.sin(Math.toRadians(arg));
                    case "cos": return Math.cos(Math.toRadians(arg));
                    case "tan": return Math.tan(Math.toRadians(arg));
                    case "log": return Math.log10(arg);
                    case "ln": return Math.log(arg);
                    case "round": return Math.rint(arg);
                    case "floor": return Math.floor(arg);
                    case "ceil": return Math.ceil(arg);
                    default: throw new IllegalStateException("unknown function " + name);
                }
            }
            throw new IllegalStateException("unexpected char " + c);
        }
    }
}
