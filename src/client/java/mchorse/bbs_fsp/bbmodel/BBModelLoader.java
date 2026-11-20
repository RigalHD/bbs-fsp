package mchorse.bbs_fsp.bbmodel;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.model.loaders.IModelLoader;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;

/**
 * Loads a model folder holding a Blockbench project, with every texture of the project as its own
 * material (see {@link BBModelParser}) and its animations as the model's animations. A texture embedded in the project is written once into the
 * model's {@code textures/<material>/} folder, the place BBS looks for material textures, so it can
 * be repainted or swapped there; a picture already in that folder wins over the embedded one.
 */
public class BBModelLoader implements IModelLoader
{
    public static final String[] EXTENSIONS = {".bbmodel", ".ajblueprint"};

    public static boolean isProject(String path)
    {
        for (String extension : EXTENSIONS)
        {
            if (path.endsWith(extension))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Link project = IModelLoader.getLink(model.combine("model.bbmodel"), links, (link) -> isProject(link.path));

        if (project == null || !links.contains(project))
        {
            return null;
        }

        BBModelParser.Result result;

        try (InputStream stream = models.provider.getAsset(project); Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
        {
            result = BBModelParser.parse(reader, models.parser);
        }
        catch (Exception e)
        {
            System.err.println("Failed to load Blockbench project: " + project);
            e.printStackTrace();

            return null;
        }

        if (result.model.topGroups.isEmpty())
        {
            return null;
        }

        ModelInstance instance = new ModelInstance(id, result.model, new Animations(models.parser), null);

        for (BBModelParser.Texture texture : result.textures)
        {
            Link link = this.texture(models.provider, links, model, texture);

            if (instance.baseTexture == null)
            {
                instance.baseTexture = link;
            }

            instance.materials.add(texture.material);
            instance.materialTextures.put(texture.material, link);
        }

        for (Animation animation : result.animations)
        {
            instance.animations.add(animation);
        }

        instance.applyConfig(config);

        return instance;
    }

    private Link texture(AssetProvider provider, Collection<Link> links, Link model, BBModelParser.Texture texture)
    {
        Link existing = IModelLoader.findMaterialTexture(links, model, texture.material);

        if (existing != null)
        {
            return existing;
        }

        if (texture.png != null)
        {
            Link link = model.combine("textures/" + texture.material + "/" + texture.material + ".png");
            File file = provider.getFile(link);

            if (file != null)
            {
                try
                {
                    file.getParentFile().mkdirs();
                    Files.write(file.toPath(), texture.png);

                    return link;
                }
                catch (Exception e)
                {
                    System.err.println("Failed to write texture " + link + ": " + e.getMessage());
                }
            }
        }
        else
        {
            /* A project that links its textures instead: look for the picture next to the project. */
            for (Link link : links)
            {
                if (link.path.endsWith("/" + texture.file))
                {
                    return link;
                }
            }
        }

        return LinkUtils.color(1F, 0F, 1F);
    }
}
