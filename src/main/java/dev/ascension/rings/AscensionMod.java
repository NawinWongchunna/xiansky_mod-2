package dev.ascension.rings;

import dev.ascension.rings.ability.AscensionServer;
import dev.ascension.rings.ability.NineStepServer;
import dev.ascension.rings.ability.SwordServer;
import dev.ascension.rings.ability.YinYangServer;
import dev.ascension.rings.network.Net;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AscensionMod implements ModInitializer {
    public static final String MOD_ID = "ascension_rings";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AscensionConfig.load();
        ModSounds.init();

        ServerPlayNetworking.registerGlobalReceiver(Net.ACTIVATE,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> AscensionServer.start(player)));

        ServerPlayNetworking.registerGlobalReceiver(Net.SWORD_ACTIVATE,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> SwordServer.start(player)));

        ServerPlayNetworking.registerGlobalReceiver(Net.YY_ACTIVATE,
                (server, player, handler, buf, responseSender) -> {
                    // ถ้ากดครั้งที่ 2 client จะแนบจุดที่เล็ง (ตามเมาส์) มาด้วย
                    Vec3d aim = buf.readBoolean() ? new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
                    int mode = buf.readableBytes() > 0 ? buf.readByte() : 0; // 1 = Shift+J พุ่งเข้าคัมภีร์
                    server.execute(() -> YinYangServer.press(player, aim, mode));
                });

        ServerPlayNetworking.registerGlobalReceiver(Net.NS_ACTIVATE,
                (server, player, handler, buf, responseSender) -> {
                    Vec3d aim = buf.readBoolean() ? new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
                    server.execute(() -> NineStepServer.press(player, aim));
                });

        ServerTickEvents.END_SERVER_TICK.register(AscensionServer::tick);
        ServerTickEvents.END_SERVER_TICK.register(NineStepServer::tick);
        ServerTickEvents.END_SERVER_TICK.register(YinYangServer::tick);
        ServerTickEvents.END_SERVER_TICK.register(SwordServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            AscensionServer.abort(handler.player);
            SwordServer.abort(handler.player);
            YinYangServer.abort(handler.player);
            NineStepServer.abort(handler.player);
        });

        // /ascension  = ใช้สกิลโดยไม่ต้องกดปุ่ม (ไว้ทดสอบ)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("ascension").executes(ctx -> {
                    AscensionServer.start(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })));

        // /voidblade = ใช้สกิล G โดยไม่ต้องกดปุ่ม (ไว้ทดสอบ)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("voidblade").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    SwordServer.start(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })));

        // /yinyang = สกิล J (เรียกครั้งแรก = เริ่ม, เรียกอีกครั้ง = สั่งโจมตีจุดที่เล็ง) ไว้ทดสอบ
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("yinyang").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    YinYangServer.press(ctx.getSource().getPlayerOrThrow(), null);
                    return 1;
                })));

        // /lift <x y z> [dimension] = สกิล V: แหวกมิติ -> เก้าก้าวย่างอากาศ -> พุ่งไปโผล่ที่พิกัด (รองรับ ~ และข้ามมิติ)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("lift").requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.argument("pos", net.minecraft.command.argument.Vec3ArgumentType.vec3())
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    Vec3d pos = net.minecraft.command.argument.Vec3ArgumentType.getVec3(ctx, "pos");
                                    return NineStepServer.begin(src.getPlayerOrThrow(), pos, src.getWorld()) ? 1 : 0;
                                })
                                .then(CommandManager.argument("dimension", net.minecraft.command.argument.DimensionArgumentType.dimension())
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            Vec3d pos = net.minecraft.command.argument.Vec3ArgumentType.getVec3(ctx, "pos");
                                            var dim = net.minecraft.command.argument.DimensionArgumentType.getDimensionArgument(ctx, "dimension");
                                            return NineStepServer.begin(src.getPlayerOrThrow(), pos, dim) ? 1 : 0;
                                        })))));

        LOGGER.info("Ascension Rings loaded");
    }
}
