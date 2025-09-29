package io.github.jayhost;

public class Token {
    public enum Type {
        PLUS, MINUS, STAR, SLASH,
        GREATER, GREATER_EQUAL,
        LESS, LESS_EQUAL
    }

    public final Type type;
    public final String lexeme;
    public final int line;

    public Token(Type type, String lexeme, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
    }
}
