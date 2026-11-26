package dev.ascension.rings.client.sword;

import dev.ascension.rings.Timeline;
import dev.ascension.rings.client.camera.CameraEffects;

/** คีย์เฟรมกล้องของสกิล G (t = tick ตั้งแต่เริ่ม) — กล้องถอยไกลและเงยมองรอยแยกบนฟ้า */
public final class SwordCamera {
    private SwordCamera() {}

    public static float yaw(float t) {
        return Timeline.keys(t, 0, 0, 100, 18, 190, 35, 300, 80, 420, 0);
    }

    /** ติดลบ = เงยขึ้น */
    public static float pitch(float t) {
        return Timeline.keys(t, 0, 0, 40, -20, 120, -36, 175, -30, 190, -12, 230, -22, 340, -14, 420, 0);
    }

    public static float distance(float t) {
        return Timeline.keys(t, 0, 0, 40, 8, 120, 18, 175, 24, 192, 20, 260, 42, 340, 36, 420, 0);
    }

    public static float lower(float t) {
        return Timeline.keys(t, 0, 0, 60, -1.0f, 175, -2.0f, 200, -0.5f, 420, 0);
    }

    public static float fov(float t) {
        return Timeline.keys(t, 0, 0, 120, 4, 175, -14, 189, -18, 192, 32, 215, 10, 270, 0, 420, 0);
    }

    public static float shakeAmp(float t) {
        return Timeline.keys(t, 0, 0.05f, 25, 0.2f, 75, 0.55f, 150, 0.95f, 175, 1.3f, 189, 1.8f, 192, 3.2f,
                230, 1.6f, 290, 0.7f, 340, 0.25f, 420, 0f);
    }

    public static float shakeYaw(float t) {
        return shakeAmp(t) * 1.5f * CameraEffects.noise(t * 3.3f);
    }

    public static float shakePitch(float t) {
        return shakeAmp(t) * 1.1f * CameraEffects.noise(t * 4.1f + 9f);
    }
}
