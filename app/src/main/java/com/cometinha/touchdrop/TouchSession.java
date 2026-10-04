package com.cometinha.touchdrop;

final class TouchSession {
    static volatile byte[] payload;
    static volatile Runnable onTouch;
    static volatile boolean enabled;
    static final byte[] SELECT = {(byte)0x00,(byte)0xA4,0x04,0x00,0x07,(byte)0xF0,0x54,0x4F,0x55,0x43,0x48,0x01,0x00};
    static final byte[] ACK = {(byte)0x80,0x10,0x00,0x00,0x00};
    static synchronized void clear() { enabled=false; payload=null; onTouch=null; }
}
