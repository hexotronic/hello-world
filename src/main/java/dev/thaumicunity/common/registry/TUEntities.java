package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.entity.SpellProjectile;
import dev.thaumicunity.common.entity.SpellZoneEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types. */
public final class TUEntities {
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(ThaumicUnity.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpellProjectile>> SPELL_PROJECTILE = ENTITIES.registerEntityType(
            "spell_projectile", SpellProjectile::new, MobCategory.MISC,
            b -> b.sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(1).noSave());

    public static final DeferredHolder<EntityType<?>, EntityType<SpellZoneEntity>> SPELL_ZONE = ENTITIES.registerEntityType(
            "spell_zone", SpellZoneEntity::new, MobCategory.MISC,
            b -> b.sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(5).noSave().fireImmune());

    private TUEntities() {
    }

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
