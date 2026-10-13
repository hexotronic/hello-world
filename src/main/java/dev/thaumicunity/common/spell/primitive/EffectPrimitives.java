package dev.thaumicunity.common.spell.primitive;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.thaumicunity.api.spell.SpellContext;
import dev.thaumicunity.api.spell.SpellParams;
import dev.thaumicunity.api.spell.SpellPrimitive;
import dev.thaumicunity.api.spell.SpellTarget;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.registry.TUAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Built-in effect primitives (docs/SPELLS.md, "Effect primitives"). Power scales damage, healing, durations and
 * strengths linearly unless noted.
 */
public final class EffectPrimitives {
    private EffectPrimitives() {
    }

    /** Params: damage, damage_type, ignite (seconds), knockback, undead_bonus (multiplier vs undead). */
    public static final SpellPrimitive.Effect DAMAGE = (ctx, target, params, power) -> {
        if (!(target.entity() instanceof LivingEntity victim)) {
            return;
        }
        float amount = (float) (params.getDouble("damage", 4.0) * power);
        if (params.has("undead_bonus") && victim.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
            amount *= (float) params.getDouble("undead_bonus", 1.0);
        }
        victim.hurtServer(ctx.level(), damageSource(ctx, params), amount);
        double ignite = params.getDouble("ignite", 0);
        if (ignite > 0) {
            victim.igniteForSeconds((float) (ignite * power));
        }
        double knockback = params.getDouble("knockback", 0);
        if (knockback != 0 && ctx.caster() != null) {
            Vec3 away = victim.position().subtract(ctx.caster().position()).normalize();
            victim.push(away.scale(knockback * power).add(0, 0.15, 0));
            victim.needsSync = true;
        }
    };

    /** Damage that heals the caster for a fraction. Params: damage, fraction. */
    public static final SpellPrimitive.Effect DRAIN = (ctx, target, params, power) -> {
        if (!(target.entity() instanceof LivingEntity victim)) {
            return;
        }
        float before = victim.getHealth();
        victim.hurtServer(ctx.level(), damageSource(ctx, params), (float) (params.getDouble("damage", 3.0) * power));
        float dealt = Math.max(0, before - victim.getHealth());
        if (ctx.caster() != null && dealt > 0) {
            ctx.caster().heal(dealt * (float) params.getDouble("fraction", 0.5));
        }
    };

    /** Params: effect (mob effect id), duration (ticks), amplifier, scale_amplifier (bool). */
    public static final SpellPrimitive.Effect POTION = (ctx, target, params, power) -> {
        if (!(target.entity() instanceof LivingEntity living)) {
            return;
        }
        Optional<Holder.Reference<MobEffect>> effect = params.getId("effect").flatMap(BuiltInRegistries.MOB_EFFECT::get);
        if (effect.isEmpty()) {
            return;
        }
        int duration = (int) Math.round(params.getInt("duration", 200) * power);
        int amplifier = params.getInt("amplifier", 0);
        if (params.getBoolean("scale_amplifier", false)) {
            amplifier += (int) Math.floor(Math.max(0, power - 1));
        }
        living.addEffect(new MobEffectInstance(effect.get(), Math.max(1, duration), amplifier), ctx.caster());
    };

    /** Params: amount, cleanse (removes harmful effects). Undead are hurt instead when params.harm_undead is true. */
    public static final SpellPrimitive.Effect HEAL = (ctx, target, params, power) -> {
        if (!(target.entity() instanceof LivingEntity living)) {
            return;
        }
        float amount = (float) (params.getDouble("amount", 4.0) * power);
        if (params.getBoolean("harm_undead", true) && living.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
            living.hurtServer(ctx.level(), ctx.level().damageSources().magic(), amount);
            return;
        }
        living.heal(amount);
        if (params.getBoolean("cleanse", false)) {
            living.getActiveEffects().stream().filter(e -> !e.getEffect().value().isBeneficial()).map(MobEffectInstance::getEffect)
                    .toList().forEach(living::removeEffect);
        }
    };

