package com.xitssunny;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = "snappyappy", name = "Snappy Tappy", version = "1.0",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class SnappyTappy {

    private static final int TOGGLE_KEY = Keyboard.KEY_F8;
    private static SocdMode MODE = SocdMode.LAST_INPUT;
    private static final AbsolutePref ABS_HORIZ = AbsolutePref.POSITIVE;
    private static final AbsolutePref ABS_VERT  = AbsolutePref.POSITIVE;

    private boolean enabled = true;

    private final Minecraft mc = Minecraft.getMinecraft();

    private static class AxisState {
        boolean negPressed;
        boolean posPressed;
        long negLastPressedNs;
        long posLastPressedNs;

        void onPress(boolean positive) {
            if (positive) {
                posPressed = true;
                posLastPressedNs = System.nanoTime();
            } else {
                negPressed = true;
                negLastPressedNs = System.nanoTime();
            }
        }

        void onRelease(boolean positive) {
            if (positive) posPressed = false; else negPressed = false;
        }
    }

    private final AxisState horiz = new AxisState();
    private final AxisState vert  = new AxisState();
    enum SocdMode { LAST_INPUT, NEUTRAL, ABSOLUTE }
    enum AbsolutePref { NEGATIVE, POSITIVE }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (mc.thePlayer == null) return;

        int code = Keyboard.getEventKey();
        if (code == 0) return;
        boolean down = Keyboard.getEventKeyState();

        if (code == TOGGLE_KEY && down) {
            enabled = !enabled;
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    "SnappyTappy " + (enabled ? "§aENABLED" : "§cDISABLED")
            ));
            if (!enabled) restoreVanilla();
            return;
        }

        KeyBinding left  = mc.gameSettings.keyBindLeft;
        KeyBinding right = mc.gameSettings.keyBindRight;
        KeyBinding fwd   = mc.gameSettings.keyBindForward;
        KeyBinding back  = mc.gameSettings.keyBindBack;

        if (code == left.getKeyCode()) {
            if (down) horiz.onPress(false); else horiz.onRelease(false);
        }
        if (code == right.getKeyCode()) {
            if (down) horiz.onPress(true); else horiz.onRelease(true);
        }
        if (code == back.getKeyCode()) {
            if (down) vert.onPress(false); else vert.onRelease(false);
        }
        if (code == fwd.getKeyCode()) {
            if (down) vert.onPress(true); else vert.onRelease(true);
        }

        if (enabled) resolveAndApply();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (mc.thePlayer == null || e.phase != TickEvent.Phase.END) return;
        if (!enabled) return;

        if (mc.currentScreen != null) {
            KeyBinding left  = mc.gameSettings.keyBindLeft;
            KeyBinding right = mc.gameSettings.keyBindRight;
            KeyBinding fwd   = mc.gameSettings.keyBindForward;
            KeyBinding back  = mc.gameSettings.keyBindBack;

            set(left, false);
            set(right, false);
            set(fwd, false);
            set(back, false);
            return;
        }

        syncAxisFromActual();
        resolveAndApply();
    }

    private void resolveAndApply() {
        KeyBinding left  = mc.gameSettings.keyBindLeft;
        KeyBinding right = mc.gameSettings.keyBindRight;
        KeyBinding fwd   = mc.gameSettings.keyBindForward;
        KeyBinding back  = mc.gameSettings.keyBindBack;

        Resolved rH = resolveAxis(horiz, MODE, ABS_HORIZ);
        set(left,  rH.negative);
        set(right, rH.positive);

        Resolved rV = resolveAxis(vert, MODE, ABS_VERT);
        set(back,  rV.negative);
        set(fwd,   rV.positive);
    }

    private static class Resolved { boolean negative, positive; }

    private Resolved resolveAxis(AxisState axis, SocdMode mode, AbsolutePref abs) {
        Resolved out = new Resolved();

        if (axis.negPressed && axis.posPressed) {
            switch (mode) {
                case LAST_INPUT:
                    boolean posIsLast = axis.posLastPressedNs >= axis.negLastPressedNs;
                    out.negative = !posIsLast;
                    out.positive = posIsLast;
                    break;
                case NEUTRAL:
                    out.negative = false;
                    out.positive = false;
                    break;
                case ABSOLUTE:
                    if (abs == AbsolutePref.POSITIVE) {
                        out.negative = false; out.positive = true;
                    } else {
                        out.negative = true; out.positive = false;
                    }
                    break;
            }
        } else if (axis.negPressed || axis.posPressed) {
            out.negative = axis.negPressed;
            out.positive = axis.posPressed;
        } else {
            out.negative = false;
            out.positive = false;
        }
        return out;
    }

    private void set(KeyBinding binding, boolean pressed) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
    }

    private void restoreVanilla() {
        syncAxisFromActual();

        KeyBinding left  = mc.gameSettings.keyBindLeft;
        KeyBinding right = mc.gameSettings.keyBindRight;
        KeyBinding fwd   = mc.gameSettings.keyBindForward;
        KeyBinding back  = mc.gameSettings.keyBindBack;

        set(left,  horiz.negPressed);
        set(right, horiz.posPressed);
        set(back,  vert.negPressed);
        set(fwd,   vert.posPressed);
    }

    private void syncAxisFromActual() {
        KeyBinding left  = mc.gameSettings.keyBindLeft;
        KeyBinding right = mc.gameSettings.keyBindRight;
        KeyBinding fwd   = mc.gameSettings.keyBindForward;
        KeyBinding back  = mc.gameSettings.keyBindBack;

        boolean leftDown  = Keyboard.isKeyDown(left.getKeyCode());
        boolean rightDown = Keyboard.isKeyDown(right.getKeyCode());
        boolean fwdDown   = Keyboard.isKeyDown(fwd.getKeyCode());
        boolean backDown  = Keyboard.isKeyDown(back.getKeyCode());

        if (leftDown != horiz.negPressed) {
            if (leftDown) horiz.onPress(false); else horiz.onRelease(false);
        }
        if (rightDown != horiz.posPressed) {
            if (rightDown) horiz.onPress(true); else horiz.onRelease(true);
        }
        if (backDown != vert.negPressed) {
            if (backDown) vert.onPress(false); else vert.onRelease(false);
        }
        if (fwdDown != vert.posPressed) {
            if (fwdDown) vert.onPress(true); else vert.onRelease(true);
        }
    }
}
