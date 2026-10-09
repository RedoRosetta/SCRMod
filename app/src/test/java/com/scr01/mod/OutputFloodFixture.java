package com.scr01.mod;

public final class OutputFloodFixture {
    public static void main(String[] args) throws Exception {
        byte[] block = new byte[4096];
        java.util.Arrays.fill(block, (byte) 'x');
        for (int i = 0; i < 64; i++) {
            System.out.write(block);
            System.err.write(block);
        }
        System.out.flush();
        System.err.flush();
    }
}
