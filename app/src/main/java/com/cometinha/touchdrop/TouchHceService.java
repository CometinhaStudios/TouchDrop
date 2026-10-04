package com.cometinha.touchdrop;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import java.util.Arrays;

public class TouchHceService extends HostApduService {
    private boolean selected;
    @Override public byte[] processCommandApdu(byte[] command, Bundle extras) {
        if (!TouchSession.enabled || TouchSession.payload == null) { selected=false; return new byte[]{0x69,(byte)0x85}; }
        // Accept SELECT with or without the optional Le byte.
        if (command != null && (Arrays.equals(command, TouchSession.SELECT)
            || Arrays.equals(command, Arrays.copyOf(TouchSession.SELECT, TouchSession.SELECT.length-1)))) {
            selected=true;
            byte[] p=TouchSession.payload;
            if (p == null) return new byte[]{0x69,(byte)0x85};
            byte[] response=Arrays.copyOf(p,p.length+2);response[p.length]=(byte)0x90;
            return response;
        }
        if (selected && Arrays.equals(command, TouchSession.ACK)) {
            selected=false; Runnable event=TouchSession.onTouch;
            if (event != null) event.run();
            return new byte[]{(byte)0x90,0x00};
        }
        return new byte[]{0x6D,0x00};
    }
    @Override public void onDeactivated(int reason) { selected=false; }
}
