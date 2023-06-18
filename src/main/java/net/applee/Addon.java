package net.applee;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class Addon extends MeteorAddon {

    @Override
    public void onInitialize() {
        Modules.get().add(new ElytraFinder());
    }

    @Override
    public String getPackage() {
        return "net.applee";
    }
}
