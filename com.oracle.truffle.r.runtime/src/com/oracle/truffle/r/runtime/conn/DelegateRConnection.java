package com.oracle.truffle.r.runtime.conn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;

import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.BaseRConnection;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;
import com.sun.istack.internal.NotNull;

import sun.nio.cs.StreamDecoder;

abstract class DelegateRConnection extends RConnection {
    protected BaseRConnection base;

    DelegateRConnection(@NotNull BaseRConnection base) {
        this.base = base;
    }

    @Override
    public int getDescriptor() {
        return base.getDescriptor();
    }

    @Override
    public boolean isTextMode() {
        return base.isTextMode();
    }

    @Override
    public boolean isOpen() {
        return base.isOpen();
    }

    @Override
    public RConnection forceOpen(String modeString) throws IOException {
        return base.forceOpen(modeString);
    }

    @Override
    protected long seekInternal(long offset, SeekMode seekMode, SeekRWMode seekRWMode) throws IOException {
        if (!isSeekable()) {
            throw RError.error(RError.SHOW_CALLER, RError.Message.SEEK_NOT_ENABLED);
        }
        throw RInternalError.shouldNotReachHere("seek has not been implemented for this connection");
    }

    /**
     * {@code readLines} from an {@link InputStream}. It would be convenient to use a
     * {@link BufferedReader} but mixing binary and text operations, which is a requirement, would
     * then be difficult.
     *
     * @param warn Specifies if warnings should be output.
     * @param skipNul Specifies if the null character should be ignored.
     */
    protected String[] readLinesHelper(int n, boolean warn, boolean skipNul) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        int totalRead = 0;
        byte[] buffer = new byte[64];
        int pushBack = 0;
        boolean nullRead = false;
        while (true) {
            int ch;
            if (pushBack != 0) {
                ch = pushBack;
                pushBack = 0;
            } else {
                ch = getc();
            }
            boolean lineEnd = false;
            if (ch < 0) {
                if (totalRead > 0) {
                    /*
                     * GnuR says if non-blocking and in text mode, silently push back incomplete
                     * lines, otherwise keep data and output warning.
                     */
                    final String incompleteFinalLine = new String(buffer, 0, totalRead, base.getEncoding());
                    if (!base.isBlocking() && base.isTextMode()) {
                        base.pushBack(RDataFactory.createStringVector(incompleteFinalLine), false);
                    } else {
                        lines.add(incompleteFinalLine);
                        if (warn) {
                            RError.warning(RError.SHOW_CALLER, RError.Message.INCOMPLETE_FINAL_LINE, base.getSummaryDescription());
                        }
                    }
                }
                break;
            }
            if (ch == '\n') {
                lineEnd = true;
            } else if (ch == '\r') {
                lineEnd = true;
                ch = getc();
                if (ch == '\n') {
                    // swallow the trailing lf
                } else {
                    pushBack = ch;
                }
            } else if (ch == 0) {
                nullRead = true;
                if (warn && !skipNul) {
                    RError.warning(RError.SHOW_CALLER, RError.Message.LINE_CONTAINS_EMBEDDED_NULLS, lines.size() + 1);
                }
            }
            if (lineEnd) {
                lines.add(new String(buffer, 0, totalRead, base.getEncoding()));
                if (n > 0 && lines.size() == n) {
                    break;
                }
                totalRead = 0;
                nullRead = false;
            } else {
                if (!nullRead) {
                    buffer = DelegateRConnection.checkBuffer(buffer, totalRead);
                    buffer[totalRead++] = (byte) (ch & 0xFF);
                }
                if (skipNul) {
                    nullRead = false;
                }
            }
        }
        String[] result = new String[lines.size()];
        lines.toArray(result);
        return result;
    }

    public static void writeStringHelper(OutputStream out, String s, boolean nl, Charset encoding) throws IOException {
        out.write(s.getBytes(encoding));
        if (nl) {
            out.write(System.lineSeparator().getBytes(encoding));
        }
    }

    public static void writeStringHelper(WritableByteChannel out, String s, boolean nl, Charset encoding) throws IOException {
        final byte[] bytes = s.getBytes(encoding);
        final byte[] lineSepBytes = nl ? System.lineSeparator().getBytes(encoding) : null;

        ByteBuffer buf = ByteBuffer.allocate(bytes.length + (nl ? lineSepBytes.length : 0));
        buf.put(bytes);
        if (nl) {
            buf.put(lineSepBytes);
        }

        buf.rewind();
        out.write(buf);
    }

    /**
     * Writes characters in binary mode (without any re-encoding) to the provided channel.
     *
     * @param out The output stream (must not be {@code null}.
     * @param s The character string to write (must not be {@code null}).
     * @param pad The number of null characters to append to the characters.
     * @param eos The end-of-string terminator (may be {@code null}).
     * @throws IOException
     */
    public static void writeCharHelper(OutputStream out, String s, int pad, String eos) throws IOException {

        out.write(s.getBytes());
        if (pad > 0) {
            for (int i = 0; i < pad; i++) {
                out.write(0);
            }
        }
        if (eos != null) {
            if (eos.length() > 0) {
                out.write(eos.getBytes());
            }
            // function writeChar is defined to append the null character if eos != null
            out.write(0);
        }
    }

    /**
     * Writes characters in binary mode (without any re-encoding) to the provided channel.
     *
     * @param channel The writable byte channel to write to (must not be {@code null}).
     * @param s The character string to write (must not be {@code null}).
     * @param pad The number of null characters to append to the characters.
     * @param eos The end-of-string terminator (may be {@code null}).
     * @throws IOException
     */
    public static void writeCharHelper(@NotNull WritableByteChannel channel, @NotNull String s, int pad, String eos) throws IOException {

        final byte[] bytes = s.getBytes();
        final byte[] eosBytes = eos != null ? eos.getBytes() : null;

        final int bufLen = bytes.length + (pad > 0 ? pad : 0) + (eos != null ? eosBytes.length + 1 : 0);
        assert bufLen >= s.length();
        ByteBuffer buf = ByteBuffer.allocate(bufLen);
        buf.put(bytes);
        if (pad > 0) {
            for (int i = 0; i < pad; i++) {
                buf.put((byte) 0);
            }
        }
        if (eos != null) {
            if (eos.length() > 0) {
                buf.put(eos.getBytes());
            }
            // function writeChar is defined to append the null character if eos != null
            buf.put((byte) 0);
        }
        buf.rewind();
        channel.write(buf);
    }

    @Deprecated
    public static void writeBinHelper(ByteBuffer buffer, OutputStream outputStream) throws IOException {
        int n = buffer.remaining();
        byte[] b = new byte[n];
        buffer.get(b);
        outputStream.write(b);
    }

    /**
     * Reads null-terminated character strings from a {@link ReadableByteChannel}.
     */
    public static byte[] readBinCharsHelper(ReadableByteChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        int numRead = channel.read(buf);
        if (numRead <= 0) {
            return null;
        }
        int totalRead = 0;
        byte[] buffer = new byte[64];
        while (true) {
            buffer = DelegateRConnection.checkBuffer(buffer, totalRead);
            buffer[totalRead++] = (byte) (buf.get() & 0xFF);
            if (numRead == 0) {
                break;
            }
            buf.clear();
            numRead = channel.read(buf);
        }
        return buffer;
    }

    /**
     * Reads null-terminated character strings from a {@link InputStream}.
     */
    public static byte[] readBinCharsHelper(InputStream in) throws IOException {
        int ch = in.read();
        if (ch < 0) {
            return null;
        }
        int totalRead = 0;
        byte[] buffer = new byte[64];
        while (true) {
            buffer = DelegateRConnection.checkBuffer(buffer, totalRead);
            buffer[totalRead++] = (byte) (ch & 0xFF);
            if (ch == 0) {
                break;
            }
            ch = in.read();
        }
        return buffer;
    }

    public static int readBinHelper(ByteBuffer buffer, InputStream inputStream) throws IOException {
        int bytesToRead = buffer.remaining();
        byte[] b = new byte[bytesToRead];
        int totalRead = 0;
        int thisRead = 0;
        while ((totalRead < bytesToRead) && ((thisRead = inputStream.read(b, totalRead, bytesToRead - totalRead)) > 0)) {
            totalRead += thisRead;
        }
        buffer.put(b, 0, totalRead);
        return totalRead;
    }

    /**
     * Reads a specified amount of characters.
     *
     * @param nchars Number of characters to read.
     * @param in The encoded byte stream.
     * @return The read string.
     * @throws IOException
     */
    public static String readCharHelper(int nchars, Reader in) throws IOException {
        char[] chars = new char[nchars];
        in.read(chars);
        int j = 0;
        for (; j < chars.length; j++) {
            // strings end at 0
            if (chars[j] == 0) {
                break;
            }
        }

        return new String(chars, 0, j);
    }

    /**
     * Reads a specified amount of bytes.
     *
     * @param nchars The number of bytes to read.
     * @param in The input stream.
     * @return The read string.
     * @throws IOException
     */
    public static String readCharHelper(int nchars, InputStream in) throws IOException {
        byte[] buf = new byte[nchars];
        in.read(buf);
        int j = 0;
        for (; j < buf.length; j++) {
            // strings end at 0
            if (buf[j] == 0) {
                break;
            }
        }

        return new String(buf, 0, j);
    }

    public static String readCharHelper(int nchars, ReadableByteChannel channel, boolean useBytes) throws IOException {
        if (useBytes) {
            ByteBuffer buf = ByteBuffer.allocate(nchars);
            channel.read(buf);
            int j = 0;
            for (; j < buf.position(); j++) {
                // strings end at 0
                if (buf.get(j) == 0) {
                    break;
                }
            }

            return new String(buf.array(), 0, j);
        } else {
            // we need a decoder
            StreamDecoder decoder = StreamDecoder.forDecoder(channel, Charset.defaultCharset().newDecoder(), nchars);
            char[] chars = new char[nchars];
            decoder.read(chars);
            int j = 0;
            for (; j < chars.length; j++) {
                // strings end at 0
                if (chars[j] == 0) {
                    break;
                }
            }

            return new String(chars, 0, j);
        }
    }

    /**
     * Implements standard seeking behavior.
     */
    public static long seek(SeekableByteChannel channel, long offset, SeekMode seekMode, @SuppressWarnings("unused") SeekRWMode seekRWMode) throws IOException {
        long position = channel.position();
        switch (seekMode) {
            case ENQUIRE:
                break;
            case CURRENT:
                if (offset != 0) {
                    channel.position(position + offset);
                }
                break;
            case START:
                channel.position(offset);
                break;
            case END:
                throw RInternalError.unimplemented();

        }
        return position;
    }

    /**
     * Enlarges the buffer if necessary.
     */
    static byte[] checkBuffer(byte[] buffer, int n) {
        if (n > buffer.length - 1) {
            byte[] newBuffer = new byte[buffer.length + buffer.length / 2];
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
            return newBuffer;
        } else {
            return buffer;
        }
    }

    @SuppressWarnings("unused")
    public static String[] readLinesNonBlockHelper(BaseRConnection conn, ReadableByteChannel channel, int n, boolean warn, boolean skipNul, String summaryDescription, Charset encoding) {

        throw RInternalError.unimplemented();
    }

    public static void writeLinesHelper(WritableByteChannel out, RAbstractStringVector lines, String sep, Charset encoding) throws IOException {
        for (int i = 0; i < lines.getLength(); i++) {
            final String line = lines.getDataAt(i);
            DelegateRConnection.writeStringHelper(out, line, false, encoding);
            DelegateRConnection.writeStringHelper(out, sep, false, encoding);
        }
    }

    public static void writeLinesHelper(OutputStream out, RAbstractStringVector lines, String sep, Charset encoding) throws IOException {
        for (int i = 0; i < lines.getLength(); i++) {
            final String line = lines.getDataAt(i);
            DelegateRConnection.writeStringHelper(out, line, false, encoding);
            DelegateRConnection.writeStringHelper(out, sep, false, encoding);
        }
    }

}
