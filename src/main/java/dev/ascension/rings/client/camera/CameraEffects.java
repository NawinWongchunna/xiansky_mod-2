package dev.ascension.rings.client.camera;

import dev.ascension.rings.Timeline;

/** คีย์เฟรมของกล้อง cutscene (t = tick ตั้งแต่เริ่มสกิล) */
public final class CameraEffects {
    private CameraEffects() {}

    /** องศาหมุนวนรอบตัวผู้เล่น (orbit) */
    public static float yaw(float t) {
        return Timeline.keys(t, 0, 0, 60, 40, 120, 75, 160, 0);
    }

    /** เงยกล้องขึ้น (pitch ติดลบ = มองขึ้น) */
    public static float pitch(float t) {
        return Timeline.keys(t, 0, 0, 40, -14, 100, -22, 120, -6, 160, 0);
    }

    /** ถอยกล้องออกจากตัวเพิ่มเติม (บล็อก) */
    public static float distance(float t) {
        return Timeline.keys(t, 0, 0, 20, 1.5f, 100, 4.5f, 120, 2.0f, 130, 6.5f, 160, 0);
    }

    /** ลดกล้องลงต่ำ (ติดลบ) เพื่อมุมมองเงยมองฮีโร่ */
    public static float lower(float t) {
        return Timeline.keys(t, 0, 0, 40, -0.6f, 100, -1.4f, 120, -0.4f, 160, 0);
    }

    /** FOV เพิ่ม/ลด (องศา): ซูมเข้าตอนชาร์จ กระชากกว้างตอนระเบิด */
    public static float fov(float t) {
        return Timeline.keys(t, 0, 0, 20, 4, 100, -10, 118, -16, 122, 22, 140, 6, 160, 0);
    }

    /** แอมพลิจูดการสั่น 0..1.4 */
    public static float shakeAmp(float t) {
        return Timeline.keys(t, 0, 0.04f, 20, 0.12f, 60, 0.4f, 100, 0.75f, 119, 1.0f, 124, 1.4f, 140, 0.3f, 160, 0);
    }

    public static float noise(float x) {
        return (float) ((Math.sin(x * 12.9898) + Math.sin(x * 7.233 + 1.3)) * 0.5);
    }

    public static float shakeYaw(float t) {
        return shakeAmp(t) * 1.6f * noise(t * 3.7f);
    }

    public static float shakePitch(float t) {
        return shakeAmp(t) * 1.2f * noise(t * 4.9f + 17f);
    }
}
