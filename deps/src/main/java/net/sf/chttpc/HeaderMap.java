package net.sf.chttpc;

import com.carrotsearch.hppc.AbstractIterator;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

final class HeaderMap extends AbstractMap<String, HeaderPair> {
    private final HdrMap container;

    private HeaderMap(HdrMap container) {
        this.container = container;
    }

    static HeaderMap create(int size) {
        return new HeaderMap(new HdrMap(size));
    }

    void append(String key, String value) {
        HeaderPair pair = get(key);

        if (pair == null) {
            container.append(key, new HeaderPair(key, value));
        } else {
            pair.append(value);
        }
    }

    void append(String key, String[] array, int from, int to) {
        int length = to - from;

        HeaderPair value;

        if (length == 1) {
            value = new HeaderPair(key, array[from]);
        } else {
            if (array.length > 80) {
                // that's some serious outlier, let's avoid wasting memory...
                array = Arrays.copyOfRange(array, from, to);
                from = 0;
                to = length;
            }

            value = new HeaderPair(key, array, from, to);
        }

        container.append(key, value);
    }

    @Override
    public int size() {
        return container.size();
    }

    @Override
    public HeaderPair get(Object key) {
        return container.get((String) key);
    }

    @Override
    public boolean containsKey(Object key) {
        return container.containsKey((String) key);
    }

    private Set<Entry<String, HeaderPair>> entrySet;

    @Override
    public Set<Entry<String, HeaderPair>> entrySet() {
        if (entrySet != null) {
            return entrySet;
        }

        return entrySet = new AbstractSet<Entry<String, HeaderPair>>() {
            @Override
            public Iterator<Entry<String, HeaderPair>> iterator() {
                return new AbstractIterator<Entry<String, HeaderPair>>() {
                    int pos = -1;

                    @Override
                    protected Entry<String, HeaderPair> fetch() {
                        Object[] keys = container.values;

                        HeaderPair value = null;

                        while (value == null) {
                            ++pos;

                            if (pos == keys.length) {
                                return done();
                            }

                            value = (HeaderPair) container.values[pos];
                        }

                        return value;
                    }
                };
            }

            @Override
            public boolean contains(Object o) {
                return o instanceof Map.Entry && o.equals(get(((Entry) o).getKey()));
            }

            @Override
            public int size() {
                return container.size();
            }
        };
    }
}
