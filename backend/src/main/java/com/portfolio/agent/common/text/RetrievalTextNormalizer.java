package com.portfolio.agent.common.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RetrievalTextNormalizer {

    public static final String VERSION = "nfkc-bigram-v1";

    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "是", "在", "这个", "项目", "一个", "以及");

    public List<String> terms(String source) {
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        List<Integer> han = new ArrayList<>();
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (isLatinOrNumber(codePoint)) {
                flushHan(han, terms);
                latin.appendCodePoint(codePoint);
            } else if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushLatin(latin, terms);
                han.add(codePoint);
            } else {
                flushLatin(latin, terms);
                flushHan(han, terms);
            }
            offset += Character.charCount(codePoint);
        }
        flushLatin(latin, terms);
        flushHan(han, terms);
        terms.removeIf(STOP_WORDS::contains);
        return List.copyOf(terms);
    }

    private boolean isLatinOrNumber(int codePoint) {
        return Character.isDigit(codePoint)
                || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
    }

    private void flushLatin(StringBuilder latin, List<String> terms) {
        if (!latin.isEmpty()) {
            terms.add(latin.toString());
            latin.setLength(0);
        }
    }

    private void flushHan(List<Integer> han, List<String> terms) {
        if (han.size() == 1) {
            terms.add(new String(Character.toChars(han.get(0))));
        } else {
            for (int index = 0; index + 1 < han.size(); index++) {
                terms.add(new String(Character.toChars(han.get(index)))
                        + new String(Character.toChars(han.get(index + 1))));
            }
        }
        han.clear();
    }
}
