package com.cometinha.touchdrop;

import java.io.*;
import java.security.*;

/** Bounded streaming protocol. Android-free so it can be exercised on the JVM. */
public final class Wire {
    public static final long MAX_BYTES = 25L * 1024 * 1024;
    public static final int MAGIC = 0x54443031;
    private Wire() {}
    public static void checkSize(long size) throws IOException {
        if (size <= 0 || size > MAX_BYTES) throw new IOException("Escolha uma foto de até 25 MB.");
    }
    public static String hash(File f) throws IOException {
        MessageDigest md = digest();
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[32768]; int n;
            while ((n = in.read(b)) != -1) md.update(b, 0, n);
        }
        return hex(md.digest());
    }
    public static void copyExact(InputStream in, OutputStream out, long size, Progress progress) throws IOException {
        checkSize(size); byte[] b = new byte[32768]; long done = 0;
        while (done < size) {
            int n = in.read(b, 0, (int)Math.min(b.length, size - done));
            if (n < 0) throw new EOFException("A conexão terminou antes de enviar a foto inteira.");
            if (n == 0) continue;
            out.write(b, 0, n); done += n;
            if (progress != null) progress.update(done, size);
        }
    }
    public static void receive(InputStream in, File file, long size, String expected, Progress progress) throws IOException {
        if (!expected.matches("[a-f0-9]{64}")) throw new IOException("Identificação da foto inválida.");
        boolean complete = false;
        try {
            try (OutputStream out = new FileOutputStream(file)) { copyExact(in, out, size, progress); }
            if (!MessageDigest.isEqual(hash(file).getBytes(java.nio.charset.StandardCharsets.US_ASCII), expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                throw new IOException("A foto chegou incompleta ou alterada. Tente novamente.");
            complete = true;
        } finally { if (!complete) file.delete(); }
    }
    public static boolean tokenMatches(String actual, String expected) {
        return actual != null && expected != null && actual.length() == 32 && expected.length() == 32
            && MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII), expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return s.toString();
    }
    public interface Progress { void update(long current, long total); }
}
