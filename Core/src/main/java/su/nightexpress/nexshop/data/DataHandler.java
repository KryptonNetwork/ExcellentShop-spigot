package su.nightexpress.nexshop.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.nexshop.ShopPlugin;
import su.nightexpress.nexshop.api.shop.Shop;
import su.nightexpress.nexshop.api.shop.product.Product;
import su.nightexpress.nexshop.api.shop.type.TradeType;
import su.nightexpress.nexshop.config.Config;
import su.nightexpress.nexshop.data.key.ProductKey;
import su.nightexpress.nexshop.data.legacy.LegacyStockAmount;
import su.nightexpress.nexshop.data.legacy.LegacyStockAmountSerializer;
import su.nightexpress.nexshop.data.legacy.LegacyStockData;
import su.nightexpress.nexshop.data.product.PriceData;
import su.nightexpress.nexshop.data.product.StockData;
import su.nightexpress.nexshop.data.shop.RotationData;
import su.nightexpress.nexshop.shop.virtual.impl.Rotation;
import su.nightexpress.nexshop.shop.virtual.impl.VirtualProduct;
import su.nightexpress.nexshop.shop.virtual.impl.VirtualShop;
import su.nightexpress.nexshop.user.ShopUser;
import su.nightexpress.nexshop.user.UserSettings;
import su.nightexpress.nightcore.db.AbstractUserDataManager;
import su.nightexpress.nightcore.db.sql.column.Column;
import su.nightexpress.nightcore.db.sql.column.ColumnType;
import su.nightexpress.nightcore.db.sql.query.SQLQueries;
import su.nightexpress.nightcore.db.sql.query.impl.DeleteQuery;
import su.nightexpress.nightcore.db.sql.query.impl.SelectQuery;
import su.nightexpress.nightcore.db.sql.query.type.ValuedQuery;
import su.nightexpress.nightcore.db.sql.util.WhereOperator;
import su.nightexpress.nightcore.util.Lists;
import su.nightexpress.nightcore.util.TimeUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

public class DataHandler extends AbstractUserDataManager<ShopPlugin, ShopUser> {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .registerTypeAdapter(LegacyStockAmount.class, new LegacyStockAmountSerializer())
        .create();

    public static final Column COLUMN_GEN_SHOP_ID    = Column.of("shopId", ColumnType.STRING);
    public static final Column COLUMN_GEN_PRODUCT_ID = Column.of("productId", ColumnType.STRING);
    public static final Column COLUMN_GEN_HOLDER_ID  = Column.of("holderId", ColumnType.STRING);

    public static final Column COLUMN_STOCK_BUY_STOCK    = Column.of("buyStock", ColumnType.INTEGER);
    public static final Column COLUMN_STOCK_SELL_STOCK   = Column.of("sellStock", ColumnType.INTEGER);
    public static final Column COLUMN_STOCK_RESTOCK_DATE = Column.of("restockDate", ColumnType.LONG);

    public static final Column COLUMN_PRICE_LAST_BUY     = Column.of("lastBuyPrice", ColumnType.DOUBLE);
    public static final Column COLUMN_PRICE_LAST_SELL    = Column.of("lastSellPrice", ColumnType.DOUBLE);
    public static final Column COLUMN_PRICE_LAST_UPDATED = Column.of("lastUpdated", ColumnType.LONG);
    public static final Column COLUMN_PRICE_EXPIRE_DATE  = Column.of("expireDate", ColumnType.LONG);
    public static final Column COLUMN_PRICE_PURCHASES    = Column.of("purchases", ColumnType.INTEGER);
    public static final Column COLUMN_PRICE_SALES        = Column.of("sales", ColumnType.INTEGER);

    public static final Column COLUMN_ROTATE_PRODUCTS      = Column.of("products", ColumnType.STRING);
    public static final Column COLUMN_ROTATE_NEXT_ROTATION = Column.of("nextRotation", ColumnType.LONG);

    public static final Column COLUMN_BANK_HOLDER  = Column.of("holder", ColumnType.STRING);
    public static final Column COLUMN_BANK_BALANCE = Column.of("balance", ColumnType.STRING);

    private static final Column COL_USER_SETTINGS = Column.of("settings", ColumnType.STRING);

    private final String tablePriceData;
    private final String tableStockData;
    private final String tableRotationData;
    private final String tableChestBank;

    public DataHandler(@NotNull ShopPlugin plugin) {
        super(plugin);
        this.tablePriceData = this.getTablePrefix() + "_" + Config.DATA_PRICE_TABLE.get();
        this.tableStockData = this.getTablePrefix() + "_" + Config.DATA_STOCKS_TABLE.get();
        this.tableRotationData = this.getTablePrefix() + "_" + Config.DATA_ROTATIONS_TABLE.get();
        this.tableChestBank = this.getTablePrefix() + "_chestshop_bank";
    }

    @Override
    @NotNull
    protected GsonBuilder registerAdapters(@NotNull GsonBuilder builder) {
        return builder;
    }

