package com.rushCT;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EconomySystem implements Listener {
    private static Economy econ = null;
    private final JavaPlugin plugin;

    public EconomySystem(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        setupEconomy();
    }

    private boolean setupEconomy() {
        if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            this.plugin.getLogger().severe("无法找到Vault插件！");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            this.plugin.getLogger().severe("无法找到经济服务提供商！");
            return false;
        }
        econ = rsp.getProvider();
        this.plugin.getLogger().info("成功连接到Vault经济系统！");
        return econ != null;
    }

    public boolean hasEconomy() {
        return econ != null;
    }

    public double getBalance(Player player) {
        if (econ != null) {
            return econ.getBalance(player);
        }
        return 0.0d;
    }

    public boolean depositPlayer(Player player, double amount) {
        if (econ != null) {
            return econ.depositPlayer(player, amount).transactionSuccess();
        }
        return false;
    }

    public boolean withdrawPlayer(Player player, double amount) {
        if (econ != null) {
            return econ.withdrawPlayer(player, amount).transactionSuccess();
        }
        return false;
    }

    public String format(double amount) {
        if (econ != null) {
            return econ.format(amount);
        }
        return String.valueOf(amount);
    }

    public static Economy getEconomy() {
        return econ;
    }

    private final java.util.Map<java.util.UUID, Integer> residencePageCache = new java.util.HashMap<>();

    public void openResidenceMenu(Player player) {
        openResidenceMenu(player, 0);
    }

    public void openResidenceMenu(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(new ResidenceMenuHolder(), 54, "领地系统");

        for (int i = 0; i < 54; i++) {
            ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = background.getItemMeta();
            meta.setDisplayName("§7");
            background.setItemMeta(meta);
            inventory.setItem(i, background);
        }

        addMainMenuItems(inventory, 3);

        ItemStack greenGlass = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta greenMeta = greenGlass.getItemMeta();
        greenMeta.setDisplayName("§7");
        greenGlass.setItemMeta(greenMeta);
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, greenGlass);
        }

        List<String> residences = new ArrayList<>();
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Residence")) {
                Residence res = Residence.getInstance();
                if (res != null) {
                    List<String> playerResList = res.getResidenceManager().getResidenceList(player.getName(), false, false);
                    if (playerResList != null) {
                        residences.addAll(playerResList);
                    }
                }
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("获取玩家领地列表时出错: " + e.getMessage());
            e.printStackTrace();
        }

        int itemsPerPage = 7;
        int totalPages = (int) Math.ceil((double) residences.size() / itemsPerPage);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, residences.size());

        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName("§a§l上一页");
            prevMeta.setLore(Collections.singletonList("§7点击查看上一页"));
            prevPage.setItemMeta(prevMeta);
            inventory.setItem(18, prevPage);
        }

        int slotIndex = 19;
        for (int i = startIndex; i < endIndex; i++) {
            String resName = residences.get(i);
            ItemStack resItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta resMeta = resItem.getItemMeta();
            resMeta.setDisplayName("§a§l" + resName);
            resMeta.setLore(Collections.singletonList("§7点击管理此领地"));
            resItem.setItemMeta(resMeta);
            inventory.setItem(slotIndex, resItem);
            slotIndex++;
        }

        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName("§a§l下一页");
            nextMeta.setLore(Collections.singletonList("§7点击查看下一页"));
            nextPage.setItemMeta(nextMeta);
            inventory.setItem(26, nextPage);
        }

        for (int i = 27; i < 36; i++) {
            inventory.setItem(i, greenGlass);
        }

        addPlaceholderItem(inventory, 36, player, Material.GOLD_INGOT, "§a持有领地", "%residence_user_amount%/%residence_user_maxres%");
        addPlaceholderItem(inventory, 37, player, Material.EMERALD, "§a子领地上限", "%residence_user_maxsub%");

        ItemStack specItem = new ItemStack(Material.PAPER);
        ItemMeta specMeta = specItem.getItemMeta();
        specMeta.setDisplayName("§a领地规格");
        String ew = PlaceholderAPI.setPlaceholders(player, "%residence_user_maxew%");
        String ns = PlaceholderAPI.setPlaceholders(player, "%residence_user_maxns%");
        String ud = PlaceholderAPI.setPlaceholders(player, "%residence_user_maxud%");
        specMeta.setLore(java.util.Arrays.asList("§7东西向: " + ew, "§7南北向: " + ns, "§7上下向: " + ud));
        specItem.setItemMeta(specMeta);
        inventory.setItem(38, specItem);

        addPlaceholderItem(inventory, 39, player, Material.PURPLE_STAINED_GLASS_PANE, "§a租借上限", "%residence_user_maxrents%");
        addPlaceholderItem(inventory, 40, player, Material.CLOCK, "§a租期上限", "%residence_user_maxrentdays%天");
        addPlaceholderItem(inventory, 41, player, Material.DIAMOND, "§a每格价格", "%residence_user_blockcost%金币");

        ItemStack currentInfo = new ItemStack(Material.BOOK);
        ItemMeta currentMeta = currentInfo.getItemMeta();
        currentMeta.setDisplayName("§a当前所处领地信息");
        List<String> currentLore = new ArrayList<>();
        currentLore.add("§7领地: " + PlaceholderAPI.setPlaceholders(player, "%residence_user_current_res%"));
        currentLore.add("§7主人: " + PlaceholderAPI.setPlaceholders(player, "%residence_user_current_owner%"));
        currentLore.add("§7面积: " + PlaceholderAPI.setPlaceholders(player, "%residence_user_current_ssize%"));
        currentLore.add("§7售价: " + PlaceholderAPI.setPlaceholders(player, "%residence_user_current_saleprice%"));
        currentLore.add("§7租价: " + PlaceholderAPI.setPlaceholders(player, "%residence_user_current_rentprice%"));
        currentLore.add("§7租客: " + PlaceholderAPI.setPlaceholders(player, "%residence_user_current_rentedby%"));
        currentLore.add("§7租期剩余: " + PlaceholderAPI.setPlaceholders(player, "%residence_user_current_rentdays%天"));
        currentMeta.setLore(currentLore);
        currentInfo.setItemMeta(currentMeta);
        inventory.setItem(42, currentInfo);

        for (int i = 45; i <= 47; i++) {
            ItemStack sellItem = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta sellMeta = sellItem.getItemMeta();
            sellMeta.setDisplayName("§a出售领地列表");
            sellMeta.setLore(Collections.singletonList("§7点击打开出售领地菜单"));
            sellItem.setItemMeta(sellMeta);
            inventory.setItem(i, sellItem);
        }

        for (int i = 48; i <= 50; i++) {
            ItemStack rentItem = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta rentMeta = rentItem.getItemMeta();
            rentMeta.setDisplayName("§a出租领地列表");
            rentMeta.setLore(Collections.singletonList("§7点击打开出租领地菜单"));
            rentItem.setItemMeta(rentMeta);
            inventory.setItem(i, rentItem);
        }

        for (int i = 51; i <= 53; i++) {
            ItemStack backToMain = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta backMeta = backToMain.getItemMeta();
            backMeta.setDisplayName("§c§l返回主菜单");
            backMeta.setLore(Collections.singletonList("§7点击返回服务器主菜单"));
            backToMain.setItemMeta(backMeta);
            inventory.setItem(i, backToMain);
        }

        residencePageCache.put(player.getUniqueId(), page);

        player.openInventory(inventory);
    }

    public void openResidenceManageMenu(Player player, String residenceName) {
        boolean isOwner = false;
        ClaimedResidence residence = null;

        if (Bukkit.getPluginManager().isPluginEnabled("Residence")) {
            Residence res = Residence.getInstance();
            if (res != null) {
                residence = res.getResidenceManager().getByName(residenceName);
                if (residence != null && residence.isOwner(player)) {
                    isOwner = true;
                }
            }
        }

        if (isOwner) {
            openResidenceOwnerMenu(player, residenceName, residence);
        } else {
            openResidenceInfoMenu(player, residenceName, residence);
        }
    }

    private void openResidenceInfoMenu(Player player, String residenceName, ClaimedResidence residence) {
        Inventory inventory = Bukkit.createInventory(new ResidenceManageHolder(residenceName), 54, "领地信息 - " + residenceName);

        ItemStack greenGlass = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta greenMeta = greenGlass.getItemMeta();
        greenMeta.setDisplayName("§7");
        greenGlass.setItemMeta(greenMeta);

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, greenGlass);
        }

        for (int row = 1; row <= 4; row++) {
            int startSlot = row * 9;
            inventory.setItem(startSlot, greenGlass);
            inventory.setItem(startSlot + 4, greenGlass);
            inventory.setItem(startSlot + 8, greenGlass);
        }

        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 3; col++) {
                int slot = row * 9 + col;
                ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = grayPane.getItemMeta();
                meta.setDisplayName("§7");
                grayPane.setItemMeta(meta);
                inventory.setItem(slot, grayPane);
            }
            for (int col = 5; col <= 7; col++) {
                int slot = row * 9 + col;
                ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = grayPane.getItemMeta();
                meta.setDisplayName("§7");
                grayPane.setItemMeta(meta);
                inventory.setItem(slot, grayPane);
            }
        }

        for (int i = 45; i < 53; i++) {
            inventory.setItem(i, greenGlass);
        }

        ItemStack info = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§a§l" + residenceName);
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        String owner = "§7无";
        String bank = "§7无";
        String world = "§7无";
        String location = "§7无";
        String size = "§7无";
        String created = "§7无";
        String rentDays = "§7无";

        if (residence != null) {
            owner = "§7" + residence.getOwner();
            bank = "§7" + residence.getBank().getStoredMoneyFormated() + " 金币";
            world = "§7" + residence.getMainArea().getWorld().getName();

            org.bukkit.Location loc1 = residence.getMainArea().getLowLoc();
            org.bukkit.Location loc2 = residence.getMainArea().getHighLoc();
            int centerX = (loc1.getBlockX() + loc2.getBlockX()) / 2;
            int centerY = (loc1.getBlockY() + loc2.getBlockY()) / 2;
            int centerZ = (loc1.getBlockZ() + loc2.getBlockZ()) / 2;
            location = "§7(" + centerX + ", " + centerY + ", " + centerZ + ")";

            int ew = Math.abs(loc2.getBlockX() - loc1.getBlockX()) + 1;
            int ns = Math.abs(loc2.getBlockZ() - loc1.getBlockZ()) + 1;
            int ud = Math.abs(loc2.getBlockY() - loc1.getBlockY()) + 1;
            size = "§7" + ew + "x" + ns + "x" + ud;

            long createdTime = residence.getCreateTime();
            long now = System.currentTimeMillis();
            long diffMinutes = (now - createdTime) / 60000;
            long days = diffMinutes / 1440;
            long hours = (diffMinutes % 1440) / 60;
            long mins = diffMinutes % 60;
            if (days > 0) {
                created = "§7" + days + "天" + hours + "小时" + mins + "分";
            } else if (hours > 0) {
                created = "§7" + hours + "小时" + mins + "分";
            } else {
                created = "§7" + mins + "分";
            }

            if (residence.isRented()) {
                long expireTime = residence.getLeaseExpireTime();
                long nowTime = System.currentTimeMillis();
                long remainingDays = (expireTime - nowTime) / 86400000;
                if (remainingDays > 0) {
                    rentDays = "§7" + remainingDays + " 天";
                } else {
                    rentDays = "§7已到期";
                }
            }
        }

        addSimpleItem(inventory, 10, Material.PLAYER_HEAD, "§a所有者", owner);
        addSimpleItem(inventory, 11, Material.GOLD_INGOT, "§a银行余额", bank);
        addSimpleItem(inventory, 12, Material.WATER_BUCKET, "§a所处世界", world);
        addSimpleItem(inventory, 13, Material.GREEN_STAINED_GLASS_PANE, "§7", "");
        addSimpleItem(inventory, 19, Material.GRASS_BLOCK, "§a大小", size);
        addSimpleItem(inventory, 20, Material.CLOCK, "§a已创建时间", created);
        addSimpleItem(inventory, 21, Material.RECOVERY_COMPASS, "§a租期剩余时间", rentDays);

        addSimpleItem(inventory, 28, Material.COMPASS, "§a所处位置", location);

        String sellPrice = "§7无";
        String rentPrice = "§7无";
        if (residence != null) {
            if (residence.isForSell()) {
                sellPrice = "§7售价: " + residence.getSellPrice() + " 金币";
            }
            if (residence.isForRent()) {
                rentPrice = "§7" + residence.getRentable().cost + "/" + residence.getRentable().days + "天";
            }
        }
        String autoRentText = "§7设置自动续租";
        String unrentText = "§7点击退租";
        addCommandItem(inventory, 14, Material.GOLD_BLOCK, "§b买下该领地", "/res market buy " + residenceName, sellPrice);
        addCommandItem(inventory, 15, Material.EMERALD_BLOCK, "§b租下该领地", "/res market rent " + residenceName + " true", rentPrice);
        addCommandItem(inventory, 16, Material.CLOCK, "§b自动续租", "/res market autopay " + residenceName, autoRentText);
        addCommandItem(inventory, 23, Material.BARRIER, "§b退租", "/res market unrent " + residenceName, unrentText);

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§l返回领地菜单");
        back.setItemMeta(backMeta);
        inventory.setItem(53, back);

        player.openInventory(inventory);
    }

    private void openResidenceOwnerMenu(Player player, String residenceName, ClaimedResidence residence) {
        Inventory inventory = Bukkit.createInventory(new ResidenceManageHolder(residenceName), 54, "领地管理 - " + residenceName);

        ItemStack greenGlass = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta greenMeta = greenGlass.getItemMeta();
        greenMeta.setDisplayName("§7");
        greenGlass.setItemMeta(greenMeta);

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, greenGlass);
        }

        for (int row = 1; row <= 4; row++) {
            int startSlot = row * 9;
            inventory.setItem(startSlot, greenGlass);
            inventory.setItem(startSlot + 4, greenGlass);
            inventory.setItem(startSlot + 8, greenGlass);
        }

        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 3; col++) {
                int slot = row * 9 + col;
                ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = grayPane.getItemMeta();
                meta.setDisplayName("§7");
                grayPane.setItemMeta(meta);
                inventory.setItem(slot, grayPane);
            }
            for (int col = 5; col <= 7; col++) {
                int slot = row * 9 + col;
                ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = grayPane.getItemMeta();
                meta.setDisplayName("§7");
                grayPane.setItemMeta(meta);
                inventory.setItem(slot, grayPane);
            }
        }

        for (int i = 45; i < 53; i++) {
            inventory.setItem(i, greenGlass);
        }

        inventory.setItem(14, greenGlass);

        ItemStack info = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§a§l" + residenceName);
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        String owner = "§7无";
        String bank = "§7无";
        String world = "§7无";
        String location = "§7无";
        String size = "§7无";
        String created = "§7无";
        String rentDays = "§7无";

        if (residence != null) {
            owner = "§7" + residence.getOwner();
            bank = "§7" + residence.getBank().getStoredMoneyFormated() + " 金币";
            world = "§7" + residence.getMainArea().getWorld().getName();

            org.bukkit.Location loc1 = residence.getMainArea().getLowLoc();
            org.bukkit.Location loc2 = residence.getMainArea().getHighLoc();
            int centerX = (loc1.getBlockX() + loc2.getBlockX()) / 2;
            int centerY = (loc1.getBlockY() + loc2.getBlockY()) / 2;
            int centerZ = (loc1.getBlockZ() + loc2.getBlockZ()) / 2;
            location = "§7(" + centerX + ", " + centerY + ", " + centerZ + ")";

            int ew = Math.abs(loc2.getBlockX() - loc1.getBlockX()) + 1;
            int ns = Math.abs(loc2.getBlockZ() - loc1.getBlockZ()) + 1;
            int ud = Math.abs(loc2.getBlockY() - loc1.getBlockY()) + 1;
            size = "§7" + ew + "x" + ns + "x" + ud;

            long createdTime = residence.getCreateTime();
            long now = System.currentTimeMillis();
            long diffMinutes = (now - createdTime) / 60000;
            long days = diffMinutes / 1440;
            long hours = (diffMinutes % 1440) / 60;
            long mins = diffMinutes % 60;
            if (days > 0) {
                created = "§7" + days + "天" + hours + "小时" + mins + "分";
            } else if (hours > 0) {
                created = "§7" + hours + "小时" + mins + "分";
            } else {
                created = "§7" + mins + "分";
            }

            if (residence.isRented()) {
                long expireTime = residence.getLeaseExpireTime();
                long remainingDays = (expireTime - now) / 86400000;
                if (remainingDays > 0) {
                    rentDays = "§7" + remainingDays + " 天";
                } else {
                    rentDays = "§7已到期";
                }
            }
        }

        addSimpleItem(inventory, 10, Material.PLAYER_HEAD, "§a所有者", owner);
        addSimpleItem(inventory, 11, Material.GOLD_INGOT, "§a银行余额", bank);
        addSimpleItem(inventory, 12, Material.WATER_BUCKET, "§a所处世界", world);
        addSimpleItem(inventory, 19, Material.GRASS_BLOCK, "§a大小", size);
        addSimpleItem(inventory, 20, Material.CLOCK, "§a已创建时间", created);
        addSimpleItem(inventory, 21, Material.RECOVERY_COMPASS, "§a租期剩余时间", rentDays);
        addSimpleItem(inventory, 28, Material.COMPASS, "§a所处位置", location);

        addCommandItem(inventory, 14, Material.NAME_TAG, "§b重命名领地", "/res rename " + residenceName, "§7用法: /res rename <旧名称> <新名称>");
        addCommandItem(inventory, 15, Material.PAPER, "§b设置公共权限", "/res set " + residenceName, "§7设置领地公共权限");
        addCommandItem(inventory, 16, Material.OAK_SIGN, "§b进入消息", "/res message " + residenceName + " enter", "§7设置进入领地提示");
        addCommandItem(inventory, 23, Material.OAK_SIGN, "§b离开消息", "/res message " + residenceName + " leave", "§7设置离开领地提示");
        addCommandItem(inventory, 24, Material.PLAYER_HEAD, "§b玩家权限", "/res pset " + residenceName, "§7用法: /res pset <领地> [玩家] [权限] [true/false/remove]");
        addCommandItem(inventory, 25, Material.CHEST, "§b领地商店", "/res shop", "§7管理领地商店");
        addCommandItem(inventory, 32, Material.GOLD_BLOCK, "§b出售领地", "/res market sell " + residenceName, "§7左键:出售 | 右键:取消出售");
        addCommandItem(inventory, 33, Material.EMERALD_BLOCK, "§b出租领地", "/res market rentable " + residenceName, "§7左键:出租 | 右键:取消出租");
        addCommandItem(inventory, 34, Material.BOOK, "§b领地聊天", "/res rc " + residenceName, "§7加入领地聊天频道");
        addCommandItem(inventory, 42, Material.PLAYER_HEAD, "§b赠送领地", "/res give " + residenceName, "§7用法: /res give <领地> <玩家>");
        addCommandItem(inventory, 43, Material.DIAMOND, "§b银行存款", "/res bank deposit " + residenceName, "§7左键:存入 | 右键:取出");

        addSimpleItem(inventory, 41, Material.BARRIER, "§b删除领地", "§7删除此领地");

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§l返回领地菜单");
        back.setItemMeta(backMeta);
        inventory.setItem(53, back);

        player.openInventory(inventory);
    }

    private void addSimpleItem(Inventory inventory, int slot, Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Collections.singletonList(lore));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void addMainMenuItems(Inventory inventory, int greenSlot) {
        ItemStack signIn = new ItemStack(Material.COMPASS);
        ItemMeta signInMeta = signIn.getItemMeta();
        signInMeta.setDisplayName("§c§l签到系统");
        signInMeta.setLore(Collections.singletonList("§7点击打开签到菜单"));
        signIn.setItemMeta(signInMeta);
        inventory.setItem(0, signIn);

        ItemStack events = new ItemStack(Material.GOAT_HORN);
        ItemMeta eventsMeta = events.getItemMeta();
        eventsMeta.setDisplayName("§6§l活动系统");
        eventsMeta.setLore(Collections.singletonList("§7暂时未开放"));
        events.setItemMeta(eventsMeta);
        inventory.setItem(1, events);

        ItemStack economy = new ItemStack(Material.GOLD_INGOT);
        ItemMeta economyMeta = economy.getItemMeta();
        economyMeta.setDisplayName("§e§l经济系统");
        economyMeta.setLore(Collections.singletonList("§7暂时未开放"));
        economy.setItemMeta(economyMeta);
        inventory.setItem(2, economy);

        ItemStack greenGlass = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta greenMeta = greenGlass.getItemMeta();
        greenMeta.setDisplayName("§7");
        greenGlass.setItemMeta(greenMeta);
        inventory.setItem(greenSlot, greenGlass);

        ItemStack friends = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta friendsMeta = friends.getItemMeta();
        friendsMeta.setDisplayName("§b§l好友系统");
        friendsMeta.setLore(Collections.singletonList("§7点击打开好友菜单"));
        friends.setItemMeta(friendsMeta);
        inventory.setItem(4, friends);

        ItemStack quest = new ItemStack(Material.BOOK);
        ItemMeta questMeta = quest.getItemMeta();
        questMeta.setDisplayName("§9§l任务系统");
        questMeta.setLore(Collections.singletonList("§7暂时未开放"));
        quest.setItemMeta(questMeta);
        inventory.setItem(5, quest);

        ItemStack gift = new ItemStack(Material.SHULKER_BOX);
        ItemMeta giftMeta = gift.getItemMeta();
        giftMeta.setDisplayName("§d§l快递系统");
        giftMeta.setLore(Collections.singletonList("§7点击打开快递菜单"));
        gift.setItemMeta(giftMeta);
        inventory.setItem(6, gift);

        ItemStack teleport = new ItemStack(Material.ENDER_PEARL);
        ItemMeta teleportMeta = teleport.getItemMeta();
        teleportMeta.setDisplayName("§f§l传送系统");
        teleportMeta.setLore(Collections.singletonList("§7暂时未开放"));
        teleport.setItemMeta(teleportMeta);
        inventory.setItem(7, teleport);

        ItemStack admin = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta adminMeta = admin.getItemMeta();
        adminMeta.setDisplayName("§4§l管理员菜单");
        adminMeta.setLore(Collections.singletonList("§7需要管理员权限"));
        admin.setItemMeta(adminMeta);
        inventory.setItem(8, admin);
    }

    private void addPlaceholderItem(Inventory inventory, int slot, Player player, Material material, String name, String placeholder) {
        String value = PlaceholderAPI.setPlaceholders(player, placeholder);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (value.equals(placeholder)) {
            meta.setLore(Collections.singletonList("§7无"));
        } else {
            meta.setLore(Collections.singletonList(value));
        }
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void addCommandItem(Inventory inventory, int slot, Material material, String name, String command, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Collections.singletonList(lore));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void sendClickableCommand(Player player, String message, String command) {
        net.kyori.adventure.text.Component component = net.kyori.adventure.text.Component.text(message)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("点击粘贴指令")));
        player.sendMessage(component);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        if (holder instanceof ResidenceMenuHolder) {
            event.setCancelled(true);
            handleResidenceMenuClick(event, player);
        } else if (holder instanceof ResidenceManageHolder) {
            event.setCancelled(true);
            ResidenceManageHolder manageHolder = (ResidenceManageHolder) holder;
            handleResidenceManageClick(event, player, manageHolder.getResidenceName());
        } else if (holder instanceof ConfirmDeleteHolder) {
            event.setCancelled(true);
            ConfirmDeleteHolder deleteHolder = (ConfirmDeleteHolder) holder;
            handleConfirmDeleteClick(event, player, deleteHolder.getResidenceName());
        } else if (holder instanceof ConfirmUnrentHolder) {
            event.setCancelled(true);
            ConfirmUnrentHolder unrentHolder = (ConfirmUnrentHolder) holder;
            handleConfirmUnrentClick(event, player, unrentHolder.getResidenceName());
        } else if (holder instanceof SoldResidencesHolder) {
            event.setCancelled(true);
            handleSoldResidencesMenuClick(event, player);
        } else if (holder instanceof RentResidencesHolder) {
            event.setCancelled(true);
            handleRentResidencesMenuClick(event, player);
        } else if (holder instanceof ConfirmActionHolder) {
            event.setCancelled(true);
            ConfirmActionHolder actionHolder = (ConfirmActionHolder) holder;
            handleConfirmActionClick(event, player, actionHolder.getAction(), actionHolder.getResidenceName(), actionHolder.getAmount());
        }
    }

    private void handleResidenceMenuClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        Integer currentPage = residencePageCache.getOrDefault(player.getUniqueId(), 0);

        if (slot >= 18 && slot <= 26) {
            if (slot == 18) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.ARROW && clicked.hasItemMeta()) {
                    if (currentPage > 0) {
                        openResidenceMenu(player, currentPage - 1);
                    }
                }
            } else if (slot == 26) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.ARROW && clicked.hasItemMeta()) {
                    openResidenceMenu(player, currentPage + 1);
                }
            } else if (slot >= 19 && slot <= 25) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.GRASS_BLOCK && clicked.hasItemMeta()) {
                    String displayName = clicked.getItemMeta().getDisplayName();
                    if (displayName != null && displayName.startsWith("§a§l")) {
                        String residenceName = displayName.substring(4);
                        openResidenceManageMenu(player, residenceName);
                    }
                }
            }
            return;
        }

        if (slot >= 45 && slot <= 47) {
            openSoldResidencesMenu(player);
            return;
        }

        if (slot >= 48 && slot <= 50) {
            openRentResidencesMenu(player);
            return;
        }

        if (slot >= 51 && slot <= 53) {
            player.closeInventory();
            player.performCommand("menu");
            return;
        }

        switch (slot) {
            case 0:
                player.closeInventory();
                player.performCommand("daily-check");
                break;
            case 4:
                player.closeInventory();
                player.performCommand("friend");
                break;
            case 6:
                player.closeInventory();
                player.performCommand("gift");
                break;
            case 8:
                if (player.hasPermission("admin")) {
                    player.sendMessage("§a管理员菜单暂时未开放！");
                } else {
                    player.sendMessage("§c你没有权限访问管理员菜单！");
                }
                break;
            default:
                if (slot >= 0 && slot < 9 && slot != 3) {
                    player.sendMessage("§c该系统暂时未开放！");
                }
                break;
        }
    }

    private void handleResidenceManageClick(InventoryClickEvent event, Player player, String residenceName) {
        int slot = event.getRawSlot();
        boolean isRightClick = event.isRightClick();

        if (slot == 4) {
            player.closeInventory();
            player.performCommand("res tp " + residenceName);
            return;
        }

        if (slot == 53) {
            player.closeInventory();
            openResidenceMenu(player);
            return;
        }

        ClaimedResidence residence = null;
        if (Bukkit.getPluginManager().isPluginEnabled("Residence")) {
            Residence res = Residence.getInstance();
            if (res != null) {
                residence = res.getResidenceManager().getByName(residenceName);
            }
        }

        boolean isOwner = residence != null && residence.isOwner(player);

        if (isOwner) {
            switch (slot) {
                case 14:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res rename " + residenceName);
                    sendClickableCommand(player, "§7用法: /res rename <旧名称> <新名称>", "/res rename " + residenceName + " ");
                    break;
                case 15:
                    player.performCommand("res set " + residenceName);
                    player.sendMessage("§a已打开领地公共权限设置菜单");
                    break;
                case 16:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res message " + residenceName + " enter ");
                    player.sendMessage("§7设置进入领地提示");
                    break;
                case 23:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res message " + residenceName + " leave ");
                    player.sendMessage("§7设置离开领地提示");
                    break;
                case 24:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res pset " + residenceName + " ");
                    player.sendMessage("§7用法: /res pset <领地> [玩家] [权限] [true/false/remove]");
                    break;
                case 25:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res shop");
                    player.sendMessage("§7管理领地商店");
                    break;
                case 32:
                    player.closeInventory();
                    if (isRightClick) {
                        player.performCommand("res market unsell " + residenceName);
                        player.sendMessage("§a已取消出售领地");
                    } else {
                        sendClickableCommand(player, "§b点击这里: ", "/res market sell " + residenceName);
                        player.sendMessage("§7左键: /res market sell");
                        player.sendMessage("§7右键: /res market unsell");
                    }
                    break;
                case 33:
                    player.closeInventory();
                    if (isRightClick) {
                        player.performCommand("res market unrent " + residenceName);
                        player.sendMessage("§a已取消出租领地");
                    } else {
                        sendClickableCommand(player, "§b点击这里: ", "/res market rentable " + residenceName);
                        player.sendMessage("§7左键: /res market rentable");
                        player.sendMessage("§7右键: /res market unrent");
                    }
                    break;
                case 34:
                    player.performCommand("res rc " + residenceName);
                    player.sendMessage("§a已加入领地聊天频道");
                    break;
                case 41:
                    player.closeInventory();
                    openConfirmDeleteMenu(player, residenceName);
                    break;
                case 42:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res give " + residenceName + " ");
                    player.sendMessage("§7用法: /res give <领地> <玩家>");
                    break;
                case 43:
                    player.closeInventory();
                    if (isRightClick) {
                        sendClickableCommand(player, "§b点击这里: ", "/res bank withdraw " + residenceName);
                        player.sendMessage("§7右键: /res bank withdraw");
                    } else {
                        sendClickableCommand(player, "§b点击这里: ", "/res bank deposit " + residenceName);
                        player.sendMessage("§7左键: /res bank deposit");
                    }
                    break;
            }
        } else {
            switch (slot) {
                case 14:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res market buy " + residenceName);
                    break;
                case 15:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res market rent " + residenceName + " true");
                    break;
                case 16:
                    player.closeInventory();
                    sendClickableCommand(player, "§b点击这里: ", "/res market autopay " + residenceName);
                    break;
                case 23:
                    player.closeInventory();
                    openConfirmUnrentMenu(player, residenceName);
                    break;
            }
        }
    }

    private void openConfirmUnrentMenu(Player player, String residenceName) {
        Inventory inventory = Bukkit.createInventory(new ConfirmUnrentHolder(residenceName), 9, "确认退租");

        for (int i = 0; i < 9; i++) {
            ItemStack glass = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = glass.getItemMeta();
            meta.setDisplayName("§7");
            glass.setItemMeta(meta);
            inventory.setItem(i, glass);
        }

        ItemStack confirm = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a§l确认退租");
        confirmMeta.setLore(Collections.singletonList("§7点击后将执行退租操作"));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(3, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c§l取消操作");
        cancelMeta.setLore(Collections.singletonList("§7返回领地信息菜单"));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(5, cancel);

        player.openInventory(inventory);
    }

    private void openConfirmDeleteMenu(Player player, String residenceName) {
        Inventory inventory = Bukkit.createInventory(new ConfirmDeleteHolder(residenceName), 9, "确认删除领地");

        for (int i = 0; i < 9; i++) {
            ItemStack glass = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = glass.getItemMeta();
            meta.setDisplayName("§7");
            glass.setItemMeta(meta);
            inventory.setItem(i, glass);
        }

        ItemStack confirm = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a§l确认删除");
        confirmMeta.setLore(Collections.singletonList("§7点击后将永久删除此领地"));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(3, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c§l取消操作");
        cancelMeta.setLore(Collections.singletonList("§7返回领地管理菜单"));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(5, cancel);

        player.openInventory(inventory);
    }

    private void handleConfirmDeleteClick(InventoryClickEvent event, Player player, String residenceName) {
        int slot = event.getRawSlot();

        if (slot == 3) {
            player.closeInventory();
            player.performCommand("res remove " + residenceName);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.performCommand("res confirm");
            }, 10);
        } else if (slot == 5) {
            player.closeInventory();
            openResidenceManageMenu(player, residenceName);
        }
    }

    private void handleConfirmUnrentClick(InventoryClickEvent event, Player player, String residenceName) {
        int slot = event.getRawSlot();

        if (slot == 3) {
            player.closeInventory();
            player.performCommand("res market unrent " + residenceName);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.performCommand("res market confirm");
            }, 10);
        } else if (slot == 5) {
            player.closeInventory();
            openResidenceManageMenu(player, residenceName);
        }
    }

    private void openConfirmActionMenu(Player player, String action, String residenceName, double amount) {
        String title = action.equals("BUY") ? "确认购买领地" : "确认租用领地";
        Inventory inventory = Bukkit.createInventory(new ConfirmActionHolder(action, residenceName, amount), 9, title);

        for (int i = 0; i < 9; i++) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = glass.getItemMeta();
            meta.setDisplayName("§7");
            glass.setItemMeta(meta);
            inventory.setItem(i, glass);
        }

        ItemStack infoItem = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§a§l" + residenceName);
        List<String> lore = new ArrayList<>();
        if (action.equals("BUY")) {
            lore.add("§7价格: §e" + amount + " 金币");
        } else {
            lore.add("§7租金: §e" + amount + " 金币/天");
        }
        infoMeta.setLore(lore);
        infoItem.setItemMeta(infoMeta);
        inventory.setItem(4, infoItem);

        ItemStack confirm = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a§l确认");
        confirmMeta.setLore(Collections.singletonList("§7点击确认" + (action.equals("BUY") ? "购买" : "租用")));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(2, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c§l取消");
        cancelMeta.setLore(Collections.singletonList("§7点击取消"));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(6, cancel);

        player.openInventory(inventory);
    }

    private void handleConfirmActionClick(InventoryClickEvent event, Player player, String action, String residenceName, double amount) {
        int slot = event.getRawSlot();

        if (slot == 2) {
            player.closeInventory();
            if (action.equals("BUY")) {
                player.performCommand("res market buy " + residenceName);
            } else {
                player.performCommand("res market rent " + residenceName + " true");
            }
        } else if (slot == 6) {
            player.closeInventory();
            if (action.equals("BUY")) {
                openSoldResidencesMenu(player);
            } else {
                openRentResidencesMenu(player);
            }
        }
    }

    private void handleSoldResidencesMenuClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        Integer currentPage = soldPageCache.getOrDefault(player.getUniqueId(), 0);
        boolean isRightClick = event.isRightClick();

        if (slot >= 18 && slot < 45) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.GRASS_BLOCK && clicked.hasItemMeta()) {
                String displayName = clicked.getItemMeta().getDisplayName();
                if (displayName != null && displayName.startsWith("§a§l")) {
                    String residenceName = displayName.substring(4);

                    player.closeInventory();

                    if (isRightClick) {
                        openResidenceManageMenu(player, residenceName);
                    } else {
                        if (Bukkit.getPluginManager().isPluginEnabled("Residence")) {
                            Residence res = Residence.getInstance();
                            if (res != null) {
                                ClaimedResidence residence = res.getResidenceManager().getByName(residenceName);
                                if (residence != null) {
                                    double price = residence.getSellPrice();
                                    double balance = getBalance(player);
                                    if (balance >= price) {
                                        openConfirmActionMenu(player, "BUY", residenceName, price);
                                    } else {
                                        player.sendMessage("§c你没有足够的金币购买这个领地！");
                                        player.sendMessage("§c需要: §e" + price + " §c金币");
                                        player.sendMessage("§c你有: §e" + balance + " §c金币");
                                        openSoldResidencesMenu(player, currentPage);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        if (slot == 45) {
            if (currentPage > 0) {
                openSoldResidencesMenu(player, currentPage - 1);
            }
            return;
        }

        if (slot >= 46 && slot <= 48) {
            player.closeInventory();
            openResidenceMenu(player);
            return;
        }

        if (slot == 53) {
            openSoldResidencesMenu(player, currentPage + 1);
        }
    }

    private void handleRentResidencesMenuClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        Integer currentPage = rentPageCache.getOrDefault(player.getUniqueId(), 0);
        boolean isRightClick = event.isRightClick();

        if (slot >= 18 && slot < 45) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.DIRT_PATH && clicked.hasItemMeta()) {
                String displayName = clicked.getItemMeta().getDisplayName();
                if (displayName != null && displayName.startsWith("§a§l")) {
                    String residenceName = displayName.substring(4);

                    player.closeInventory();

                    if (isRightClick) {
                        openResidenceManageMenu(player, residenceName);
                    } else {
                        if (Bukkit.getPluginManager().isPluginEnabled("Residence")) {
                            Residence res = Residence.getInstance();
                            if (res != null) {
                                ClaimedResidence residence = res.getResidenceManager().getByName(residenceName);
                                if (residence != null) {
                                    double rentCost = residence.getRentable().cost;
                                    double balance = getBalance(player);
                                    if (balance >= rentCost) {
                                        openConfirmActionMenu(player, "RENT", residenceName, rentCost);
                                    } else {
                                        player.sendMessage("§c你没有足够的金币租用这个领地！");
                                        player.sendMessage("§c需要: §e" + rentCost + " §c金币/天");
                                        player.sendMessage("§c你有: §e" + balance + " §c金币");
                                        openRentResidencesMenu(player, currentPage);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        if (slot == 45) {
            if (currentPage > 0) {
                openRentResidencesMenu(player, currentPage - 1);
            }
            return;
        }

        if (slot >= 46 && slot <= 48) {
            player.closeInventory();
            openResidenceMenu(player);
            return;
        }

        if (slot == 53) {
            openRentResidencesMenu(player, currentPage + 1);
        }
    }

    private static class ResidenceMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class ResidenceManageHolder implements InventoryHolder {
        private final String residenceName;

        ResidenceManageHolder(String residenceName) {
            this.residenceName = residenceName;
        }

        String getResidenceName() {
            return residenceName;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class ConfirmDeleteHolder implements InventoryHolder {
        private final String residenceName;

        ConfirmDeleteHolder(String residenceName) {
            this.residenceName = residenceName;
        }

        String getResidenceName() {
            return residenceName;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class ConfirmUnrentHolder implements InventoryHolder {
        private final String residenceName;

        ConfirmUnrentHolder(String residenceName) {
            this.residenceName = residenceName;
        }

        String getResidenceName() {
            return residenceName;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class SoldResidencesHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class RentResidencesHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class ConfirmActionHolder implements InventoryHolder {
        private final String action;
        private final String residenceName;
        private final double amount;

        ConfirmActionHolder(String action, String residenceName, double amount) {
            this.action = action;
            this.residenceName = residenceName;
            this.amount = amount;
        }

        String getAction() {
            return action;
        }

        String getResidenceName() {
            return residenceName;
        }

        double getAmount() {
            return amount;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final java.util.Map<java.util.UUID, Integer> soldPageCache = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> rentPageCache = new java.util.HashMap<>();

    public void openSoldResidencesMenu(Player player) {
        openSoldResidencesMenu(player, 0);
    }

    public void openSoldResidencesMenu(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(new SoldResidencesHolder(), 54, "出售领地列表");

        for (int i = 0; i < 54; i++) {
            ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = background.getItemMeta();
            meta.setDisplayName("§7");
            background.setItemMeta(meta);
            inventory.setItem(i, background);
        }

        addMainMenuItems(inventory, 3);

        ItemStack greenGlass = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta greenMeta = greenGlass.getItemMeta();
        greenMeta.setDisplayName("§7");
        greenGlass.setItemMeta(greenMeta);
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, greenGlass);
        }

        List<String[]> soldResidences = new ArrayList<>();
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Residence")) {
                Residence res = Residence.getInstance();
                if (res != null) {
                    String[] allResidenceNames = res.getResidenceManager().getResidenceList();
                    for (String residenceName : allResidenceNames) {
                        ClaimedResidence residence = res.getResidenceManager().getByName(residenceName);
                        if (residence != null && residence.isForSell()) {
                            String world = residence.getMainArea().getWorld().getName();
                            org.bukkit.Location loc1 = residence.getMainArea().getLowLoc();
                            org.bukkit.Location loc2 = residence.getMainArea().getHighLoc();
                            int centerX = (loc1.getBlockX() + loc2.getBlockX()) / 2;
                            int centerY = (loc1.getBlockY() + loc2.getBlockY()) / 2;
                            int centerZ = (loc1.getBlockZ() + loc2.getBlockZ()) / 2;
                            String location = "(" + centerX + ", " + centerY + ", " + centerZ + ")";

                            int ew = Math.abs(loc2.getBlockX() - loc1.getBlockX()) + 1;
                            int ns = Math.abs(loc2.getBlockZ() - loc1.getBlockZ()) + 1;
                            int ud = Math.abs(loc2.getBlockY() - loc1.getBlockY()) + 1;
                            String size = ew + "x" + ns + "x" + ud;

                            String owner = residence.getOwner();
                            double price = residence.getSellPrice();

                            soldResidences.add(new String[]{residence.getName(), world, location, size, owner, String.valueOf(price)});
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("获取出售领地列表时出错: " + e.getMessage());
            e.printStackTrace();
        }

        int itemsPerPage = 21;
        int totalPages = (int) Math.ceil((double) soldResidences.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, soldResidences.size());

        int slot = 18;
        for (int i = startIndex; i < endIndex && slot < 45; i++) {
            String[] res = soldResidences.get(i);
            ItemStack resItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta resMeta = resItem.getItemMeta();
            resMeta.setDisplayName("§a§l" + res[0]);

            List<String> lore = new ArrayList<>();
            lore.add("§7世界: " + res[1]);
            lore.add("§7坐标: " + res[2]);
            lore.add("§7大小: " + res[3]);
            lore.add("§7所有者: " + res[4]);
            lore.add("§e价格: " + res[5] + " 金币");
            lore.add("");
            lore.add("§a左键: 购买");
            lore.add("§b右键: 查看属性");

            resMeta.setLore(lore);
            resItem.setItemMeta(resMeta);
            inventory.setItem(slot, resItem);
            slot++;
        }

        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName("§a§l上一页");
            prevMeta.setLore(Collections.singletonList("§7第 " + (page + 1) + " / " + totalPages + " 页"));
            prevPage.setItemMeta(prevMeta);
            inventory.setItem(45, prevPage);
        }

        for (int i = 46; i <= 48; i++) {
            ItemStack backBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta backMeta = backBtn.getItemMeta();
            backMeta.setDisplayName("§c§l返回领地主菜单");
            backBtn.setItemMeta(backMeta);
            inventory.setItem(i, backBtn);
        }

        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName("§a§l下一页");
            nextMeta.setLore(Collections.singletonList("§7第 " + (page + 1) + " / " + totalPages + " 页"));
            nextPage.setItemMeta(nextMeta);
            inventory.setItem(53, nextPage);
        }

        soldPageCache.put(player.getUniqueId(), page);
        player.openInventory(inventory);
    }

    public void openRentResidencesMenu(Player player) {
        openRentResidencesMenu(player, 0);
    }

    public void openRentResidencesMenu(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(new RentResidencesHolder(), 54, "出租领地列表");

        for (int i = 0; i < 54; i++) {
            ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = background.getItemMeta();
            meta.setDisplayName("§7");
            background.setItemMeta(meta);
            inventory.setItem(i, background);
        }

        addMainMenuItems(inventory, 3);

        ItemStack greenGlass = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta greenMeta = greenGlass.getItemMeta();
        greenMeta.setDisplayName("§7");
        greenGlass.setItemMeta(greenMeta);
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, greenGlass);
        }

        List<String[]> rentResidences = new ArrayList<>();
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Residence")) {
                Residence res = Residence.getInstance();
                if (res != null) {
                    String[] allResidenceNames = res.getResidenceManager().getResidenceList();
                    for (String residenceName : allResidenceNames) {
                        ClaimedResidence residence = res.getResidenceManager().getByName(residenceName);
                        if (residence != null && residence.isForRent() && !residence.isRented()) {
                            String world = residence.getMainArea().getWorld().getName();
                            org.bukkit.Location loc1 = residence.getMainArea().getLowLoc();
                            org.bukkit.Location loc2 = residence.getMainArea().getHighLoc();
                            int centerX = (loc1.getBlockX() + loc2.getBlockX()) / 2;
                            int centerY = (loc1.getBlockY() + loc2.getBlockY()) / 2;
                            int centerZ = (loc1.getBlockZ() + loc2.getBlockZ()) / 2;
                            String location = "(" + centerX + ", " + centerY + ", " + centerZ + ")";

                            int ew = Math.abs(loc2.getBlockX() - loc1.getBlockX()) + 1;
                            int ns = Math.abs(loc2.getBlockZ() - loc1.getBlockZ()) + 1;
                            int ud = Math.abs(loc2.getBlockY() - loc1.getBlockY()) + 1;
                            String size = ew + "x" + ns + "x" + ud;

                            String owner = residence.getOwner();
                            double rentCost = residence.getRentable().cost;
                            int rentDays = residence.getRentable().days;

                            rentResidences.add(new String[]{residence.getName(), world, location, size, owner, String.valueOf(rentCost), String.valueOf(rentDays)});
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("获取出租领地列表时出错: " + e.getMessage());
            e.printStackTrace();
        }

        int itemsPerPage = 21;
        int totalPages = (int) Math.ceil((double) rentResidences.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, rentResidences.size());

        int slot = 18;
        for (int i = startIndex; i < endIndex && slot < 45; i++) {
            String[] res = rentResidences.get(i);
            ItemStack resItem = new ItemStack(Material.DIRT_PATH);
            ItemMeta resMeta = resItem.getItemMeta();
            resMeta.setDisplayName("§a§l" + res[0]);

            List<String> lore = new ArrayList<>();
            lore.add("§7世界: " + res[1]);
            lore.add("§7坐标: " + res[2]);
            lore.add("§7大小: " + res[3]);
            lore.add("§7所有者: " + res[4]);
            lore.add("§e租金: " + res[5] + " 金币/天");
            lore.add("§e租期: " + res[6] + " 天");
            lore.add("");
            lore.add("§a左键: 租用");
            lore.add("§b右键: 查看属性");

            resMeta.setLore(lore);
            resItem.setItemMeta(resMeta);
            inventory.setItem(slot, resItem);
            slot++;
        }

        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName("§a§l上一页");
            prevMeta.setLore(Collections.singletonList("§7第 " + (page + 1) + " / " + totalPages + " 页"));
            prevPage.setItemMeta(prevMeta);
            inventory.setItem(45, prevPage);
        }

        for (int i = 46; i <= 48; i++) {
            ItemStack backBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta backMeta = backBtn.getItemMeta();
            backMeta.setDisplayName("§c§l返回领地主菜单");
            backBtn.setItemMeta(backMeta);
            inventory.setItem(i, backBtn);
        }

        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName("§a§l下一页");
            nextMeta.setLore(Collections.singletonList("§7第 " + (page + 1) + " / " + totalPages + " 页"));
            nextPage.setItemMeta(nextMeta);
            inventory.setItem(53, nextPage);
        }

        rentPageCache.put(player.getUniqueId(), page);
        player.openInventory(inventory);
    }
}
