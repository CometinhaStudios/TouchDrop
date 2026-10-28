package com.cyber90s.dual;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int PICK_ROM = 4107;
    private final WebView[] players = new WebView[2];
    private final String[] controllerDescriptors = new String[2];
    private final Map<Integer, Set<String>> axisKeysByDevice = new HashMap<>();
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> fileChooserCallback;
    private int mappingSlot = -1;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        controllerDescriptors[0] = getPreferences(MODE_PRIVATE).getString("controller_slot_1", "");
        controllerDescriptors[1] = getPreferences(MODE_PRIVATE).getString("controller_slot_2", "");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(15, 15, 18));

        for (int i = 0; i < 2; i++) {
            final int slot = i;
            WebView web = new WebView(this);
            players[i] = web;
            web.setBackgroundColor(Color.rgb(15, 15, 18));
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.getSettings().setMediaPlaybackRequiresUserGesture(false);
            web.getSettings().setAllowFileAccess(false);
            web.getSettings().setAllowContentAccess(true);
            web.addJavascriptInterface(new Bridge(slot), "CyberBridge");
            web.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    return assetLoader.shouldInterceptRequest(request.getUrl());
                }
            });
            web.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                    if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = callback;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    try {
                        startActivityForResult(intent, PICK_ROM);
                    } catch (Exception e) {
                        fileChooserCallback = null;
                        callback.onReceiveValue(null);
                        Toast.makeText(MainActivity.this, "Não consegui abrir o seletor de ROM.", Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i == 1) lp.leftMargin = 2;
            root.addView(web, lp);
            web.loadUrl("https://appassets.androidplatform.net/assets/player.html?slot=" + (slot + 1));
        }
        setContentView(root);
        restoreConnectedControllerLabels();
    }

    private void restoreConnectedControllerLabels() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || !isGamepad(d)) continue;
            String descriptor = d.getDescriptor();
            for (int slot = 0; slot < 2; slot++) {
                if (descriptor.equals(controllerDescriptors[slot])) {
                    updateControllerLabel(slot, d.getName());
                }
            }
        }
    }

    private boolean isGamepad(InputDevice d) {
        if (d == null) return false;
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private int slotForDescriptor(String descriptor) {
        if (descriptor == null) return -1;
        if (descriptor.equals(controllerDescriptors[0]) && !descriptor.isEmpty()) return 0;
        if (descriptor.equals(controllerDescriptors[1]) && !descriptor.isEmpty()) return 1;
        return -1;
    }

    private void assignController(int slot, InputDevice device) {
        if (device == null || !isGamepad(device)) {
            Toast.makeText(this, "Esse dispositivo não parece ser um controle. Aperta um botão no controle Bluetooth/USB.", Toast.LENGTH_LONG).show();
            return;
        }
        String descriptor = device.getDescriptor();
        int other = slot == 0 ? 1 : 0;
        if (descriptor.equals(controllerDescriptors[other])) {
            updateControllerLabel(slot, "Esse controle já está no outro lado");
            mappingSlot = -1;
            return;
        }
        controllerDescriptors[slot] = descriptor;
        getPreferences(MODE_PRIVATE).edit()
                .putString("controller_slot_" + (slot + 1), descriptor)
                .apply();
        mappingSlot = -1;
        updateControllerLabel(slot, device.getName());
        Toast.makeText(this, "Controle vinculado ao emulador " + (slot + 1), Toast.LENGTH_SHORT).show();
    }

    private void updateControllerLabel(int slot, String name) {
        if (players[slot] == null) return;
        String js = "if(window.setControllerStatus)window.setControllerStatus(" +
                JSONObject.quote(name == null ? "Controle conectado" : name) + ");";
        players[slot].post(() -> players[slot].evaluateJavascript(js, null));
    }

    private void sendKeyToSlot(int slot, String key, boolean down) {
        if (slot < 0 || slot > 1 || players[slot] == null || key == null) return;
        String js = "if(window.cyberInput)window.cyberInput(" + JSONObject.quote(key) + "," + down + ");";
        players[slot].post(() -> players[slot].evaluateJavascript(js, null));
    }

    private String mappedKey(int keyCode, int slot) {
        boolean first = slot == 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return first ? "ArrowUp" : "w";
            case KeyEvent.KEYCODE_DPAD_DOWN: return first ? "ArrowDown" : "s";
            case KeyEvent.KEYCODE_DPAD_LEFT: return first ? "ArrowLeft" : "a";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return first ? "ArrowRight" : "d";
            case KeyEvent.KEYCODE_BUTTON_A: return first ? "z" : "j";
            case KeyEvent.KEYCODE_BUTTON_B: return first ? "x" : "k";
            case KeyEvent.KEYCODE_BUTTON_X: return first ? "a" : "u";
            case KeyEvent.KEYCODE_BUTTON_Y: return first ? "s" : "i";
            case KeyEvent.KEYCODE_BUTTON_START: return "enter";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return first ? "v" : "h";
            case KeyEvent.KEYCODE_BUTTON_L1: return first ? "q" : "o";
            case KeyEvent.KEYCODE_BUTTON_R1: return first ? "e" : "p";
            case KeyEvent.KEYCODE_BUTTON_L2: return first ? "tab" : "y";
            case KeyEvent.KEYCODE_BUTTON_R2: return first ? "r" : "t";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return first ? "1" : "3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return first ? "2" : "4";
            default: return null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        if (!isGamepad(device)) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return true;

        if (mappingSlot >= 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
            assignController(mappingSlot, device);
            return true;
        }
        int slot = slotForDescriptor(device.getDescriptor());
        if (slot < 0) return true;
        String key = mappedKey(event.getKeyCode(), slot);
        if (key != null) sendKeyToSlot(slot, key, event.getAction() == KeyEvent.ACTION_DOWN);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (!isGamepad(device) || (event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return super.dispatchGenericMotionEvent(event);
        }
        int slot = slotForDescriptor(device.getDescriptor());
        if (slot < 0) return true;

        float x = Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_X)) > 0.1f
                ? event.getAxisValue(MotionEvent.AXIS_HAT_X) : event.getAxisValue(MotionEvent.AXIS_X);
        float y = Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_Y)) > 0.1f
                ? event.getAxisValue(MotionEvent.AXIS_HAT_Y) : event.getAxisValue(MotionEvent.AXIS_Y);
        Set<String> desired = new HashSet<>();
        boolean first = slot == 0;
        if (y < -0.5f) desired.add(first ? "ArrowUp" : "w");
        if (y > 0.5f) desired.add(first ? "ArrowDown" : "s");
        if (x < -0.5f) desired.add(first ? "ArrowLeft" : "a");
        if (x > 0.5f) desired.add(first ? "ArrowRight" : "d");

        Set<String> old = axisKeysByDevice.get(event.getDeviceId());
        if (old == null) old = new HashSet<>();
        for (String key : old) if (!desired.contains(key)) sendKeyToSlot(slot, key, false);
        for (String key : desired) if (!old.contains(key)) sendKeyToSlot(slot, key, true);
        axisKeysByDevice.put(event.getDeviceId(), desired);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_ROM && fileChooserCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
    }

    private final class Bridge {
        private final int slot;
        Bridge(int slot) { this.slot = slot; }

        @JavascriptInterface
        public void bindController() {
            runOnUiThread(() -> {
                mappingSlot = slot;
                Toast.makeText(MainActivity.this,
                        "Emulador " + (slot + 1) + ": aperta um botão no controle que quer vincular.",
                        Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void showMessage(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }
}
