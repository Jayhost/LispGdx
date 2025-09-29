package io.github.jayhost;

public class BinaryExpr extends Expr {
    public final Expr left;
    public final Token operator;
    public final Expr right;

    public BinaryExpr(Expr left, Token operator, Expr right, int line) {
        super(line);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitBinaryExpr(this);
    }
}