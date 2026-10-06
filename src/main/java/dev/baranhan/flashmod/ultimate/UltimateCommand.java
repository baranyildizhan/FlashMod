package dev.baranhan.flashmod.ultimate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.speed.SpeedsterData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /flashult start [hedef] | preview | dummy | abort | cooldown reset | timescale <x>  (op 2). */
@Mod.EventBusSubscriber(modid = FlashMod.MODID)
public final class UltimateCommand {
    private UltimateCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("flashult").requires(src -> src.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(c -> start(c.getSource(), null))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> start(c.getSource(), EntityArgument.getEntity(c, "target")))))
                .then(Commands.literal("preview").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    UltimateManager.preview(p, UltimateTargeting.find(p, true));
                    return 1;
                }))
                .then(Commands.literal("dummy").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Zombie z = EntityType.ZOMBIE.create(p.level());
                    if (z == null) return 0;
                    Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
                    z.moveTo(p.getX() + look.x * 5, p.getY(), p.getZ() + look.z * 5, p.getYRot() + 180F, 0F);
                    z.setNoAi(true);
                    z.setPersistenceRequired();
                    p.level().addFreshEntity(z);
                    return 1;
                }))
                .then(Commands.literal("abort").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    UltimateSession s = UltimateManager.sessionOf(p);
                    if (s != null) UltimateManager.abort(p.server, s);
                    return s != null ? 1 : 0;
                }))
                .then(Commands.literal("cooldown").then(Commands.literal("reset").executes(c -> {
                    SpeedsterData.setUltCooldown(c.getSource().getPlayerOrException(), 0L);
                    c.getSource().sendSuccess(() -> Component.literal("Ultimate cooldown reset"), false);
                    return 1;
                })))
                .then(Commands.literal("timescale").then(Commands.argument("scale", FloatArgumentType.floatArg(0.1F, 2F))
                        .executes(c -> {
                            float v = FloatArgumentType.getFloat(c, "scale");
                            FlashServerConfig.ULT_DURATION_SCALE.set((double) v);
                            c.getSource().sendSuccess(() -> Component.literal("Ultimate durationScale = " + v), false);
                            return 1;
                        }))));
    }

    private static int start(CommandSourceStack src, Entity forced) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        LivingEntity le = forced instanceof LivingEntity l ? l : null;
        return UltimateManager.tryStart(p, le, true) ? 1 : 0;
    }
}
