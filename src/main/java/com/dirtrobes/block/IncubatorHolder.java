package com.dirtrobes.block;

/** Bloc dont l'entité porte un incubateur sans en être un (ex. : incubateur à engrenage de Create). */
public interface IncubatorHolder {
    IncubatorBlockEntity incubator();
}
