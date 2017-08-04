package net.sf.xfd.curl;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import java.util.Arrays;

/**
 * A {@link StringBuilder}-like type for constructing urls. This class does not perform
 * any validation, or encoding so be careful to construct valid urls yourself.
 *
 * While the input data is stored in character {@link #buffer}, the final url supplied to
 * {@link CurlHttp} is expected to contain only 7-bit ASCII symbols (all codepoints are converted
 * to 8-bit chars by truncation before feeding to native library). If you want to use so-called
 * "Unicode domain names", use {@link java.net.IDN}, {@link android.icu.text.IDNA} or similar
 * facilities to convert domain names from international format to Punycode. If you expect to
 * receive some parts of url or entire urls from outside, it is recommended, that you validate
 * and transform those to canonical form, using specialized classes, such as {@link java.net.URL}.
 */
public class MutableUrl implements CharSequence {
    private static final char[] EMPTY = new char[0];

    private final static float GROWTH_RATIO = 1.5f;

    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - /* aligned array header + slack */32;
    private final static int MIN_GROW_COUNT = 10;

    public char[] buffer = EMPTY;
    public int length;

    public MutableUrl() {}

    public MutableUrl(int size) {
        buffer = new char[size];
    }

    public void append(char ch) {
        ensureBufferSpace(1);
        buffer[length++] = ch;
    }

    public void append(long number) {
        if (number == Long.MIN_VALUE) {
            append("-2147483648");
        }
        int appendedLength = (number < 0) ? stringSize(-number) + 1 : stringSize(number);
        ensureBufferSpace(appendedLength);
        IntParser.getNumberChars(number, appendedLength, buffer);
        length += appendedLength;
    }

    public void append(String string) {
        append(string, 0, string.length());
    }

    public void append(String string, int off, int count) {
        ensureBufferSpace(count);
        string.getChars(off, count, buffer, length);
        length += count;
    }

    public void append(CharSequence chars) {
        append(chars, 0, chars.length());
    }

    public void append(CharSequence chars, int off, int count) {
        ensureBufferSpace(count);
        for (int i = 0; i < count; ++i) {
            buffer[length + i] = chars.charAt(off + i);
        }
        length += count;
    }

    public void append(char[] array) {
        append(array, 0, array.length);
    }

    public void append(char[] array, int off, int count) {
        ensureBufferSpace(count);
        System.arraycopy(array, off, buffer, length, count);
        length += count;
    }

    public void insert(int dstOffset, String string) {
        if (dstOffset < 0 || dstOffset >= length) throw new IndexOutOfBoundsException();

        insertNoCheck(dstOffset, string, 0, string.length());
    }

    public void insert(int dstOffset, String string, int off, int count) {
        if (dstOffset < 0 || dstOffset >= length
                || off < 0 || count < 0) throw new IndexOutOfBoundsException();

        insertNoCheck(dstOffset, string, off, count);
    }

    protected void insertNoCheck(int dstOffset, String string, int off, int count) {
        final char[] dest;
        if (length + count > buffer.length) {
            dest = new char[grow(buffer.length, length, count)];
            System.arraycopy(buffer, 0, dest, 0, dstOffset);
        } else {
            dest = buffer;
        }

        System.arraycopy(buffer, dstOffset, dest, dstOffset + count, length - dstOffset);
        string.getChars(off, count, dest, dstOffset);
        length += count;
    }

    public void insert(int dstOffset, CharSequence chars) {
        if (dstOffset < 0 || dstOffset >= length) throw new IndexOutOfBoundsException();

        insert(dstOffset, chars, 0, chars.length());
    }

    public void insert(int dstOffset, CharSequence chars, int off, int count) {
        if (dstOffset < 0 || dstOffset >= length
                || off < 0 || count < 0) throw new IndexOutOfBoundsException();

        insertNoCheck(dstOffset, chars, off, count);
    }

    protected void insertNoCheck(int dstOffset, CharSequence chars, int off, int count) {
        final char[] dest;
        if (length + count > buffer.length) {
            dest = new char[grow(buffer.length, length, count)];
            System.arraycopy(buffer, 0, dest, 0, dstOffset);
        } else {
            dest = buffer;
        }

        System.arraycopy(buffer, dstOffset, dest, dstOffset + count, length - dstOffset);
        for (int i = 0; i < count; ++i) {
            buffer[length + i] = chars.charAt(off + i);
        }
        length += count;
    }

