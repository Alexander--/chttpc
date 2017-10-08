package net.sf.chttpc.test;

import com.google.common.truth.Correspondence;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import static com.google.common.truth.Truth.assertThat;

public final class EqualsIgnoreCase extends Correspondence<Map.Entry<String, List<String>>, Map.Entry<String, List<String>>> {
    public static final EqualsIgnoreCase INSTANCE = new EqualsIgnoreCase();

    @Override
    public boolean compare(@Nullable Map.Entry<String, List<String>> e1, @Nullable Map.Entry<String, List<String>> e2) {
        assert e1 != null && e2 != null;

        try {
            return e1.getKey().equalsIgnoreCase(e2.getKey()) && e1.getValue().equals(e2.getValue());
        } catch (Throwable t) {
            t.printStackTrace();

            throw t;
        }
    }

    @Override
    public String toString() {
        return "is equal (ignoring case of keys) to";
    }
}
