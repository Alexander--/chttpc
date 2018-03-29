package net.sf.chttpc;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import com.carrotsearch.hppc.CharArrayList;

import java.util.Arrays;

/**
 * A {@link StringBuilder}-like type for constructing urls. This class does not perform
 * any validation or encoding, so be careful to construct valid urls yourself.
 *
 * While the input data is stored in character {@link #buffer}, the final url is supposed
 * to contain only 7-bit ASCII symbols (all codepoints will be converted to 8-bit chars
 * by truncation before feeding to native library). If you want to use so-called
 * "Unicode domain names", use {@link java.net.IDN} or similar facilities
 * to convert domain names from international format to Punycode.
 *
 * <br/>
 *
 * If you expect to receive some parts of url or entire urls from outside,
 * it is recommended, that you validate and transform those to canonical form
 * using specialized classes, such as {@link java.net.URL}.
 */
public class MutableUrl extends CharArrayList implements CharSequence, Cloneable {
    protected MutableUrl() {}

    public void append(char ch) {
        add(ch);
    }

    public void append(@NonNull String string) {
        append(string, 0, string.length());
    }

    public void append(@NonNull String string, int off, int count) {
        ensureBufferSpace(count);
        string.getChars(off, count, buffer, elementsCount);
        elementsCount += count;
    }

    public void append(@NonNull CharSequence chars) {
        append(chars, 0, chars.length());
    }

    public void append(@NonNull CharSequence chars, int off, int count) {
        ensureBufferSpace(count);
        for (int i = 0; i < count; ++i) {
            buffer[elementsCount + i] = chars.charAt(off + i);
        }
        elementsCount += count;
    }

    public void append(@NonNull char[] array) {
        add(array, 0, array.length);
    }

    public void append(@NonNull char[] array, int off, int count) {
        add(array, off, count);
    }

    public void append(long number) {
        if (number == Long.MIN_VALUE) {
            append("-2147483648");
        }
        int appendedLength = (number < 0) ? stringSize(-number) + 1 : stringSize(number);
        ensureBufferSpace(appendedLength);
        IntParser.getNumberChars(number, elementsCount + appendedLength, buffer);
        elementsCount += appendedLength;
    }

    public void setLength(int newLength) {
        if (newLength < 0) throw new IndexOutOfBoundsException();

        if (newLength > buffer.length) {
            buffer = Arrays.copyOf(buffer, newLength);
        } else if (newLength < elementsCount) {
            Arrays.fill(buffer, newLength, elementsCount, '\0');
        }

        elementsCount = newLength;
    }

    @Override
    @CheckResult
    public int length() {
        return elementsCount;
    }

    @Override
    @CheckResult
    public char charAt(int index) {
        if (index >= elementsCount) throw new IndexOutOfBoundsException();

        return buffer[index];
    }

    @Override
    @CheckResult
    public CharSequence subSequence(int start, int end) {
        if (start >= elementsCount || end >= elementsCount) throw new IndexOutOfBoundsException();

        return new String(buffer, start, end);
    }

    @NonNull
    @Override
    public String toString() {
        return new String(buffer, 0, elementsCount);
    }

    @Override
    public MutableUrl clone() {
        return (MutableUrl) super.clone();
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
