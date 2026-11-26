package dev.ascension.rings.network;

import dev.ascension.rings.AscensionMod;
import net.minecraft.util.Identifier;

public final class Net {
    /** client -> server : ผู้เล่นกดปุ่มสกิล */
    public static final Identifier ACTIVATE = new Identifier(AscensionMod.MOD_ID, "activate");
    /** server -> client : เริ่มสกิล (uuid, x, y, z) */
    public static final Identifier START = new Identifier(AscensionMod.MOD_ID, "start");
    /** server -> client : จบสกิล (uuid) */
    public static final Identifier END = new Identifier(AscensionMod.MOD_ID, "end");

    /** สกิล G (Void Blade): client -> server */
    public static final Identifier SWORD_ACTIVATE = new Identifier(AscensionMod.MOD_ID, "sword_activate");
    /** server -> client : uuid, impact x,y,z, radius */
    public static final Identifier SWORD_START = new Identifier(AscensionMod.MOD_ID, "sword_start");
    /** server -> client : uuid */
    public static final Identifier SWORD_END = new Identifier(AscensionMod.MOD_ID, "sword_end");

    /** สกิล J (Yin-Yang Scripture): client -> server (กดครั้งแรก = เริ่ม, กดอีกครั้ง = สั่งมังกรโจมตีจุดที่เล็ง) */
    public static final Identifier YY_ACTIVATE = new Identifier(AscensionMod.MOD_ID, "yy_activate");
    /** server -> client : uuid, caster xyz, book xyz */
    public static final Identifier YY_START = new Identifier(AscensionMod.MOD_ID, "yy_start");
    /** server -> client : uuid, target xyz, radius */
    public static final Identifier YY_STRIKE = new Identifier(AscensionMod.MOD_ID, "yy_strike");
    /** server -> client : uuid */
    public static final Identifier YY_END = new Identifier(AscensionMod.MOD_ID, "yy_end");

    private Net() {}
}
