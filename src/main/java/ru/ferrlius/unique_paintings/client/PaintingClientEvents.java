package ru.ferrlius.unique_paintings.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.slf4j.Logger;
import ru.ferrlius.unique_paintings.client.model.VariantPaintingBakedModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-side model loading hooks.
 *
 * 1. {@link #registerAdditional(ModelEvent.RegisterAdditional)}:
 *    Scans the resource manager for every {@code models/item/painting/*.json}
 *    file across all loaded resource packs (mod assets + user resourcepacks)
 *    and asks the bakery to bake them. Without this step the bakery would
 *    skip them because nothing in the game directly references them.
 *
 * 2. {@link #modifyBakingResult(ModelEvent.ModifyBakingResult)}:
 *    Wraps the baked {@code minecraft:painting#inventory} model with
 *    {@link VariantPaintingBakedModel}, which uses ItemOverrides to swap to
 *    the variant-specific baked model based on the stack's entity_data
 *    component.
 *
 * Runs only on the client (Dist.CLIENT) and only on the mod event bus.
 */
@EventBusSubscriber(
        modid = "unique_paintings",
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class PaintingClientEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Resource path prefix for variant model files (without "models/" and ".json"). */
    private static final String VARIANT_MODEL_PREFIX = "item/painting/";

    /** Variant string used by ModelResourceLocation.standalone in 1.21. */
    private static final String STANDALONE_VARIANT = "standalone";

    /** Key under which the painting item's inventory baked model lives. */
    private static final ModelResourceLocation PAINTING_INVENTORY_KEY =
            ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("painting"));

    private PaintingClientEvents() {}

    @SubscribeEvent
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();

        // listResources walks all loaded packs (vanilla, mods, user resourcepacks).
        Set<ResourceLocation> found = rm.listResources(
                "models/" + VARIANT_MODEL_PREFIX.substring(0, VARIANT_MODEL_PREFIX.length() - 1),
                path -> path.getPath().endsWith(".json")
        ).keySet();

        int count = 0;
        for (ResourceLocation loc : found) {
            // loc looks like:  <ns>:models/item/painting/<variant>.json
            String full = loc.getPath();
            // Strip the "models/" prefix and ".json" suffix to get model id.
            String modelId = full.substring(
                    "models/".length(),
                    full.length() - ".json".length()
            );
            // Skip anything that didn't actually live under item/painting/
            // (defensive — listResources is recursive in some versions).
            if (!modelId.startsWith(VARIANT_MODEL_PREFIX)) {
                continue;
            }

            event.register(ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), modelId)
            ));
            count++;
        }

        LOGGER.debug("Registered {} painting variant models for baking", count);
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();

        BakedModel basePainting = models.get(PAINTING_INVENTORY_KEY);
        if (basePainting == null) {
            LOGGER.warn("minecraft:painting baked model not found; variant override skipped");
            return;
        }

        Map<ResourceLocation, BakedModel> variantMap = collectVariantModels(models);
        if (variantMap.isEmpty()) {
            LOGGER.warn("No painting variant models were baked");
        } else {
            LOGGER.debug("Loaded {} painting variant baked models", variantMap.size());
        }

        models.put(
                PAINTING_INVENTORY_KEY,
                new VariantPaintingBakedModel(basePainting, variantMap)
        );
    }

    /**
     * Walk the baked model map and collect every model whose key matches
     * {@code <ns>:item/painting/<variant>#standalone}, indexed by the
     * full painting variant id (e.g. {@code minecraft:void}).
     */
    private static Map<ResourceLocation, BakedModel> collectVariantModels(
            Map<ModelResourceLocation, BakedModel> models
    ) {
        Map<ResourceLocation, BakedModel> result = new HashMap<>();

        for (Map.Entry<ModelResourceLocation, BakedModel> entry : models.entrySet()) {
            ModelResourceLocation key = entry.getKey();
            if (!STANDALONE_VARIANT.equals(key.getVariant())) {
                continue;
            }

            ResourceLocation id = key.id();
            String path = id.getPath();
            if (!path.startsWith(VARIANT_MODEL_PREFIX)) {
                continue;
            }

            String variantName = path.substring(VARIANT_MODEL_PREFIX.length());
            result.put(
                    ResourceLocation.fromNamespaceAndPath(id.getNamespace(), variantName),
                    entry.getValue()
            );
        }

        return result;
    }
}
