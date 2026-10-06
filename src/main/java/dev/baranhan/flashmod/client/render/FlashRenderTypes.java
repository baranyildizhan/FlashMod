package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Shader'siz "bloom"un temeli: additive (SRC_ALPHA, ONE) karisim. Ust uste binen yumusak
 * seritler isigi toplar -> merkez beyaza doygunlasir, kenarlar renkli halo olur.
 * Derinlik yazilmaz (COLOR_WRITE) ama derinlik testi yapilir -> bloklarin arkasinda kalir.
 */
public final class FlashRenderTypes extends RenderType {
    public static final RenderType ADDITIVE_GLOW = create("flashmod_additive_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> GHOSTS = new HashMap<>();

    private FlashRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                             boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /**
     * Phasing hayaletleri: entityTranslucent gibi ama derinlik YAZMAZ -> ust uste binen kopyalar
     * birbirini kesmez, bulanik/titresen bir yigin olusur.
     */
    public static RenderType ghost(ResourceLocation texture) {
        return GHOSTS.computeIfAbsent(texture, tex -> create("flashmod_ghost",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(true)));
    }
}
