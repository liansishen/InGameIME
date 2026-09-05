package dev.ingameime;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import dev.ingameime.client.ClientEvents;
import dev.ingameime.client.ClientIme;

public final class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        ClientIme.getInstance()
            .start();
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }
}