    public void setLength(int newLength) {
        if (newLength < 0) throw new IndexOutOfBoundsException();

        if (newLength > buffer.length) {
            buffer = Arrays.copyOf(buffer, newLength);
        } else if (newLength < length) {
            Arrays.fill(buffer, newLength, length, '\0');
        }

        length = newLength;
    }

    public void clear() {
        setLength(0);
    }

    public void release() {
        buffer = EMPTY;
        length = 0;
    }

    @Override
    @CheckResult
    public int length() {
        return length;
    }

    @Override
    @CheckResult
    public char charAt(int index) {
        if (index >= length) throw new IndexOutOfBoundsException();

        return buffer[index];
    }

    @Override
    @CheckResult
    public CharSequence subSequence(int start, int end) {
        if (start >= length || end >= length) throw new IndexOutOfBoundsException();

        return new String(buffer, start, end);
    }

    protected void ensureBufferSpace(int expectedAdditions) {
        final int bufferLen = (buffer == null ? 0 : buffer.length);
        if (length + expectedAdditions > bufferLen) {
            final int newSize = grow(bufferLen, length, expectedAdditions);

            this.buffer = Arrays.copyOf(buffer, newSize);
        }
    }

    protected int grow(int currentBufferLength, int elementsCount, int expectedAdditions) {
        long growBy = (long) ((long) currentBufferLength * GROWTH_RATIO);
        growBy = Math.max(growBy, MIN_GROW_COUNT);
        growBy = Math.min(growBy, MAX_ARRAY_LENGTH);
        long growTo = Math.min(MAX_ARRAY_LENGTH, growBy + currentBufferLength);
        long newSize = Math.max((long) elementsCount + expectedAdditions, growTo);

        if (newSize > MAX_ARRAY_LENGTH) {
            throw new RuntimeException("Java array size exceeded");
        }

        return (int) newSize;
    }

    @NonNull
    @Override
    public String toString() {
        return new String(buffer, 0, length);
    }

    private static int stringSize(long x) {
        long p = 10;
        for (int i=1; i<19; i++) {
            if (x < p)
                return i;
            p = 10*p;
        }
        return 19;
    }

    private static class IntParser {
        private IntParser() {}

        private final static char [] DigitTens = {
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
                '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
                '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
                '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
                '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
                '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
                '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
                '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
                '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
                '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
        } ;

        private final static char [] DigitOnes = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        };

        private final static char[] digits = {
                '0' , '1' , '2' , '3' , '4' , '5' ,
                '6' , '7' , '8' , '9' , 'a' , 'b' ,
                'c' , 'd' , 'e' , 'f' , 'g' , 'h' ,
                'i' , 'j' , 'k' , 'l' , 'm' , 'n' ,
                'o' , 'p' , 'q' , 'r' , 's' , 't' ,
                'u' , 'v' , 'w' , 'x' , 'y' , 'z'
        };

        private static void getNumberChars(long number, int index, char[] buf) {
            long q;
            int r;
            int charPos = index;
            char sign = 0;

            if (number < 0) {
                sign = '-';
                number = -number;
            }

            // Get 2 digits/iteration using longs until quotient fits into an int
            while (number > Integer.MAX_VALUE) {
                q = number / 100;
                // really: r = i - (q * 100);
                r = (int)(number - ((q << 6) + (q << 5) + (q << 2)));
                number = q;
                buf[--charPos] = DigitOnes[r];
                buf[--charPos] = DigitTens[r];
            }

            // Get 2 digits/iteration using ints
            int q2;
            int i2 = (int) number;
            while (i2 >= 65536) {
                q2 = i2 / 100;
                // really: r = i2 - (q * 100);
                r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
                i2 = q2;
                buf[--charPos] = DigitOnes[r];
                buf[--charPos] = DigitTens[r];
            }

            // Fall thru to fast mode for smaller numbers
            // assert(i2 <= 65536, i2);
            for (;;) {
                q2 = (i2 * 52429) >>> (16+3);
                r = i2 - ((q2 << 3) + (q2 << 1));  // r = i2-(q2*10) ...
                buf[--charPos] = digits[r];
                i2 = q2;
                if (i2 == 0) break;
            }
            if (sign != 0) {
                buf[--charPos] = sign;
            }
        }
    }
}
