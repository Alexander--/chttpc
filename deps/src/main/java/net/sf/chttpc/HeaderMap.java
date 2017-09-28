package net.sf.chttpc;

import com.carrotsearch.hppc.AbstractIterator;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HeaderMap extends AbstractMap<String, List<String>> {
    private final HdrMap container;

    private HeaderMap(HdrMap container) {
        this.container = container;
    }

    static HeaderMap create(int size) {
        return new HeaderMap(new HdrMap(size));
    }

    void justPut(String key, List<String> value) {
        container.append(key, value);
    }

    @Override
    public int size() {
        return container.size();
    }

    @Override
    public List<String> get(Object key) {
        return container.get((String) key);
    }

    @Override
    public boolean containsKey(Object key) {
        return container.containsKey((String) key);
    }

    @Override
    public boolean containsValue(Object value) {
        if (value == null) return false;

        final Object[] tab = container.values;

        for (Object v : tab) {
            if (v != null && v.equals(value)) {
                return true;
            }
        }

        return false;
    }

    private Set<Entry<String, List<String>>> entrySet;

    @Override
    public Set<Entry<String, List<String>>> entrySet() {
        if (entrySet != null) {
            return entrySet;
        }

        return entrySet = new AbstractSet<Entry<String, List<String>>>() {
            @Override
            public Iterator<Entry<String, List<String>>> iterator() {
                return new AbstractIterator<Entry<String, List<String>>>() {
                    int pos = -1;

                    @Override
                    protected Entry<String, List<String>> fetch() {
                        Object[] keys = container.keys;

                        String key = null;

                        while (key == null) {
                            ++pos;

                            if (pos == keys.length) {
                                return done();
                            }

                            key = (String) container.keys[pos];
                        }

                        List<String> value = container.indexGet(pos);

                        return new SimpleImmutableEntry<>(key, value);
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
