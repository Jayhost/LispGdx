// ========================================================================
// io/github/jayhost/LispCallable.java
//
// A new, unified interface for all executable Lisp objects,
// both functions and closures.
// ========================================================================
package io.github.jayhost;

@FunctionalInterface
public interface LispCallable {
    /**
     * Applies the function or closure to the given arguments.
     * @param args The arguments to the function.
     * @return The result of the function application.
     */
    Object apply(Object... args) throws Exception;
}
