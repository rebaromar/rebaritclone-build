package com.abdulla.clonephone;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned, bounded encrypted records. Independent keys and counters per direction. */
public final class SecureChannel {
    public static final int MAX_FRAME = 1048576;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final byte[] sendKey, receiveKey;
    private long sent, received;
    private final Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
    private final Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");

    public SecureChannel(InputStream in, OutputStream out, byte[] master, boolean server) throws Exception {
        input = new DataInputStream(new BufferedInputStream(in, 524288)); output = new DataOutputStream(new BufferedOutputStream(out, 524288));
        byte[] session = new byte[32];
        if (server) {
            new SecureRandom().nextBytes(session);
            output.writeInt(0x43503034); output.write(session); output.flush();
        } else {
            if (input.readInt() != 0x43503034) throw new IOException("Protocol mismatch");
            input.readFully(session);
        }
        sendKey = derive(master, session, server ? "server" : "client");
        receiveKey = derive(master, session, server ? "client" : "server");
        if (server) {
            if (!MessageDigest.isEqual(read(), "HELLO".getBytes(StandardCharsets.UTF_8))) throw new IOException("Pairing failed");
            write("READY".getBytes(StandardCharsets.UTF_8));
        } else {
            write("HELLO".getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(read(), "READY".getBytes(StandardCharsets.UTF_8))) throw new IOException("Pairing failed");
        }
    }
    private static byte[] derive(byte[] master, byte[] session, String direction) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(master, "HmacSHA256"));
        mac.update(session);
        return mac.doFinal(("rebaritclone-v4-" + direction).getBytes(StandardCharsets.UTF_8));
    }
    private byte[] nonce(long counter) { return ByteBuffer.allocate(12).putInt(0).putLong(counter).array(); }
    public void write(byte[] plain) throws Exception { writeBuffered(plain, plain.length); output.flush(); }
    public void writeBuffered(byte[] plain, int length) throws Exception {
        if (length < 0 || length > plain.length || length > MAX_FRAME || sent == Long.MAX_VALUE) throw new IOException("Frame limit");
        encrypt.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sendKey, "AES"), new GCMParameterSpec(128, nonce(sent++)));
        byte[] encrypted = encrypt.doFinal(plain, 0, length);
        output.writeInt(encrypted.length); output.write(encrypted);
    }
    public byte[] read() throws Exception {
        int size = input.readInt();
        if (size < 16 || size > MAX_FRAME + 16 || received == Long.MAX_VALUE) throw new IOException("Frame limit");
        byte[] encrypted = new byte[size]; input.readFully(encrypted);
        decrypt.init(Cipher.DECRYPT_MODE, new SecretKeySpec(receiveKey, "AES"), new GCMParameterSpec(128, nonce(received++)));
        return decrypt.doFinal(encrypted);
    }
    public static String newCode() {
        byte[] key = new byte[16]; new SecureRandom().nextBytes(key);
        StringBuilder s = new StringBuilder(); for (byte b : key) s.append(String.format("%02X", b)); return s.toString();
    }
    public static byte[] parseCode(String text) {
        text = text.replaceAll("[\\s-]", "");
        if (!text.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("Pairing code must be 32 hex characters");
        byte[] key = new byte[16]; for (int i=0; i<16; i++) key[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16); return key;
    }
}
