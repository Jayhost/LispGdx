package io.github.jayhost;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final String source;
    private int current = 0;
    private int line = 1;

    public Parser(String source) {
        this.source = source;
    }

    /**
     * Parses a script with multiple top-level forms.
     * @return A list of ASTs.
     */
    public List<Expr> parseMultiple() throws ParseException {
        List<Expr> expressions = new ArrayList<>();
        while (!isAtEnd()) {
            skipWhitespace();
            // Stop if we've reached the end after skipping whitespace
            if (isAtEnd()) break;
            expressions.add(parseExpr());
        }
        return expressions;
    }
    
    public Expr parse() throws ParseException {
        skipWhitespace();
        if (isAtEnd()) {
            throw new ParseException("Empty input");
        }
        Expr expr = parseExpr();
        skipWhitespace();
        if (!isAtEnd() && peek() != ';') { // Allow comments at the end
            throw new ParseException("Unexpected characters at end: '" + peek() + "'");
        }
        return expr;
    }

    private Expr parseExpr() throws ParseException {
        skipWhitespace();
        return parsePrimary();
    }

    private Expr parsePrimary() throws ParseException {
        skipWhitespace();
        char c = peek();
        if (c == ';') {
            skipComment();
            return parseExpr(); // Try parsing the next expression
        }
        if (c == '(') {
            return parseList();
        } else if (c == '"') {
            return parseString();
        } else {
            return parseAtom();
        }
    }

    private Expr parseString() throws ParseException {
        int startLine = line;
        consume('"'); // Consume the opening quote
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek() != '"') {
            // Here you could handle escape sequences like \n or \" if you wanted
            sb.append(advance());
        }
        if (isAtEnd()) {
            throw new ParseException("Unterminated string literal.");
        }
        consume('"'); // Consume the closing quote
        return new StringExpr(sb.toString(), startLine);
    }
    
    
    private Expr parseList() throws ParseException {
        int startLine = line;
        consume('(');
        List<Expr> elements = new ArrayList<>();
        while (!isAtEnd() && peek() != ')') {
            elements.add(parseExpr());
            skipWhitespace();
        }
        if (isAtEnd()) {
            throw new ParseException("Unexpected end of input in list");
        }
        consume(')');
        return new ListExpr(elements, startLine);
    }
    
    private Expr parseAtom() throws ParseException {
        int startLine = line;
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && !Character.isWhitespace(peek()) && peek() != '(' && peek() != ')') {
            sb.append(advance());
        }
        String token = sb.toString();
        if (token.isEmpty()) {
            throw new ParseException("Empty token");
        }
        try {
            // Avoid parsing special symbols as numbers
            if(token.equals("+") || token.equals("-")) throw new NumberFormatException();
            return new NumberExpr(Double.parseDouble(token), startLine);
        } catch (NumberFormatException e) {
            return new SymbolExpr(token, startLine);
        }
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char advance() {
        char c = source.charAt(current++);
        if (c == '\n') {
            line++;
        }
        return c;
    }

    private void skipWhitespace() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\r' || c == '\t' || c == '\n') {
                advance();
            } else if (c == ';') { // Handle comments as whitespace
                skipComment();
            }
            else {
                break;
            }
        }
    }
    
    private void skipComment() {
        while (!isAtEnd() && peek() != '\n') {
            advance();
        }
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void consume(char expected) throws ParseException {
        if (isAtEnd() || peek() != expected) {
            throw new ParseException("Expected '" + expected + "' but found '" + peek() + "'");
        }
        advance();
    }
}
