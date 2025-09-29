package io.github.jayhost;

public class DynamicClassLoader extends ClassLoader {
    public Class<?> defineClass(String name, byte[] b) {
        // The defineClass method is inherited from ClassLoader and does the magic.
        return defineClass(name, b, 0, b.length);
    }
}
