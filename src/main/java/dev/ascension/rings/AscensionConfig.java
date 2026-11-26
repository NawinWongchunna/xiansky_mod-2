package dev.ascension.rings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/ascension_rings.json */
public final class AscensionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AscensionConfig instance = new AscensionConfig();

    /** เปิด cutscene (กล้อง/ฟ้าเปลี่ยนสี/แถบดำ/overlay) ฝั่ง client */
    public boolean cinematic = true;
    /** ดาเมจตอนปล่อยพลัง (ฝั่ง server) */
    public float damage = 14.0f;
    /** รัศมีดาเมจ (บล็อก) */
    public double radius = 14.0;
    /** คูลดาวน์ (tick) 400 = 20 วินาที */
    public int cooldownTicks = 400;

    // ---- สกิล G (Void Blade) ----
    /** รัศมีคลื่นทำลายล้าง (บล็อก) */
    public double swordRadius = 100.0;
    /** ดาเมจต่อสิ่งมีชีวิตที่คลื่นผ่าน */
    public float swordDamage = 80.0f;
    /** คูลดาวน์ (tick) 2400 = 2 นาที */
    public int swordCooldownTicks = 2400;
    /** ทำลายบล็อก (ปิดได้ถ้าไม่ต้องการ grief) */
    public boolean destroyBlocks = true;
    /** เปลี่ยนสภาพอากาศเป็นพายุฝนฟ้าคะนอง */
    public boolean changeWeather = true;
    /** คืนสภาพอากาศเดิมหลังจบสกิล */
    public boolean restoreWeather = true;
    /** ฟ้าผ่าประกอบฉาก */
    public boolean swordLightning = true;

    // ---- สกิล J (Yin-Yang Scripture) ----
    /** รัศมีระเบิดหยินหยาง (บล็อก) */
    public double yinYangRadius = 100.0;
    /** ดาเมจต่อสิ่งมีชีวิตที่คลื่นผ่าน */
    public float yinYangDamage = 200.0f;
    /** คูลดาวน์ (tick) 3600 = 3 นาที */
    public int yinYangCooldownTicks = 3600;
    /** แปลงพื้นผิวเป็นลายหยินหยางขาว-ดำ (concrete) หลังทำลาย */
    public boolean yinYangPaintGround = true;

    public static AscensionConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("ascension_rings.json");
        try {
            if (Files.exists(path)) {
                AscensionConfig loaded = GSON.fromJson(Files.readString(path), AscensionConfig.class);
                if (loaded != null) instance = loaded;
            }
            Files.writeString(path, GSON.toJson(instance));
        } catch (Exception e) {
            AscensionMod.LOGGER.warn("Could not read/write config, using defaults", e);
        }
    }
}
