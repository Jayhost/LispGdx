package io.github.jayhost;

public class Box {
    public Object value;

    public Box(Object value) {
        this.value = value;
    }

    public Object get() {
        return value;
    }

    public void set(Object value) {
        this.value = value;
    }
}
