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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public void openResidenceMenu(Player player) {
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

        int slot = 18;
        for (String resName : residences) {
            if (slot >= 36) break;
            ItemStack resItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta resMeta = resItem.getItemMeta();
            resMeta.setDisplayName("§a§l" + resName);
            resMeta.setLore(Collections.singletonList("§7点击管理此领地"));
            resItem.setItemMeta(resMeta);
            inventory.setItem(slot, resItem);
            slot++;
        }

        addPlaceholderItem(inventory, 36, player, Material.GOLD_INGOT, "§a持有领地", "%residence_user_amount%/%residence_user_maxres%");
        addPlaceholderItem(inventory, 37, player, Material.EMERALD, "§a子领地上限", "%residence_user_maxsub%");
        addPlaceholderItem(inventory, 38, player, Material.BLUE_STAINED_GLASS_PANE, "§a东西向规格", "%residence_user_maxew%");
        addPlaceholderItem(inventory, 39, player, Material.RED_STAINED_GLASS_PANE, "§a南北向规格", "%residence_user_maxns%");
        addPlaceholderItem(inventory, 40, player, Material.GREEN_STAINED_GLASS_PANE, "§a上下向规格", "%residence_user_maxud%");
        addPlaceholderItem(inventory, 41, player, Material.PURPLE_STAINED_GLASS_PANE, "§a租借上限", "%residence_user_maxrents%");
        addPlaceholderItem(inventory, 42, player, Material.CLOCK, "§a租期上限", "%residence_user_maxrentdays%天");
        addPlaceholderItem(inventory, 43, player, Material.DIAMOND, "§a每格价格", "%residence_user_blockcost%金币");

        addCurrentResidenceItem(inventory, 45, player, "§a当前领地", "%residence_user_current_res%");
        addCurrentResidenceItem(inventory, 46, player, "§a领地主人", "%residence_user_current_owner%");
        addCurrentResidenceItem(inventory, 47, player, "§a占地面积", "%residence_user_current_ssize%");
        addCurrentResidenceItem(inventory, 48, player, "§a领地售价", "%residence_user_current_saleprice%");
        addCurrentResidenceItem(inventory, 49, player, "§a租价", "%residence_user_current_rentprice%");
        addCurrentResidenceItem(inventory, 50, player, "§a租客", "%residence_user_current_rentedby%");
        addCurrentResidenceItem(inventory, 51, player, "§a租期剩余", "%residence_user_current_rentdays%天");
        addCurrentResidenceItem(inventory, 52, player, "§a领地银行余额", "%residence_user_current_bank%");

        ItemStack backToMain = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = backToMain.getItemMeta();
        backMeta.setDisplayName("§c§l返回主菜单");
        backMeta.setLore(Collections.singletonList("§7点击返回服务器主菜单"));
        backToMain.setItemMeta(backMeta);
        inventory.setItem(53, backToMain);

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
                rentPrice = "§7租价: " + residence.getRentable().cost + " 金币/天";
            }
        }
        String autoRent = "§7无";
        addCommandItem(inventory, 14, Material.GOLD_BLOCK, "§b买下该领地", "/res market buy " + residenceName, sellPrice);
        addCommandItem(inventory, 15, Material.EMERALD_BLOCK, "§b租下该领地", "/res market rent " + residenceName + " true", rentPrice);
        addCommandItem(inventory, 16, Material.CLOCK, "§b自动续租", "/res market autopay " + residenceName, autoRent);

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
        addCommandItem(inventory, 32, Material.GOLD_BLOCK, "§b出售领地", "/res market sell " + residenceName, "§7左键出售 | 右键取消出售");
        addCommandItem(inventory, 33, Material.EMERALD_BLOCK, "§b出租领地", "/res market rentable " + residenceName, "§7左键出租 | 右键取消出租");
        addCommandItem(inventory, 34, Material.BOOK, "§b领地聊天", "/res rc " + residenceName, "§7加入领地聊天频道");
        addCommandItem(inventory, 41, Material.BARRIER, "§b删除领地", "/res remove " + residenceName, "§7删除此领地");
        addCommandItem(inventory, 42, Material.PLAYER_HEAD, "§b赠送领地", "/res give " + residenceName, "§7用法: /res give <领地> <玩家>");
        addCommandItem(inventory, 43, Material.DIAMOND, "§b银行存款", "/res bank deposit " + residenceName, "§7左键存入 | 右键取出");

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

    private void addCurrentResidenceItem(Inventory inventory, int slot, Player player, String name, String placeholder) {
        String value = PlaceholderAPI.setPlaceholders(player, placeholder);
        boolean hasValue = !value.isEmpty() && !value.equals(placeholder);
        ItemStack item = new ItemStack(Material.REDSTONE_LAMP);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Collections.singletonList("§7" + (hasValue ? value : "无")));
        if (hasValue) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
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
        }
    }

    private void handleResidenceMenuClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();

        if (slot >= 18 && slot < 36) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.GRASS_BLOCK && clicked.hasItemMeta()) {
                String displayName = clicked.getItemMeta().getDisplayName();
                if (displayName != null && displayName.startsWith("§a§l")) {
                    String residenceName = displayName.substring(4);
                    openResidenceManageMenu(player, residenceName);
                    return;
                }
            }
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
            case 53:
                player.closeInventory();
                player.performCommand("menu");
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

        if (!isOwner) {
            if (slot == 14) {
                player.closeInventory();
                player.performCommand("res market buy " + residenceName);
            } else if (slot == 15) {
                player.closeInventory();
                if (isRightClick) {
                    player.performCommand("res market rent " + residenceName + " false");
                } else {
                    player.performCommand("res market rent " + residenceName + " true");
                }
            } else if (slot == 16) {
                player.closeInventory();
                if (isRightClick) {
                    player.performCommand("res market autopay false");
                } else {
                    player.performCommand("res market autopay true");
                }
            }
            return;
        }

        switch (slot) {
            case 14:
                player.closeInventory();
                player.chat("/res rename " + residenceName);
                break;
            case 15:
                player.closeInventory();
                player.performCommand("res set " + residenceName);
                break;
            case 16:
                player.closeInventory();
                player.chat("/res message " + residenceName + " enter");
                break;
            case 23:
                player.closeInventory();
                player.chat("/res message " + residenceName + " leave");
                break;
            case 24:
                player.closeInventory();
                player.chat("/res pset " + residenceName);
                break;
            case 25:
                player.closeInventory();
                player.performCommand("res shop");
                break;
            case 32:
                player.closeInventory();
                if (isRightClick) {
                    player.performCommand("res market unsell " + residenceName);
                } else {
                    player.chat("/res market sell " + residenceName);
                }
                break;
            case 33:
                player.closeInventory();
                if (isRightClick) {
                    player.performCommand("res market unrent " + residenceName);
                } else {
                    player.chat("/res market rentable " + residenceName);
                }
                break;
            case 34:
                player.closeInventory();
                player.performCommand("res rc " + residenceName);
                break;
            case 41:
                openConfirmDeleteMenu(player, residenceName);
                break;
            case 42:
                player.closeInventory();
                player.chat("/res give " + residenceName);
                break;
            case 43:
                player.closeInventory();
                if (isRightClick) {
                    player.chat("/res bank withdraw " + residenceName);
                } else {
                    player.chat("/res bank deposit " + residenceName);
                }
                break;
        }
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
}