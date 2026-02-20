package com.babelmoth.rotp_ata.init;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.client.render.item.ThelaHunGinjeetSpearISTER;
import com.babelmoth.rotp_ata.item.ThelaHunGinjeetSpearItem;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class InitItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AddonMain.MOD_ID);

    public static final RegistryObject<Item> THELA_HUN_GINJEET_SPEAR = ITEMS.register("thela_hun_ginjeet_spear",
            () -> new ThelaHunGinjeetSpearItem(new Item.Properties().stacksTo(1)
                    .setISTER(() -> ThelaHunGinjeetSpearISTER::new)));

    public static final RegistryObject<Item> THELA_HUN_GINJEET_SPEAR_SPRITE = ITEMS.register("thela_hun_ginjeet_spear_sprite",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> THELA_HUN_GINJEET_SPEAR_RENDER = ITEMS.register("thela_hun_ginjeet_spear_render",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> THELA_HUN_GINJEET_SPEAR_THROWING = ITEMS.register("thela_hun_ginjeet_spear_throwing",
            () -> new Item(new Item.Properties().stacksTo(1)));
}
