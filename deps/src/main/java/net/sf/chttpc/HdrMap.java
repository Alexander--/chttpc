package net.sf.chttpc;

import com.carrotsearch.hppc.BitMixer;
import com.carrotsearch.hppc.ObjectObjectHashMap;

final class HdrMap extends ObjectObjectHashMap<String, HeaderPair> {
    HdrMap(int size) {
        super(size);
    }

    void append(String key, HeaderPair value) {
        put(key, value);
    }

    @Override
    protected boolean equals(Object v1, Object v2) {
        if (v1 == v2) {
            return true;
        }

        if (v1 == null) {
            return false;
        }

        if (v1.getClass() != String.class || v2.getClass() != String.class) {
            return v1.equals(v2);
        }

        return ((String) v1).equalsIgnoreCase((String) v2);
    }

    @Override
    protected int hashKey(String key) {
        int h = 0;

        for (int i = 0; i < key.length(); i++) {
            int ch = key.charAt(i);

            if (ch >= 'A' && ch <= 'Z') {
                ch += ('a' - 'A');
            }

            h = 31 * h + ch;
        }

        return BitMixer.mix(h, this.keyMixer);
    }

    @Override
    protected void allocateThenInsertThenRehash(int slot, String pendingKey, HeaderPair pendingValue) {
        throw new AssertionError();
    }
}
