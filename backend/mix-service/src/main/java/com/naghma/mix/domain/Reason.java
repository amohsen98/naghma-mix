package com.naghma.mix.domain;

import java.util.Map;

/**
 * Why a transition scored as it did. {@code code} and {@code args} let the app word the
 * reason in the user's language; {@code text} is the English fallback.
 */
public record Reason(String code, Map<String, Object> args, String text) {

    public static Reason of(String code, String text, Map<String, Object> args) {
        return new Reason(code, Map.copyOf(args), text);
    }
}
