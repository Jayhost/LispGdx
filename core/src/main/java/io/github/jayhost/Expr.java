package io.github.jayhost;

import java.util.List;

public abstract class Expr {
    public final int line;

    protected Expr(int line) {
        this.line = line;
    }

    // The Visitor pattern is not used by the JIT, but it's good practice
    // to keep it if you want to support other backends (like the interpreter).
    public interface Visitor<R> {
        R visitNumberExpr(NumberExpr expr);
        R visitStringExpr(StringExpr expr);
        R visitSymbolExpr(SymbolExpr expr);
        R visitListExpr(ListExpr expr);
        R visitBinaryExpr(BinaryExpr expr);
    }
    public abstract <R> R accept(Visitor<R> visitor);
}

class NumberExpr extends Expr {
    public final double value;
    public NumberExpr(double value, int line) {
        super(line);
        this.value = value;
    }
    public <R> R accept(Visitor<R> visitor) { return visitor.visitNumberExpr(this); }
}

class StringExpr extends Expr {
    public final String value;
    public StringExpr(String value, int line) {
        super(line);
        this.value = value;
    }
    public <R> R accept(Visitor<R> visitor) { return visitor.visitStringExpr(this); }
}

class SymbolExpr extends Expr {
    public final String name;
    public SymbolExpr(String name, int line) {
        super(line);
        this.name = name;
    }
    public <R> R accept(Visitor<R> visitor) { return visitor.visitSymbolExpr(this); }
}

class ListExpr extends Expr {
    public final List<Expr> elements;
    public ListExpr(List<Expr> elements, int line) {
        super(line);
        this.elements = elements;
    }
    public <R> R accept(Visitor<R> visitor) { return visitor.visitListExpr(this); }
}