    @Override
    public void onPurge() {
        super.onPurge();

        LocalDateTime deadline = LocalDateTime.now().minusDays(this.getConfig().getPurgePeriod());
        long deadlineMs = TimeUtil.toEpochMillis(deadline);

        // TODO ChestShop bank purge

        if (SQLQueries.hasTable(this.connector, this.tablePriceData)) {
            DeleteQuery<Long> query = new DeleteQuery<Long>().where(DataHandler.COLUMN_PRICE_LAST_UPDATED, WhereOperator.SMALLER, String::valueOf);
            this.delete(this.tablePriceData, query, deadlineMs);
        }
        if (SQLQueries.hasTable(this.connector, this.tableStockData)) {
            DeleteQuery<Long> query = new DeleteQuery<Long>().where(DataHandler.COLUMN_STOCK_RESTOCK_DATE, WhereOperator.SMALLER, String::valueOf);
            this.delete(this.tableStockData, query, deadlineMs);
        }
        if (SQLQueries.hasTable(this.connector, this.tableRotationData)) {
            DeleteQuery<Long> query = new DeleteQuery<Long>().where(DataHandler.COLUMN_ROTATE_NEXT_ROTATION, WhereOperator.SMALLER, String::valueOf);
            this.delete(this.tableRotationData, query, deadlineMs);
        }
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        this.createTable(this.tableStockData, Lists.newList(
            COLUMN_GEN_SHOP_ID,
            COLUMN_GEN_PRODUCT_ID,
            COLUMN_GEN_HOLDER_ID,
            COLUMN_STOCK_BUY_STOCK,
            COLUMN_STOCK_SELL_STOCK,
            COLUMN_STOCK_RESTOCK_DATE
        ));

        this.createTable(this.tablePriceData, Lists.newList(
            COLUMN_GEN_SHOP_ID,
            COLUMN_GEN_PRODUCT_ID,
            COLUMN_PRICE_LAST_BUY,
            COLUMN_PRICE_LAST_SELL,
            COLUMN_PRICE_LAST_UPDATED,
            COLUMN_PRICE_EXPIRE_DATE,
            COLUMN_PRICE_PURCHASES,
            COLUMN_PRICE_SALES
        ));

        this.createTable(this.tableRotationData, Lists.newList(
            COLUMN_GEN_SHOP_ID,
            COLUMN_GEN_HOLDER_ID,
            COLUMN_ROTATE_NEXT_ROTATION,
            COLUMN_ROTATE_PRODUCTS
        ));

        this.createTable(this.tableChestBank, Lists.newList(
            COLUMN_BANK_HOLDER,
            COLUMN_BANK_BALANCE
        ));
    }

    @Override
    public void onSynchronize() {
        this.plugin.getDataManager().handleSynchronization();
    }

    public void updateStockDatas() {
        String tableStockDataLegacy = this.getTablePrefix() + "_virtual_stock_data_fused";

        if (!SQLQueries.hasTable(this.connector, tableStockDataLegacy)) return;
        if (!SQLQueries.hasColumn(this.connector, tableStockDataLegacy, "globalAmount")) return;

        Map<ProductKey, StockData> dataMap = new HashMap<>();

        this.select(tableStockDataLegacy, DataQueries.LEGACY_STOCK_DATA_LOADER, SelectQuery::all).forEach(data -> {
            data.getPlayerAmounts().forEach((tradeType, holderMap) -> {
                holderMap.forEach((holderId, amounts) -> {
                    updateStockData(dataMap, data, tradeType, amounts, holderId.toString());
                });
            });

            data.getGlobalAmounts().forEach((tradeType, amounts) -> {
                updateStockData(dataMap, data, tradeType, amounts, data.getShopId());
            });
        });

        this.insert(this.tableStockData, DataQueries.STOCK_DATA_INSERT, dataMap.values());

        this.dropColumn(tableStockDataLegacy, "globalAmount");
        this.dropColumn(tableStockDataLegacy, "playerAmount");
    }

    private void updateStockData(Map<ProductKey, StockData> dataMap,
                                 LegacyStockData data,
                                 TradeType tradeType,
                                 LegacyStockAmount amounts,
                                 String holderId) {
        ProductKey key = new ProductKey(data.getShopId(), data.getProductId(), data.getShopId());

        StockData stockData = dataMap.computeIfAbsent(key, k -> new StockData(data.getShopId(), data.getProductId(), holderId, 0, 0, 0));
        int items = amounts.getItemsLeft();
        long restock = amounts.getRestockDate();
        if (restock > stockData.getRestockDate()) stockData.setRestockDate(restock);

        if (tradeType == TradeType.BUY) {
            stockData.setBuyStock(items);
        }
        else {
            stockData.setSellStock(items);
        }
    }

