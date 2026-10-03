package com.example.api.notes;

/** Fixed business codes contain no submitted values or persistence diagnostics. */
public final class NotesFailure extends RuntimeException {
    private final String code;
    NotesFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
