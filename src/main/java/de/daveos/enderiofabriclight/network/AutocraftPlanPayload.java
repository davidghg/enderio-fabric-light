package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import de.daveos.enderiofabriclight.autocraft.Autocrafter;
import de.daveos.enderiofabriclight.autocraft.CraftingPlan;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * S2C payload: answer to an {@link AutocraftRequestPayload}, either a preview or a craft result.
 *
 * @param outcome {@link Autocrafter.Outcome} ordinal
 * @param status  {@link CraftingPlan.Status} ordinal when a plan was made, else -1
 * @param steps   number of crafting steps in the plan
 */
public record AutocraftPlanPayload(int outcome, int status, Item item, int amount, int limit, int steps,
                                   List<ItemAmount> consumed, List<ItemAmount> missing) implements CustomPacketPayload {
    public static final Type<AutocraftPlanPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "autocraft_plan")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AutocraftPlanPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, AutocraftPlanPayload::outcome,
        ByteBufCodecs.VAR_INT, AutocraftPlanPayload::status,
        ByteBufCodecs.registry(Registries.ITEM), AutocraftPlanPayload::item,
        ByteBufCodecs.VAR_INT, AutocraftPlanPayload::amount,
        ByteBufCodecs.VAR_INT, AutocraftPlanPayload::limit,
        ByteBufCodecs.VAR_INT, AutocraftPlanPayload::steps,
        ItemAmount.STREAM_CODEC.apply(ByteBufCodecs.list()), AutocraftPlanPayload::consumed,
        ItemAmount.STREAM_CODEC.apply(ByteBufCodecs.list()), AutocraftPlanPayload::missing,
        AutocraftPlanPayload::new
    );

    public static AutocraftPlanPayload of(Autocrafter.Result result, Item item, int amount) {
        CraftingPlan plan = result.plan();
        return new AutocraftPlanPayload(result.outcome().ordinal(),
            plan == null ? -1 : plan.status().ordinal(), item, amount, result.limit(),
            plan == null ? 0 : plan.steps().size(),
            plan == null ? List.of() : ItemAmount.of(plan.consumed()),
            plan == null ? List.of() : ItemAmount.of(plan.missing()));
    }

    public Autocrafter.Outcome outcomeValue() {
        return Autocrafter.Outcome.values()[outcome];
    }

    /** Null when no plan was made. */
    public CraftingPlan.Status statusValue() {
        return status < 0 ? null : CraftingPlan.Status.values()[status];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
