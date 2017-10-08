package net.sf.chttpc.test;

import com.google.common.truth.Correspondence;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class InputStreamEquality extends Correspondence<InputStream, InputStream> {
    public static final InputStreamEquality INSTANCE = new InputStreamEquality();

    @Override
    public boolean compare(InputStream is1, InputStream is2) {
        byte[] buf1 = new byte[32 * 1024];
        byte[] buf2 = new byte[32 * 1024];
        try {
            DataInputStream d2 = new DataInputStream(is2);
            int len;
            while ((len = is1.read(buf1)) > 0) {
                d2.readFully(buf2,0,len);

                if (!Arrays.equals(buf1, buf2)) {
                    return false;
                }
            }
            return d2.read() < 0; // is the end of the second file also.
        } catch(EOFException ioe) {
            return false;
        } catch (IOException ioe) {
            throw new AssertionError(ioe);
        }
    }

    @Override
    public String toString() {
        return "has contents equal to that of";
    }
}
