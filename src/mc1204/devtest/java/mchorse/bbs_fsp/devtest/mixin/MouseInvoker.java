package mchorse.bbs_fsp.devtest.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MouseInvoker
{
    @Invoker("onCursorPos")
    void bbsFspDevtest$onCursorPos(long window, double x, double y);
}
