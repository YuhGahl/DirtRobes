package com.dirtrobes.compat.jade;

import com.dirtrobes.block.IncubatorBlock;
import com.dirtrobes.block.IncubatorBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Intégration Jade (chargée par Jade seulement s'il est installé). */
@WailaPlugin
public class DirtRobesJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // toutes les entités de bloc : l'incubateur, et l'incubateur à engrenage de Create qui en porte un
        registration.registerBlockDataProvider(IncubatorProvider.INSTANCE, net.minecraft.world.level.block.entity.BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(IncubatorProvider.INSTANCE, net.minecraft.world.level.block.Block.class);
    }
}
