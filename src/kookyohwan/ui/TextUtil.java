package kookyohwan.ui;

/** 한글은 터미널에서 2칸을 차지하므로 String.format의 %-10s 대신 표시 폭 기준으로 맞춘다. */
public final class TextUtil {
    private TextUtil() {
    }

    public static int displayWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            boolean wide = (c >= '가' && c <= '힣')   // 한글 음절
                    || (c >= '一' && c <= '鿿');      // 한자
            w += wide ? 2 : 1;
        }
        return w;
    }

    public static String padRight(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        for (int i = displayWidth(s); i < width; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** 폭을 넘치면 (width-2)칸에서 자르고 ".." (격자 칸용) */
    public static String cut(String s, int width) {
        if (displayWidth(s) <= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (char c : s.toCharArray()) {
            int cw = displayWidth(String.valueOf(c));
            if (w + cw > width - 2) {
                break;
            }
            sb.append(c);
            w += cw;
        }
        return sb.append("..").toString();
    }

    /** 가운데 정렬 (격자 머리글의 요일) */
    public static String center(String s, int width) {
        int total = Math.max(0, width - displayWidth(s));
        int left = total / 2;
        return " ".repeat(left) + s + " ".repeat(total - left);
    }
}
