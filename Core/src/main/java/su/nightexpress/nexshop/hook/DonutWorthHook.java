package su.nightexpress.nexshop.hook;

import me.serbob.donutworth.api.shop.ShopHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.nexshop.ShopPlugin;
import su.nightexpress.nexshop.api.shop.type.TradeType;
import su.nightexpress.nexshop.shop.virtual.impl.VirtualProduct;

public class DonutWorthHook implements ShopHook {

    private final ShopPlugin plugin;

    public DonutWorthHook(@NotNull ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "ExcellentShop";
    }

    @Override
    public void init() {

    }

    @Override
    public double getSellPrice(Material material) {
        VirtualProduct product = plugin.getVirtualShop().getBestProductFor(new ItemStack(material), TradeType.SELL);
        if (product == null) return -1;
        if (!product.isSellable()) return -1;
        if (product.getCurrency().getName().equalsIgnoreCase("kriptonit")) return -1;

        return product.getPrice(TradeType.SELL);
    }

    @Override
    public double getSellPrice(Player player, Material material) {
        VirtualProduct product = plugin.getVirtualShop().getBestProductFor(new ItemStack(material), TradeType.SELL);
        if (product == null) return -1;
        if (!product.isSellable()) return -1;
        if (product.getCurrency().getName().equalsIgnoreCase("kriptonit")) return -1;

        return product.getPrice(TradeType.SELL, player);
    }
}
