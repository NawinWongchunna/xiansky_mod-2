package dev.ascension.rings;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** เสียงต้นฉบับของสกิล J (สังเคราะห์เอง ไม่ใช่เสียงจากเกม) — ไฟล์อยู่ใน assets/ascension_rings/sounds */
public final class ModSounds {
    public static final SoundEvent CHARGE = reg("yy_charge");
    public static final SoundEvent SCRIPTURE = reg("yy_scripture");
    public static final SoundEvent DRAGON_WHITE = reg("yy_dragon_white");
    public static final SoundEvent DRAGON_BLACK = reg("yy_dragon_black");
    public static final SoundEvent WHOOSH = reg("yy_whoosh");
    public static final SoundEvent GONG = reg("yy_gong");
    public static final SoundEvent EXPLOSION = reg("yy_explosion");
    public static final SoundEvent LIGHT_BURST = reg("yy_light_burst");

    public static final SoundEvent NS_BELL = reg("ns_bell");
    public static final SoundEvent NS_RIFT = reg("ns_rift");
    public static final SoundEvent NS_CHOIR = reg("ns_choir");
    public static final SoundEvent NS_DASH = reg("ns_dash");
    public static final SoundEvent NS_BOOM = reg("ns_boom");

    private ModSounds() {}

    private static SoundEvent reg(String name) {
        Identifier id = new Identifier(AscensionMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** เรียกจาก onInitialize เพื่อบังคับให้ static field ด้านบนถูกลงทะเบียน */
    public static void init() {}
}
