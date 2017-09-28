package net.sf.chttpc;

import com.google.common.truth.Correspondence;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public final class EqualsIgnoreCase extends Correspondence<Map.Entry<String, List<String>>, Map.Entry<String, List<String>>> {
    public static final EqualsIgnoreCase INSTANCE = new EqualsIgnoreCase();

    @Override
    public boolean compare(@Nullable Map.Entry<String, List<String>> e1, @Nullable Map.Entry<String, List<String>> e2) {
        assert e1 != null && e2 != null;

        return e1.getKey().equalsIgnoreCase(e2.getKey()) && e1.getValue().equals(e2.getValue());
    }

    @Override
    public String toString() {
        return "is equal (ignoring case of keys) to";
    }
}
