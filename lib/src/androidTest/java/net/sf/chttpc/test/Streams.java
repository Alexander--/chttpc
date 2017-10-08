package net.sf.chttpc.test;

import android.support.annotation.NonNull;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;

import java.io.InputStream;

public final class Streams extends SubjectFactory<InputStreamSubject, InputStream> {
    @Override
    public InputStreamSubject getSubject(@NonNull FailureStrategy failureStrategy, @NonNull InputStream inputStream) {
        return new InputStreamSubject(failureStrategy, inputStream);
    }

    public static Streams inputStream() {
        return new Streams();
    }
}
