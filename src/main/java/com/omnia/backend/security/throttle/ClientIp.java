package com.omnia.backend.security.throttle;

import jakarta.servlet.http.HttpServletRequest;

/** The caller's IP. Behind Caddy on the same host, it is the last X-Forwarded-For entry. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isLocal(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                String last = parts[parts.length - 1].trim();
                if (!last.isEmpty()) return last;
            }
        }
        return remote == null ? "unknown" : remote;
    }

    static boolean isLocal(String address) {
        return address == null
                || address.equals("127.0.0.1")
                || address.equals("0:0:0:0:0:0:0:1")
                || address.equals("::1")
                || address.startsWith("172.")
                || address.startsWith("10.")
                || address.startsWith("192.168.");
    }
}
