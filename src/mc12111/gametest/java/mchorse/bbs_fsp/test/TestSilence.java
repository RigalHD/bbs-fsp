package mchorse.bbs_fsp.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.sound.SoundCategory;

/** The in-game checks run with the sound off: nobody listens to them. */
public final class TestSilence implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0D));
    }
}
