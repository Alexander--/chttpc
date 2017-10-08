package net.sf.chttpc.test;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;

import java.io.InputStream;

import javax.annotation.Nullable;

import static net.sf.chttpc.test.InputStreamEquality.INSTANCE;

public final class InputStreamSubject extends Subject<InputStreamSubject, InputStream> {
    public InputStreamSubject(FailureStrategy failureStrategy, @Nullable InputStream actual) {
        super(failureStrategy, actual);
    }

    public void hasSameContentsAs(InputStream other) {
        if (!INSTANCE.compare(this.actual(), other)) {
            fail(INSTANCE.toString());
        }
    }
}
