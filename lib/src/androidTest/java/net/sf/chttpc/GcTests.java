package net.sf.chttpc;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class GcTests {
    private static final int RUNS = 1000;

    private Cleaner cleaner;

    private ReferenceQueue<Object> queue;

    private final AtomicInteger counter = new AtomicInteger();

    @Before
    public void start() {
        queue = new ReferenceQueue<>();

        cleaner = new Cleaner(queue, counter);

        cleaner.start();
    }

    @Test
    public void coreCollected() throws InterruptedException {
        counter.set(0);

        makeGarbage();

        final Runtime r = Runtime.getRuntime();
        r.gc();

        Thread.sleep(400);

        r.runFinalization();

        Thread.sleep(400);

        r.gc();

        Thread.sleep(200);

        assertEquals(RUNS, counter.get());
    }

    @After
    public void cleanup() {
        cleaner.interrupt();
    }

    private void makeGarbage() {
        for (int i = 0; i < RUNS; ++i) {
            CurlHttp.create(queue);
        }
    }

    private final class Cleaner extends Thread {
        private final ReferenceQueue<?> queue;
        private final AtomicInteger counter;

        private Cleaner(ReferenceQueue<?> queue, AtomicInteger counter) {
            this.queue = queue;
            this.counter = counter;
        }

        @Override
        public void run() {
            do {
                try {
                    Reference<?> c = queue.remove();
                    c.clear();
                    counter.incrementAndGet();
                } catch (InterruptedException done) {
                    return;
                } catch (Throwable t) {
                    throw new AssertionError(t);
                }
            } while (!interrupted());
        }
    }
}