    @Override
    @NotNull
    protected Function<ResultSet, ShopUser> createUserFunction() {
        return resultSet -> {
            try {
                UUID uuid = UUID.fromString(resultSet.getString(COLUMN_USER_ID.getName()));
                String name = resultSet.getString(COLUMN_USER_NAME.getName());
                long dateCreated = resultSet.getLong(COLUMN_USER_DATE_CREATED.getName());
                long date = resultSet.getLong(COLUMN_USER_LAST_ONLINE.getName());

                UserSettings settings = GSON.fromJson(resultSet.getString(COL_USER_SETTINGS.getName()), new TypeToken<UserSettings>() {}.getType());

                return new ShopUser(uuid, name, dateCreated, date, settings);
            }
            catch (SQLException exception) {
                exception.printStackTrace();
                return null;
            }
        };
    }

    @Override
    protected void addUpsertQueryData(@NotNull ValuedQuery<?, ShopUser> query) {
        query.setValue(COL_USER_SETTINGS, user -> GSON.toJson(user.getSettings()));
    }

    @Override
    protected void addSelectQueryData(@NotNull SelectQuery<ShopUser> query) {
        query.column(COL_USER_SETTINGS);
    }

    @Override
    protected void addTableColumns(@NotNull List<Column> columns) {
        columns.add(COL_USER_SETTINGS);
    }



    @NotNull
    public List<StockData> loadStockDatas() {
        return this.select(this.tableStockData, DataQueries.STOCK_DATA_LOADER, SelectQuery::all);
    }

    @NotNull
    public List<PriceData> loadPriceDatas() {
        return this.select(this.tablePriceData, DataQueries.PRICE_DATA_LOADER, SelectQuery::all);
    }

    @NotNull
    public List<RotationData> loadRotationDatas() {
        return this.select(this.tableRotationData, DataQueries.ROTATION_DATA_LOADER, SelectQuery::all);
    }

    public void insertStockData(@NotNull StockData data) {
        this.insert(this.tableStockData, DataQueries.STOCK_DATA_INSERT, data);
    }

    public void insertPriceData(@NotNull PriceData data) {
        this.insert(this.tablePriceData, DataQueries.PRICE_DATA_INSERT, data);
    }

    public void insertRotationData(@NotNull RotationData rotationData) {
        this.insert(this.tableRotationData, DataQueries.ROTATION_DATA_INSERT, rotationData);
    }

    public void updateStockDatas(@NotNull Set<StockData> dataSet) {
        this.update(this.tableStockData, DataQueries.STOCK_DATA_UPDATE, dataSet);
    }

    public void updatePriceDatas(@NotNull Set<PriceData> dataSet) {
        this.update(this.tablePriceData, DataQueries.PRICE_DATA_UPDATE, dataSet);
    }

    public void updateRotationDatas(@NotNull Set<RotationData> dataSet) {
        this.update(this.tableRotationData, DataQueries.ROTATION_DATA_UPDATE, dataSet);
    }

    public void deleteRotationData(@NotNull VirtualShop shop) {
        this.delete(this.tableRotationData, DataQueries.ROTATION_DATA_DELETE_BY_SHOP, shop);
    }

    public void deleteRotationData(@NotNull Rotation rotation) {
        this.delete(this.tableRotationData, DataQueries.ROTATION_DATA_DELETE_BY_SELF, rotation);
    }

    public void deleteStockData(@NotNull Shop shop) {
        DeleteQuery<Shop> query = new DeleteQuery<>();
        query.whereIgnoreCase(DataHandler.COLUMN_GEN_SHOP_ID, WhereOperator.EQUAL, Shop::getId);
        this.delete(this.tableStockData, query, shop);
    }

    public void deleteStockData(@NotNull VirtualProduct product) {
        this.deleteStockDatas(Lists.newSet(product));
    }

    public void deleteStockDatas(@NotNull Set<Product> products) {
        DeleteQuery<Product> query = new DeleteQuery<>();
        query.whereIgnoreCase(DataHandler.COLUMN_GEN_SHOP_ID, WhereOperator.EQUAL, p -> p.getShop().getId());
        query.whereIgnoreCase(DataHandler.COLUMN_GEN_PRODUCT_ID, WhereOperator.EQUAL, Product::getId);
        this.delete(this.tableStockData, query, products);
    }



    public void deletePriceData(@NotNull Shop shop) {
        this.deletePriceData(shop.getId());
    }

    public void deletePriceData(@NotNull String shopId) {
        DeleteQuery<String> query = new DeleteQuery<>();
        query.whereIgnoreCase(DataHandler.COLUMN_GEN_SHOP_ID, WhereOperator.EQUAL, id -> shopId);
        this.delete(this.tablePriceData, query, shopId);
    }

    public void deletePriceData(@NotNull Product product) {
        this.deletePriceDatas(Lists.newSet(product));
    }

    public void deletePriceDatas(@NotNull Set<Product> products) {
        DeleteQuery<Product> query = new DeleteQuery<>();
        query.whereIgnoreCase(DataHandler.COLUMN_GEN_SHOP_ID, WhereOperator.EQUAL, p -> p.getShop().getId());
        query.whereIgnoreCase(DataHandler.COLUMN_GEN_PRODUCT_ID, WhereOperator.EQUAL, Product::getId);
        this.delete(this.tablePriceData, query, products);
    }

}
