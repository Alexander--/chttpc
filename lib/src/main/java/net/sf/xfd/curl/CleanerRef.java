package net.sf.xfd.curl;

import java.io.Closeable;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

final class CleanerRef<T> extends PhantomReference<T> implements Closeable {
    private static CleanerRef first = null;

    private CleanerRef next, prev;

    private final long resource;

    private CleanerRef(T scope, long resource, ReferenceQueue<? super T> q) {
        super(scope, q);

        this.resource = resource;
    }

    public static CleanerRef create(Object referent, long resource, ReferenceQueue<Object> q) {
        final CleanerRef ref = new CleanerRef<>(referent, resource, q);

        add(ref);

        return ref;
    }

    private static synchronized void add(CleanerRef cl) {
        if (first != null) {
            cl.next = first;

            first.prev = cl;
        }

        first = cl;
    }

    protected static synchronized boolean remove(CleanerRef cl) {
        // If already removed, do nothing
        if (cl.next == cl) return false;

        cl.clear();

        // Update list
        if (first == cl) {
            if (cl.next != null)
                first = cl.next;
            else
                first = cl.prev;
        }

        if (cl.next != null) cl.next.prev = cl.prev;

        if (cl.prev != null) cl.prev.next = cl.next;

        // Indicate removal by pointing the cleaner to itself

        cl.next = cl;
        cl.prev = cl;

        return true;
    }

    public void close() {
        if (remove(this)) {
            Curl.nativeDispose(resource);
        }
    }
}
