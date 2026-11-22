package kookyohwan.ui;

public final class TextUtil {
    private TextUtil() {
    }

    public static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += widthOf(cp);
            i += Character.charCount(cp);
        }
        return w;
    }

    private static int widthOf(int cp) {
        boolean wide = (cp >= 0x1100 && cp <= 0x115F)
                || (cp >= 0x2E80 && cp <= 0xA4CF)
                || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFE30 && cp <= 0xFE4F)
                || (cp >= 0xFF00 && cp <= 0xFF60)
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x1F300 && cp <= 0x1FAFF)
                || (cp >= 0x20000 && cp <= 0x3FFFD);
        return wide ? 2 : 1;
    }

    public static String padRight(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        for (int i = displayWidth(s); i < width; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public static String cut(String s, int width) {
        if (displayWidth(s) <= width) {
            return s;
        }
        if (width < 2) {
            return ".".repeat(Math.max(0, width));
        }
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int cw = widthOf(cp);
            if (w + cw > width - 2) {
                break;
            }
            sb.appendCodePoint(cp);
            w += cw;
            i += Character.charCount(cp);
        }
        return sb.append("..").toString();
    }

    public static String center(String s, int width) {
        int total = Math.max(0, width - displayWidth(s));
        int left = total / 2;
        return " ".repeat(left) + s + " ".repeat(total - left);
    }
}
