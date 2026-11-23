package com.omnia.backend.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTextTest {

    @Test
    void keepsEmojiAndAlbanianLettersButDropsHiddenCharacters() {
        assertEquals("Çmimi? 👨‍👩‍👧 ë", ChatText.clean("\u200BÇmimi?\u202E 👨‍👩‍👧 ë\u2066"));
    }

    @Test
    void countsCharactersNotUtf16Units() {
        String emoji = "😀".repeat(2000);
        assertEquals(emoji, ChatText.clean(emoji));
        assertThrows(IllegalArgumentException.class, () -> ChatText.clean(emoji + "😀"));
    }

    @Test
    void previewIsOneShortLine() {
        assertEquals("a b", ChatText.preview("a\n\nb"));
        String p = ChatText.preview("x".repeat(300));
        assertEquals(ChatText.PREVIEW_LENGTH, p.length());
        assertTrue(p.endsWith("…"));
    }

    @Test
    void buyerNameShowsInitialOnly() {
        assertEquals("Ana K.", ChatService.buyerName("Ana", "kola"));
        assertEquals("Ana", ChatService.buyerName(" Ana ", null));
        assertEquals("Blerës", ChatService.buyerName(null, " "));
    }
}
