package ru.ferrlius.unique_paintings.client.model;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Wraps the vanilla painting BakedModel.
 *
 * Whenever Minecraft asks "which model should I render for this ItemStack?",
 * we read the stored painting variant id from the stack's entity_data
 * component and return the corresponding pre-baked variant model
 * (e.g. minecraft:item/painting/void). If the variant is missing or unknown,
 * we fall back to the default painting model so the item never renders blank.
 *
 * The variant models inherit from item/generated, so they get correct
 * per-pixel side extrusion, display transforms, and GUI rendering for free.
 */
public class VariantPaintingBakedModel extends BakedModelWrapper<BakedModel> {

    private final VariantOverrides overrides;

    public VariantPaintingBakedModel(BakedModel base, Map<ResourceLocation, BakedModel> variantModels) {
        super(base);
        this.overrides = new VariantOverrides(base, variantModels);
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    private static final class VariantOverrides extends ItemOverrides {

        private final BakedModel fallback;
        private final Map<ResourceLocation, BakedModel> variantModels;

        VariantOverrides(BakedModel fallback, Map<ResourceLocation, BakedModel> variantModels) {
            this.fallback = fallback;
            this.variantModels = variantModels;
        }

        @Override
        @Nullable
        public BakedModel resolve(
                BakedModel model,
                ItemStack stack,
                @Nullable ClientLevel level,
                @Nullable LivingEntity entity,
                int seed
        ) {
            ResourceLocation variantId = readVariantId(stack);
            if (variantId == null) {
                return fallback;
            }

            BakedModel variantModel = variantModels.get(variantId);
            return variantModel != null ? variantModel : fallback;
        }

        @Nullable
        private static ResourceLocation readVariantId(ItemStack stack) {
            CustomData data = stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
            if (data.isEmpty()) {
                return null;
            }

            CompoundTag tag = data.copyTag();
            if (!tag.contains("variant")) {
                return null;
            }

            return ResourceLocation.tryParse(tag.getString("variant"));
        }
    }
}
