package net.sf.chttpc;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class HeaderPair extends AbstractList<String> implements Map.Entry<String, HeaderPair> {
    private final String key;
    private final int from;

    private Object value;
    private int to;

    public HeaderPair(String key, String value) {
        this.key = key;
        this.value = value;
        this.from = 0;
        this.to = 1;
    }

    public HeaderPair(String key, String[] value, int from, int to) {
        this.key = key;
        this.value = value;
        this.from = from;
        this.to = to;
    }

    @Override
    public String get(int index) {
        if (index >= to) {
            throw new IndexOutOfBoundsException();
        }

        if (value.getClass() == String.class) {
            return (String) value;
        }

        String[] array = (String[]) value;

        return array[from + index];
    }

    void append(String element) {
        int size = size();

        if (size == 1) {
            String one = (String) value;

            value = new String[] { one, element };
        } else {
            String[] store = (String[]) value;

            if (size >= store.length) {
                value = store = Arrays.copyOf(store, size + 1);
            }

            store[to] = element;
        }

        ++to;
    }

    @Override
    public int size() {
        return to - from;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public HeaderPair getValue() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;

        if (o == this) return true;

        if (o.getClass() == HeaderPair.class) {
            HeaderPair e = (HeaderPair) o;

            return key.equalsIgnoreCase(e.getKey()) && super.equals(e);
        }

        if (o instanceof Map.Entry) {
            Map.Entry e = (Map.Entry) o;

            return key.equals(e.getKey()) && super.equals(e.getValue());
        }

        return o instanceof List && super.equals(o);
    }

    @Override
    public String toString() {
        return key + " -> " + (value instanceof String ? (String) value : Arrays.toString((String[]) value));
    }

    @Override
    public HeaderPair setValue(HeaderPair value) {
        throw new UnsupportedOperationException();
    }
}
