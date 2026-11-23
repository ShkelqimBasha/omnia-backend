package com.omnia.backend.service.impl;

/**
 * Cleans a chat message before it is stored. Messages are plain text: clients
 * always render them as text (never HTML), and the server removes characters
 * that could hide or reorder text.
 */
public final class ChatText {

    public static final int MAX_LENGTH = 2000;
    public static final int PREVIEW_LENGTH = 140;

    private ChatText() {
    }

    /** Returns the cleaned text, or throws when it is empty or longer than {@link #MAX_LENGTH} characters. */
    public static String clean(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Shkruani një mesazh.");
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (cp == '\n') {
                out.append('\n');
            } else if (cp == '\t') {
                out.append(' ');
            } else if (!hidden(cp)) {
                out.appendCodePoint(cp);
            }
        });
        String cleaned = out.toString()
                .replaceAll("[ ]+\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Shkruani një mesazh.");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("Mesazhi mund të ketë deri në " + MAX_LENGTH + " karaktere.");
        }
        return cleaned;
    }

    /** One line for conversation lists. */
    public static String preview(String text) {
        String line = text.replaceAll("\\s+", " ").strip();
        if (line.codePointCount(0, line.length()) <= PREVIEW_LENGTH) {
            return line;
        }
        int end = line.offsetByCodePoints(0, PREVIEW_LENGTH - 1);
        return line.substring(0, end) + "…";
    }

    /** Control characters, zero-width characters and bidirectional overrides. */
    static boolean hidden(int cp) {
        if (Character.isISOControl(cp)) return true;
        if (cp >= 0x200B && cp <= 0x200F && cp != 0x200D) return true;   // zero width (not the emoji joiner), LRM/RLM
        if (cp >= 0x202A && cp <= 0x202E) return true;   // bidi embedding/override
        if (cp >= 0x2066 && cp <= 0x2069) return true;   // bidi isolates
        if (cp == 0xFEFF || cp == 0x2060 || cp == 0x00AD) return true;
        int type = Character.getType(cp);
        return type == Character.PRIVATE_USE || type == Character.SURROGATE;
    }
}