    /** Params: amount (absorption hearts ×2), duration. */
    public static final SpellPrimitive.Effect SHIELD = (ctx, target, params, power) -> {
        if (target.entity() instanceof LivingEntity living) {
            int amplifier = Math.max(0, (int) Math.round(params.getDouble("amount", 1) * power) - 1);
            living.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION,
                    (int) Math.round(params.getInt("duration", 600) * power), amplifier), ctx.caster());
        }
    };

    /** Removes effects. Params: harmful (bool), beneficial (bool). */
    public static final SpellPrimitive.Effect DISPEL = (ctx, target, params, power) -> {
        if (target.entity() instanceof LivingEntity living) {
            boolean harmful = params.getBoolean("harmful", true);
            boolean beneficial = params.getBoolean("beneficial", false);
            living.getActiveEffects().stream()
                    .filter(e -> e.getEffect().value().isBeneficial() ? beneficial : harmful)
                    .map(MobEffectInstance::getEffect).toList().forEach(living::removeEffect);
        }
    };

    /** Sets entities and (when griefing is allowed) the block in front of the hit face on fire. Params: seconds. */
    public static final SpellPrimitive.Effect IGNITE = (ctx, target, params, power) -> {
        if (target.entity() != null) {
            target.entity().igniteForSeconds((float) (params.getDouble("seconds", 4) * power));
        } else if (target.hasBlock() && ctx.mayAlterWorld()) {
            BlockPos pos = target.placementPos();
            if (ctx.level().getBlockState(pos).isAir()) {
                ctx.level().setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
            }
        }
    };

    /** Removes fire from the target and nearby blocks. Params: radius. */
    public static final SpellPrimitive.Effect EXTINGUISH = (ctx, target, params, power) -> {
        if (target.entity() != null) {
            target.entity().clearFire();
        }
        int r = (int) Math.ceil(params.getDouble("radius", 2) * power);
        BlockPos center = BlockPos.containing(target.position());
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockState state = ctx.level().getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                ctx.level().removeBlock(pos, false);
            }
        }
    };

    /**
     * Chills creatures (freeze ticks and slowness) and freezes water. Params: freeze (ticks), slow (seconds),
     * damage, freeze_water (bool).
     */
    public static final SpellPrimitive.Effect FREEZE = (ctx, target, params, power) -> {
        if (target.entity() instanceof LivingEntity living) {
            living.setTicksFrozen(Math.min(living.getTicksFrozen() + (int) (params.getInt("freeze", 140) * power), 300));
            living.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,
                    (int) (params.getDouble("slow", 4) * 20 * power), params.getInt("amplifier", 1)), ctx.caster());
            double damage = params.getDouble("damage", 0);
            if (damage > 0) {
                living.hurtServer(ctx.level(), ctx.level().damageSources().freeze(), (float) (damage * power));
            }
        }
        if (params.getBoolean("freeze_water", true) && ctx.mayAlterWorld()) {
            int r = Math.max(1, (int) Math.round(params.getDouble("radius", 1.5) * power));
            BlockPos center = BlockPos.containing(target.position());
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 0, r))) {
                BlockState state = ctx.level().getBlockState(pos);
                if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
                    ctx.level().setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                } else if (state.is(Blocks.LAVA) && state.getFluidState().isSource() && params.getBoolean("freeze_lava", false)) {
                    ctx.level().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                }
            }
        }
    };

    /** Breaks the target block. Params: max_hardness, drop (bool), radius (cube). */
    public static final SpellPrimitive.Effect BREAK = (ctx, target, params, power) -> {
        if (!target.hasBlock() || !ctx.mayAlterWorld()) {
            return;
        }
        float maxHardness = (float) (params.getDouble("max_hardness", 3.0) * power);
        int r = params.getInt("radius", 0);
        BlockPos center = target.block();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockState state = ctx.level().getBlockState(pos);
            float hardness = state.getDestroySpeed(ctx.level(), pos);
            if (!state.isAir() && hardness >= 0 && hardness <= maxHardness && mayEdit(ctx, pos)) {
                ctx.level().destroyBlock(pos, params.getBoolean("drop", true), ctx.caster(), 512);
            }
        }
    };

    /** Places a block where the medium hit. Params: block, only_replaceable (bool). */
    public static final SpellPrimitive.Effect PLACE = (ctx, target, params, power) -> {
        if (!ctx.mayAlterWorld()) {
            return;
        }
        Optional<Block> block = params.getId("block").flatMap(BuiltInRegistries.BLOCK::getOptional);
        if (block.isEmpty()) {
            return;
        }
        BlockPos pos = target.hasBlock() ? target.placementPos() : BlockPos.containing(target.position());
        BlockState existing = ctx.level().getBlockState(pos);
        if ((existing.isAir() || existing.canBeReplaced()) && mayEdit(ctx, pos)) {
            ctx.level().setBlockAndUpdate(pos, block.get().defaultBlockState());
        }
    };

    /**
     * Equal trade: swaps the target block for the block item in the caster's off hand, giving the old block back.
     * Params: max_hardness.
     */
    public static final SpellPrimitive.Effect EXCHANGE = (ctx, target, params, power) -> {
        if (!target.hasBlock() || !(ctx.caster() instanceof Player player) || !ctx.mayAlterWorld()) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!(offhand.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        BlockPos pos = target.block();
        BlockState old = ctx.level().getBlockState(pos);
        float hardness = old.getDestroySpeed(ctx.level(), pos);
        if (old.is(blockItem.getBlock()) || hardness < 0 || hardness > params.getDouble("max_hardness", 25.0) || !mayEdit(ctx, pos)) {
            return;
        }
        ctx.level().destroyBlock(pos, true, player, 512);
        ctx.level().setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
        if (!player.isCreative()) {
            offhand.shrink(1);
        }
    };

    /** Pushes or pulls. Params: strength, mode ("away", "toward", "up", "forward"). */
    public static final SpellPrimitive.Effect PUSH = (ctx, target, params, power) -> {
        Entity e = target.entity();
        if (e == null) {
            return;
        }
        double strength = params.getDouble("strength", 1.2) * power;
        Vec3 from = ctx.caster() != null ? ctx.caster().position() : target.position();
        Vec3 dir = switch (params.getString("mode", "away")) {
            case "toward" -> from.subtract(e.position());
            case "up" -> new Vec3(0, 1, 0);
            case "forward" -> ctx.caster() != null ? ctx.caster().getLookAngle() : new Vec3(0, 1, 0);
            default -> e.position().subtract(from);
        };
        if (dir.lengthSqr() < 1.0e-6) {
            dir = new Vec3(0, 1, 0);
        }
        e.push(dir.normalize().scale(strength).add(0, params.getDouble("lift", 0.2), 0));
        e.needsSync = true;
    };

    /** Blinks the caster to the target position (safe spot above blocks). Params: max_distance. */
    public static final SpellPrimitive.Effect TELEPORT = (ctx, target, params, power) -> {
        LivingEntity caster = ctx.caster();
        if (caster == null) {
            return;
        }
        Vec3 dest = target.hasBlock() ? Vec3.atBottomCenterOf(target.placementPos()) : target.position();
        if (dest.distanceTo(caster.position()) > params.getDouble("max_distance", 32.0) * power) {
            return;
        }
        BlockPos feet = BlockPos.containing(dest);
        for (int i = 0; i < 4 && !(ctx.level().getBlockState(feet).isAir() && ctx.level().getBlockState(feet.above()).isAir()); i++) {
            feet = feet.above();
        }
        caster.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        caster.resetFallDistance();
    };

    /** Places a lingering light. Params: level (0-15). */
    public static final SpellPrimitive.Effect LIGHT = (ctx, target, params, power) -> {
        BlockPos pos = target.hasBlock() ? target.placementPos() : BlockPos.containing(target.position());
        if (ctx.level().getBlockState(pos).isAir()) {
            int level = Math.min(15, Math.max(1, params.getInt("level", 15)));
            ctx.level().setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, level));
        }
        if (target.entity() instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,
                    (int) (params.getInt("glow", 200) * power)), ctx.caster());
        }
    };

    /** Calls lightning. Params: visual_only (bool). */
    public static final SpellPrimitive.Effect LIGHTNING = (ctx, target, params, power) -> {
        LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(ctx.level(), EntitySpawnReason.TRIGGERED);
        if (bolt == null) {
            return;
        }
        bolt.setPos(target.position());
        bolt.setVisualOnly(params.getBoolean("visual_only", false));
        if (ctx.caster() instanceof ServerPlayer player) {
            bolt.setCause(player);
        }
        ctx.level().addFreshEntity(bolt);
    };

    /** Params: power (explosion radius), fire (bool), grief (bool). */
    public static final SpellPrimitive.Effect EXPLOSION = (ctx, target, params, power) -> {
        Vec3 p = target.position();
        boolean grief = params.getBoolean("grief", false) && ctx.mayAlterWorld();
        ctx.level().explode(ctx.caster(), p.x, p.y, p.z, (float) (params.getDouble("power", 2.0) * Math.sqrt(power)),
                params.getBoolean("fire", false), grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
    };

    /** Bonemeal-like growth. Params: times, radius. */
    public static final SpellPrimitive.Effect GROW = (ctx, target, params, power) -> {
        int times = Math.max(1, (int) Math.round(params.getInt("times", 1) * power));
        int r = params.getInt("radius", 0);
        BlockPos center = target.hasBlock() ? target.block() : BlockPos.containing(target.position()).below();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
            BlockState state = ctx.level().getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock growable) {
                for (int i = 0; i < times; i++) {
                    state = ctx.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof BonemealableBlock) || !growable.isValidBonemealTarget(ctx.level(), pos, state, BONEMEAL_SOURCE)) {
                        break;
                    }
                    growable.performBonemeal(ctx.level(), ctx.random(), pos, state, BONEMEAL_SOURCE);
                }
            }
        }
    };

    /** Smelts the target block in place (sand to glass, ore to ingot…). */
    public static final SpellPrimitive.Effect SMELT = (ctx, target, params, power) -> {
        if (!target.hasBlock() || !ctx.mayAlterWorld()) {
            return;
        }
        BlockPos pos = target.block();
        BlockState state = ctx.level().getBlockState(pos);
        ItemStack asItem = new ItemStack(state.getBlock().asItem());
        if (asItem.isEmpty() || !mayEdit(ctx, pos)) {
            return;
        }
        ctx.level().recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(asItem), ctx.level()).ifPresent(holder -> {
            ItemStack result = holder.value().assemble(new SingleRecipeInput(asItem));
            if (result.getItem() instanceof BlockItem blockItem) {
                ctx.level().setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
            } else {
                ctx.level().removeBlock(pos, false);
                Block.popResource(ctx.level(), pos, result);
            }
        });
    };

    /** Converts blocks by a table. Params: conversions ({"minecraft:stone": "minecraft:cobblestone"}). */
    public static final SpellPrimitive.Effect TRANSMUTE = (ctx, target, params, power) -> {
        if (!target.hasBlock() || !ctx.mayAlterWorld()) {
            return;
        }
        JsonObject table = params.raw().has("conversions") && params.raw().get("conversions").isJsonObject()
                ? params.raw().getAsJsonObject("conversions") : new JsonObject();
        BlockPos pos = target.block();
        Identifier current = BuiltInRegistries.BLOCK.getKey(ctx.level().getBlockState(pos).getBlock());
        JsonElement to = table.get(current.toString());
        if (to != null && to.isJsonPrimitive() && mayEdit(ctx, pos)) {
            Identifier id = Identifier.tryParse(to.getAsString());
            if (id != null) {
                BuiltInRegistries.BLOCK.getOptional(id).ifPresent(b -> ctx.level().setBlockAndUpdate(pos, b.defaultBlockState()));
            }
        }
    };

    /** Summons a temporary creature. Params: entity, count, lifetime (ticks). */
    public static final SpellPrimitive.Effect SUMMON = (ctx, target, params, power) -> {
        Optional<EntityType<?>> type = params.getId("entity").flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional);
        if (type.isEmpty()) {
            return;
        }
        int count = Math.max(1, (int) Math.round(params.getInt("count", 1) * Math.sqrt(power)));
        int lifetime = (int) Math.round(params.getInt("lifetime", 600) * power);
        Vec3 at = target.hasBlock() ? Vec3.atBottomCenterOf(target.placementPos()) : target.position();
        for (int i = 0; i < count; i++) {
            Entity e = type.get().create(ctx.level(), EntitySpawnReason.MOB_SUMMONED);
            if (e == null) {
                return;
            }
            e.snapTo(at.x + (ctx.random().nextDouble() - 0.5), at.y, at.z + (ctx.random().nextDouble() - 0.5),
                    ctx.random().nextFloat() * 360f, 0f);
            SpellScheduler.expireLater(ctx.level(), e, lifetime);
            ctx.level().addFreshEntity(e);
        }
    };

    /** Experience: positive amount grants orbs, negative drains from players. Params: amount. */
    public static final SpellPrimitive.Effect EXPERIENCE = (ctx, target, params, power) -> {
        int amount = (int) Math.round(params.getInt("amount", 5) * power);
        if (amount > 0) {
            ExperienceOrb.award(ctx.level(), target.position(), amount);
        } else if (target.entity() instanceof Player player) {
            player.giveExperiencePoints(amount);
            if (ctx.caster() instanceof Player caster && caster != player) {
                caster.giveExperiencePoints(-amount / 2);
            }
        }
    };

    /** Changes the aura at the target. Params: vis, flux (positive adds, negative removes). */
    public static final SpellPrimitive.Effect AURA = (ctx, target, params, power) -> {
        BlockPos pos = BlockPos.containing(target.position());
        double vis = params.getDouble("vis", 0) * power;
        double flux = params.getDouble("flux", 0) * power;
        if (vis > 0) {
            AuraManager.addVis(ctx.level(), pos, vis);
        } else if (vis < 0) {
            AuraManager.drainVis(ctx.level(), pos, -vis);
        }
        if (flux > 0) {
            AuraManager.addFlux(ctx.level(), pos, flux);
        } else if (flux < 0) {
            var aura = AuraManager.auraAt(ctx.level(), pos);
            aura.setFlux(aura.flux() + flux);
        }
    };

    /** Adds temporary warp to a player target. Params: amount. */
    public static final SpellPrimitive.Effect WARP = (ctx, target, params, power) -> {
        if (target.entity() instanceof ServerPlayer player) {
            var knowledge = player.getData(TUAttachments.KNOWLEDGE);
            knowledge.addWarp(0, 0, Math.max(1, (int) Math.round(params.getInt("amount", 1) * power)));
            player.syncData(TUAttachments.KNOWLEDGE);
        }
    };

    /** Pulls item entities towards the caster. Params: radius. */
    public static final SpellPrimitive.Effect ATTRACT_ITEMS = (ctx, target, params, power) -> {
        if (ctx.caster() == null) {
            return;
        }
        double r = params.getDouble("radius", 4) * power;
        Vec3 c = target.position();
        for (Entity e : ctx.level().getEntities((Entity) null, new AABB(c, c).inflate(r),
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof ExperienceOrb)) {
            e.teleportTo(ctx.caster().getX(), ctx.caster().getY() + 0.5, ctx.caster().getZ());
        }
    };

    /** Purely cosmetic: sound and particles (illusions, signals). Params: sound. */
    public static final SpellPrimitive.Effect ILLUSION = (ctx, target, params, power) -> {
        params.getId("sound").map(BuiltInRegistries.SOUND_EVENT::getValue).ifPresent(sound ->
                ctx.level().playSound(null, target.position().x, target.position().y, target.position().z, sound,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f));
        if (target.entity() instanceof LivingEntity living && params.getBoolean("confuse", false)) {
            living.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, (int) (120 * power)), ctx.caster());
        }
    };

    // --- helpers

    static DamageSource damageSource(SpellContext ctx, SpellParams params) {
        Optional<Identifier> type = params.getId("damage_type");
        if (type.isPresent()) {
            Optional<Holder.Reference<DamageType>> holder = ctx.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .get(ResourceKey.create(Registries.DAMAGE_TYPE, type.get()));
            if (holder.isPresent()) {
                return new DamageSource(holder.get(), ctx.caster(), ctx.caster());
            }
        }
        return ctx.caster() != null ? ctx.level().damageSources().indirectMagic(ctx.caster(), ctx.caster())
                : ctx.level().damageSources().magic();
    }

    static boolean mayEdit(SpellContext ctx, BlockPos pos) {
        if (ctx.caster() instanceof Player player) {
            return ctx.level().mayInteract(player, pos) && player.mayBuild();
        }
        return true;
    }
}
