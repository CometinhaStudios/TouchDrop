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
    private boolean focusMode = false;
    private String capturingAction = null;
    private int capturingSlot = -1;

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

    private org.json.JSONArray connectedControllersJson() {
        org.json.JSONArray result = new org.json.JSONArray();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || !isGamepad(d) || d.isVirtual()) continue;
            try {
                JSONObject item = new JSONObject();
                item.put("id", d.getId());
                item.put("name", d.getName());
                item.put("descriptor", d.getDescriptor());
                int assigned = slotForDescriptor(d.getDescriptor());
                item.put("assigned", assigned < 0 ? 0 : assigned + 1);
                item.put("sources", d.getSources());
                result.put(item);
            } catch (Exception ignored) {}
        }
        return result;
    }

    private void disconnectController(int slot) {
        String old = controllerDescriptors[slot];
        controllerDescriptors[slot] = "";
        getPreferences(MODE_PRIVATE).edit().putString("controller_slot_" + (slot+1), "").apply();
        if (old != null && !old.isEmpty()) {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice d = InputDevice.getDevice(id);
                if (d != null && old.equals(d.getDescriptor())) axisKeysByDevice.remove(id);
            }
        }
        updateControllerLabel(slot, "não vinculado");
        Toast.makeText(this, "Controle desvinculado do jogador " + (slot+1), Toast.LENGTH_SHORT).show();
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

    private String actionForKeyCode(int keyCode, int slot) {
        String[] actions = {"UP","DOWN","LEFT","RIGHT","A","B","X","Y","START","SELECT","L1","R1","L2","R2"};
        String[] prefKeys = new String[actions.length];
        for (int i=0;i<actions.length;i++) prefKeys[i] = "map_slot_" + (slot+1) + "_" + actions[i];
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        int[] defaults = {KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT,KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_L2,KeyEvent.KEYCODE_BUTTON_R2};
        for (int i=0;i<actions.length;i++) {
            int configured = prefs.getInt(prefKeys[i], defaults[i]);
            if (configured != 0 && configured == keyCode) return actions[i];
        }
        return null;
    }

    private String mappedKey(int keyCode, int slot) {
        String action = actionForKeyCode(keyCode, slot);
        if (action == null) return null;
        boolean first = slot == 0;
        switch (action) {
            case "UP": return first ? "ArrowUp" : "w";
            case "DOWN": return first ? "ArrowDown" : "s";
            case "LEFT": return first ? "ArrowLeft" : "a";
            case "RIGHT": return first ? "ArrowRight" : "d";
            case "A": return first ? "z" : "j";
            case "B": return first ? "x" : "k";
            case "X": return first ? "a" : "u";
            case "Y": return first ? "s" : "i";
            case "START": return "enter";
            case "SELECT": return first ? "v" : "h";
            case "L1": return first ? "q" : "o";
            case "R1": return first ? "e" : "p";
            case "L2": return first ? "tab" : "y";
            case "R2": return first ? "r" : "t";
            default: return null;
        }
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) return super.dispatchKeyEvent(event);

        int assignedSlot = slotForDescriptor(device.getDescriptor());
        boolean controllerEvent = isGamepad(device) || assignedSlot >= 0;

        // Ao mapear, aceite o botão do controle selecionado mesmo que o Android
        // classifique esse evento como teclado/D-pad em vez de SOURCE_GAMEPAD.
        if (capturingAction != null && event.getAction() == KeyEvent.ACTION_DOWN
                && controllerEvent && (assignedSlot < 0 || assignedSlot == capturingSlot)) {
            int targetSlot = capturingSlot;
            String action = capturingAction;
            getPreferences(MODE_PRIVATE).edit()
                    .putInt("map_slot_" + (targetSlot + 1) + "_" + action, event.getKeyCode())
                    .apply();
            capturingAction = null;
            capturingSlot = -1;
            if (targetSlot >= 0 && targetSlot < 2 && players[targetSlot] != null) {
                String js = "if(window.onPhysicalButtonCaptured)window.onPhysicalButtonCaptured("
                        + JSONObject.quote(action) + "," + event.getKeyCode() + ");";
                players[targetSlot].post(() -> players[targetSlot].evaluateJavascript(js, null));
            }
            Toast.makeText(this, action + " mapeado para o botão " + event.getKeyCode(), Toast.LENGTH_SHORT).show();
            return true;
        }

        // Depois de vinculado, confie no descriptor salvo. Alguns controles
        // enviam os botões como SOURCE_KEYBOARD ou SOURCE_DPAD.
        if (assignedSlot < 0) {
            if (!controllerEvent) return super.dispatchKeyEvent(event);
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return true;
        String key = mappedKey(event.getKeyCode(), assignedSlot);
        if (key != null) {
            sendKeyToSlot(assignedSlot, key, event.getAction() == KeyEvent.ACTION_DOWN);
            players[assignedSlot].post(() -> players[assignedSlot].requestFocus());
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) return super.dispatchGenericMotionEvent(event);
        int slot = slotForDescriptor(device.getDescriptor());
        boolean joystickEvent = (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        if (slot < 0 && (!isGamepad(device) || !joystickEvent)) return super.dispatchGenericMotionEvent(event);
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
    public void onBackPressed() {
        if (focusMode) {
            focusMode = false;
            String js = "if(window.toggleFocusMode)window.toggleFocusMode(false);";
            for (WebView player : players) if (player != null) player.evaluateJavascript(js, null);
        } else {
            super.onBackPressed();
        }
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
        public void beginButtonCapture(String action) {
            runOnUiThread(() -> {
                capturingAction = action;
                capturingSlot = slot;
                Toast.makeText(MainActivity.this, "Aperta agora o botão físico que quer usar para " + action + ".", Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public String getControllers() {
            return connectedControllersJson().toString();
        }

        @JavascriptInterface
        public void assignController(String descriptor) {
            runOnUiThread(() -> {
                for (int id : InputDevice.getDeviceIds()) {
                    InputDevice device = InputDevice.getDevice(id);
                    if (device != null && descriptor != null && descriptor.equals(device.getDescriptor())) {
                        MainActivity.this.assignController(slot, device);
                        return;
                    }
                }
                Toast.makeText(MainActivity.this, "Controle não encontrado. Toca em Atualizar lista.", Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void disconnectController() {
            runOnUiThread(() -> MainActivity.this.disconnectController(slot));
        }

        @JavascriptInterface
        public void bindController() {
            runOnUiThread(() -> {
                mappingSlot = -1;
                Toast.makeText(MainActivity.this,
                        "Seleciona o controle conectado na lista e toca em Vincular.",
                        Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public String getPhysicalMapping() {
            try {
                String[] actions = {"UP","DOWN","LEFT","RIGHT","A","B","X","Y","START","SELECT","L1","R1","L2","R2"};
                int[] defaults = {KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y,
                        KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT,KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,
                        KeyEvent.KEYCODE_BUTTON_L2,KeyEvent.KEYCODE_BUTTON_R2};
                android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                JSONObject result = new JSONObject();
                for (int i=0;i<actions.length;i++) result.put(actions[i], String.valueOf(prefs.getInt("map_slot_" + (slot+1) + "_" + actions[i], defaults[i])));
                return result.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void savePhysicalMapping(String json) {
            runOnUiThread(() -> {
                try {
                    JSONObject values = new JSONObject(json);
                    String[] actions = {"UP","DOWN","LEFT","RIGHT","A","B","X","Y","START","SELECT","L1","R1","L2","R2"};
                    android.content.SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
                    for (String action : actions) {
                        if (values.has(action)) editor.putInt("map_slot_" + (slot+1) + "_" + action, Integer.parseInt(values.getString(action)));
                    }
                    editor.apply();
                    Toast.makeText(MainActivity.this, "Mapeamento salvo no lado " + (slot+1), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Não consegui salvar o mapeamento.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void toggleFullscreen() {
            runOnUiThread(() -> {
                focusMode = !focusMode;
                String js = "if(window.toggleFocusMode)window.toggleFocusMode(" + focusMode + ");";
                for (WebView player : players) if (player != null) player.evaluateJavascript(js, null);
            });
        }

        @JavascriptInterface
        public void showMessage(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }
}
