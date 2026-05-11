package com.rushCT;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class RushCT extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration dataConfig;
    private File configFile;
    private YamlConfiguration config;
    private FriendSystem friendSystem;
    private EconomySystem economySystem;
    private MotdSystem motdSystem;
    private Map<UUID, List<Gift>> gifts = new HashMap();
    private Map<UUID, CheckInData> checkInData = new ConcurrentHashMap();
    private Map<String, List<MessageRecord>> messageTrackers = new ConcurrentHashMap();
    private Set<String> activePlusOneMessages = ConcurrentHashMap.newKeySet();
    private Map<UUID, List<Long>> messageTimestamps = new ConcurrentHashMap();
    private Set<UUID> mutedPlayers = ConcurrentHashMap.newKeySet();
    private Set<UUID> newPlayers = ConcurrentHashMap.newKeySet();
    private Map<UUID, Integer> helpPageCache = new ConcurrentHashMap<>();
    
    private static class MessageRecord {
        UUID playerUUID;
        long timestamp;
        
        MessageRecord(UUID playerUUID, long timestamp) {
            this.playerUUID = playerUUID;
            this.timestamp = timestamp;
        }
    }

    private enum GiftInventoryType {
        SEND,
        RECEIVE
    }

    public void onEnable() {
        getLogger().info("*ENABLING RushCT\n*v1.0.0\n*By IsabellaX\n \u3000\u3000 \u3000    ▃▆█▇▄▖\n\u3000\u3000  \u3000▟◤▖\u3000\u3000\u3000◥█▎\n\u3000\u3000\u3000◢◤\u3000 ▐\u3000\u3000\u3000\u3000▐▉\n\u3000▗◤\u3000\u3000\u3000▂\u3000▗▖\u3000\u3000▕█▎\n\u3000◤\u3000▗▅▖◥▄\u3000▀◣\u3000\u3000█▊\n▐\u3000▕▎◥▖◣◤\u3000\u3000\u3000\u3000◢██\n█◣\u3000◥▅█▀\u3000\u3000\u3000\u3000▐██◤\n▐█▙▂\u3000\u3000\u3000   ◢██◤\n\u3000◥██◣\u3000\u3000\u3000\u3000◢▄◤\n\u3000\u3000\u3000▀██▅▇▀\n呢 哼 哼\n啊啊啊啊啊~啊啊啊啊，\n啊啊啊啊啊啊啊啊-额啊啊，\n");
        getServer().getPluginManager().registerEvents(this, this);
        loadConfig();
        loadData();
        this.friendSystem = new FriendSystem(this);
        this.economySystem = new EconomySystem(this);
        this.motdSystem = new MotdSystem(this);
    }

    public void onDisable() {
        getLogger().info("*DISABLING RushCT\n \u3000\u3000 \u3000    ▃▆█▇▄▖\n\u3000\u3000  \u3000▟◤▖\u3000\u3000\u3000◥█▎\n\u3000\u3000\u3000◢◤\u3000 ▐\u3000\u3000\u3000\u3000▐▉\n\u3000▗◤\u3000\u3000\u3000▂\u3000▗▖\u3000\u3000▕█▎\n\u3000◤\u3000▗▅▖◥▄\u3000▀◣\u3000\u3000█▊\n▐\u3000▕▎◥▖◣◤\u3000\u3000\u3000\u3000◢██\n█◣\u3000◥▅█▀\u3000\u3000\u3000\u3000▐██◤\n▐█▙▂\u3000\u3000\u3000   ◢██◤\n\u3000◥██◣\u3000\u3000\u3000\u3000◢▄◤\n\u3000\u3000\u3000▀██▅▇▀\n逸一时，误一世，\n逸久逸久罢已龄。");
        saveData();
    }

    public int getMaxConsecutiveDays(UUID playerUUID) {
        if (this.checkInData.containsKey(playerUUID)) {
            return this.checkInData.get(playerUUID).getMaxConsecutiveDays();
        }
        return 0;
    }

    private void loadConfig() {
        this.configFile = new File(getDataFolder(), "config.yml");
        if (!this.configFile.exists()) {
            this.configFile.getParentFile().mkdirs();
            try {
                this.configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(this.configFile);
        
        // 添加加入服务器日期的等级计算标准项
        if (!this.config.isConfigurationSection("joinDateLevels")) {
            this.config.createSection("joinDateLevels");
            this.config.set("joinDateLevels.rainbow", "2025-08-31");
            this.config.set("joinDateLevels.gold", "2026-02-27");
            this.config.set("joinDateLevels.silver", "2026-08-31");
            this.config.set("joinDateLevels.copper", "2027-02-27");
            this.config.set("joinDateLevels.purple", "2027-08-31");
            this.config.set("joinDateLevels.red", "2028-02-27");
            this.config.set("joinDateLevels.yellow", "2028-08-31");
            this.config.set("joinDateLevels.green", "2029-02-27");
            this.config.set("joinDateLevels.blue", "2029-08-31");
            this.config.set("joinDateLevels.white", "2030-02-27");
            try {
                this.config.save(this.configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // 添加性别列表
        if (!this.config.isConfigurationSection("genders")) {
            this.config.createSection("genders");
            this.config.createSection("genders.items");
            
            // 性别1 - 默认
            this.config.set("genders.items.1.name", "&f默认");
            this.config.set("genders.items.1.english", "&6Default");
            this.config.set("genders.items.1.description", "&r默认性别");
            
            // 性别2 - 男
            this.config.set("genders.items.2.name", "&b男");
            this.config.set("genders.items.2.english", "&6Male");
            this.config.set("genders.items.2.description", "&r男性玩家");
            
            // 性别3 - 女
            this.config.set("genders.items.3.name", "&c女");
            this.config.set("genders.items.3.english", "&6Female");
            this.config.set("genders.items.3.description", "&r女性玩家");
            
            try {
                this.config.save(this.configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadData() {
        this.dataFile = new File(getDataFolder(), "gifts.yml");
        if (!this.dataFile.exists()) {
            this.dataFile.getParentFile().mkdirs();
            try {
                this.dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
        for (String uuidStr : this.dataConfig.getKeys(false)) {
            UUID recipientUUID = UUID.fromString(uuidStr);
            List<Gift> recipientGifts = new ArrayList<>();
            for (String giftKey : this.dataConfig.getConfigurationSection(uuidStr).getKeys(false)) {
                Gift gift = Gift.loadFromConfig(this.dataConfig, uuidStr + "." + giftKey);
                if (gift != null) {
                    recipientGifts.add(gift);
                }
            }
            this.gifts.put(recipientUUID, recipientGifts);
        }
        // 加载签到数据
        File checkInFile = new File(getDataFolder(), "checkindata.yml");
        if (checkInFile.exists()) {
            YamlConfiguration checkInConfig = YamlConfiguration.loadConfiguration(checkInFile);
            if (checkInConfig.isConfigurationSection("checkInData")) {
                for (String uuidStr : checkInConfig.getConfigurationSection("checkInData").getKeys(false)) {
                    UUID playerUUID = UUID.fromString(uuidStr);
                    CheckInData data = CheckInData.loadFromConfig(checkInConfig, "checkInData." + uuidStr);
                    if (data != null) {
                        this.checkInData.put(playerUUID, data);
                    }
                }
            }
        }
    }

    private void saveData() {
        this.dataConfig = new YamlConfiguration();
        for (Map.Entry<UUID, List<Gift>> entry : this.gifts.entrySet()) {
            String uuidStr = entry.getKey().toString();
            List<Gift> recipientGifts = entry.getValue();
            for (int i = 0; i < recipientGifts.size(); i++) {
                recipientGifts.get(i).saveToConfig(this.dataConfig, uuidStr + ".gift" + i);
            }
        }
        try {
            this.dataConfig.save(this.dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 保存签到数据
        File checkInFile = new File(getDataFolder(), "checkindata.yml");
        if (!checkInFile.exists()) {
            checkInFile.getParentFile().mkdirs();
            try {
                checkInFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        YamlConfiguration checkInConfig = new YamlConfiguration();
        for (Map.Entry<UUID, CheckInData> entry : this.checkInData.entrySet()) {
            String uuidStr = entry.getKey().toString();
            CheckInData data = entry.getValue();
            data.saveToConfig(checkInConfig, "checkInData." + uuidStr);
        }
        try {
            checkInConfig.save(checkInFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();
        if (commandName.equals("friend") || commandName.equals("hello") || commandName.equals("passport") || commandName.equals("genderpage")) {
            return this.friendSystem.onCommand(sender, command, label, args);
        }
        
        if (commandName.equals("helppage")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令！");
                return true;
            }
            Player player = (Player) sender;
            if (args.length == 1) {
                try {
                    int page = Integer.parseInt(args[0]);
                    showHelpPage(player, page);
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("§c请输入有效的页码！");
                    return true;
                }
            }
            player.sendMessage("§c用法: /helppage <页码>");
            return true;
        }

        if (commandName.equals("rushct")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!(sender instanceof Player) || sender.hasPermission("rushct.admin")) {
                    getLogger().info("正在重新加载插件...");
                    onDisable();
                    loadConfig();
                    loadData();
                    this.motdSystem.reloadMotdConfig();
                    getLogger().info("插件重新加载完成！");
                    sender.sendMessage("§a插件重新加载完成！");
                } else {
                    sender.sendMessage("§c你没有权限使用此命令！");
                }
                return true;
            }
            
            if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
                try {
                    int page = Integer.parseInt(args[1]);
                    if (sender instanceof Player) {
                        showHelpPage((Player) sender, page);
                    } else {
                        showHelpPage(sender, page);
                    }
                    return true;
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c请输入有效的页码！");
                    return true;
                }
            }
            
            if (sender instanceof Player) {
                showHelpPage((Player) sender, 1);
            } else {
                showHelpPage(sender, 1);
            }
            return true;
        }


        if (commandName.equals("menu")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令！");
                return true;
            }
            openMainMenu((Player) sender);
            return true;
        }
        if (commandName.equals("daily-check")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令！");
                return true;
            }
            openCheckInMenu((Player) sender);
            return true;
        }
        if (commandName.equals("resgui")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令！");
                return true;
            }
            Player player = (Player) sender;
            if (args.length >= 1) {
                String residenceName = String.join(" ", args);
                if (Bukkit.getPluginManager().isPluginEnabled("Residence")) {
                    com.bekvon.bukkit.residence.Residence res = com.bekvon.bukkit.residence.Residence.getInstance();
                    if (res != null) {
                        com.bekvon.bukkit.residence.protection.ClaimedResidence residence = res.getResidenceManager().getByName(residenceName);
                        if (residence == null) {
                            player.sendMessage("§c领地 [" + residenceName + "] 不存在！");
                            return true;
                        }
                    }
                }
                this.economySystem.openResidenceManageMenu(player, residenceName);
            } else {
                this.economySystem.openResidenceMenu(player);
            }
            return true;
        }
        if (commandName.equals("checkplaytime")) {
            if (args.length == 4 && (sender.hasPermission("rushct.admin") || !(sender instanceof Player))) {
                String targetName = args[0];
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
                if (target == null || !target.hasPlayedBefore()) {
                    sender.sendMessage("§c该玩家不存在或从未加入过服务器！");
                    return true;
                }
                try {
                    int year = Integer.parseInt(args[1]);
                    int month = Integer.parseInt(args[2]);
                    int day = Integer.parseInt(args[3]);
                    // 调用FriendSystem的方法查看在线时长
                    this.friendSystem.checkPlayerPlaytime(sender, target.getUniqueId(), year, month, day);
                    return true;
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c请输入有效的日期格式！");
                    return true;
                }
            }
            sender.sendMessage("§c用法: /checkplaytime <玩家名称> <年> <月> <日>");
            return true;
        }
        if (commandName.equals("plusone")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令！");
                return true;
            }
            Player player = (Player) sender;
            if (args.length >= 1) {
                StringBuilder messageBuilder = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) messageBuilder.append(" ");
                    messageBuilder.append(args[i]);
                }
                String targetMessage = messageBuilder.toString();
                if (this.activePlusOneMessages.contains(targetMessage)) {
                    player.chat(targetMessage);
                    player.sendMessage("§a已发送+1消息！");
                } else {
                    player.sendMessage("§c该消息已过期或不存在！");
                }
            } else {
                player.sendMessage("§c用法: /plusone <消息>");
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以使用此命令！");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            openReceiveMenu(player, 0);
            return true;
        }
        if (args.length == 1) {
            String targetName2 = args[0];
            if (targetName2.equals(player.getName())) {
                player.sendMessage("§c不能寄给自己！");
                return true;
            }
            OfflinePlayer target2 = Bukkit.getOfflinePlayerIfCached(targetName2);
            if (target2 == null || !target2.hasPlayedBefore()) {
                player.sendMessage("§c该玩家不存在或从未加入过服务器！");
                return true;
            }
            openSendMenu(player, target2);
            return true;
        }
        player.sendMessage("§c用法: /gift [玩家名称]");
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            Player player = (Player) sender;
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlinePlayer.hasPlayedBefore() && !offlinePlayer.getName().equals(player.getName()) && offlinePlayer.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(offlinePlayer.getName());
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }

    private void openSendMenu(Player sender, OfflinePlayer recipient) {
        Inventory inventory = Bukkit.createInventory(new GiftInventoryHolder(GiftInventoryType.SEND, recipient.getUniqueId(), sender.getUniqueId()), 27, "快递寄给" + recipient.getName());
        ItemStack confirm = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a确认");
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(22, confirm);
        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c取消");
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(26, cancel);
        sender.openInventory(inventory);
    }

    private void openReceiveMenu(Player recipient, int page) {
        List<Gift> recipientGifts = this.gifts.getOrDefault(recipient.getUniqueId(), new ArrayList());
        int totalPages = (int) Math.ceil(((double) recipientGifts.size()) / 21.0d);
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }
        Inventory inventory = Bukkit.createInventory(new GiftInventoryHolder(GiftInventoryType.RECEIVE, recipient.getUniqueId(), null, page), 54, "收件箱 - 第" + (page + 1) + "页");
        
        // 填充背景
        for (int i = 0; i < 54; i++) {
            ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = background.getItemMeta();
            meta.setDisplayName("§7");
            background.setItemMeta(meta);
            inventory.setItem(i, background);
        }
        
        // 第一行：与主菜单一致，第7个为紫色玻璃板
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
        
        ItemStack land = new ItemStack(Material.WOODEN_HOE);
        ItemMeta landMeta = land.getItemMeta();
        landMeta.setDisplayName("§a§l领地系统");
        landMeta.setLore(Collections.singletonList("§7点击打开领地菜单"));
        land.setItemMeta(landMeta);
        inventory.setItem(3, land);
        
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
        
        // 第7格：紫色玻璃板
        ItemStack purpleGlass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta purpleMeta = purpleGlass.getItemMeta();
        purpleMeta.setDisplayName("§7");
        purpleGlass.setItemMeta(purpleMeta);
        inventory.setItem(6, purpleGlass);
        
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
        
        // 第二行：紫色玻璃板
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, purpleGlass);
        }
        
        // 3-5行：未领取的快递
        int startIndex = page * 21;
        int endIndex = Math.min(startIndex + 21, recipientGifts.size());
        for (int i = startIndex; i < endIndex; i++) {
            Gift gift = recipientGifts.get(i);
            for (ItemStack item : gift.getItems()) {
                int slot = 18 + (i - startIndex); // 从第三行开始
                if (slot < 45) {
                    ItemStack itemWithLore = item.clone();
                    ItemMeta meta = itemWithLore.getItemMeta();
                    if (meta != null) {
                        List<String> lore = new ArrayList<>();
                        OfflinePlayer sender = Bukkit.getOfflinePlayer(gift.getSenderUUID());
                        String senderName = sender.getName() != null ? sender.getName() : "未知玩家";
                        Date date = new Date(gift.getTimestamp());
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        String timeStr = sdf.format(date);
                        lore.add("§7寄件人: " + senderName);
                        lore.add("§7寄件时间: " + timeStr);
                        meta.setLore(lore);
                        itemWithLore.setItemMeta(meta);
                    }
                    inventory.setItem(slot, itemWithLore);
                }
            }
        }
        
        // 第6行：保持原有样式和功能
        if (page > 0) {
            for (int i3 = 45; i3 <= 47; i3++) {
                ItemStack prev = new ItemStack(Material.ARROW);
                ItemMeta prevMeta = prev.getItemMeta();
                prevMeta.setDisplayName("§a向上翻页");
                prev.setItemMeta(prevMeta);
                inventory.setItem(i3, prev);
            }
        }
        int emptySlots = getEmptyInventorySlots(recipient);
        ItemStack claimAll = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta claimAllMeta = claimAll.getItemMeta();
        claimAllMeta.setDisplayName("§a一键领取");
        claimAllMeta.setLore(Arrays.asList("§7剩余空格: " + emptySlots, "§7点击领取对应数量的物品"));
        claimAll.setItemMeta(claimAllMeta);
        for (int i4 = 48; i4 <= 50; i4++) {
            inventory.setItem(i4, claimAll);
        }
        if (page < totalPages - 1) {
            for (int i5 = 51; i5 <= 53; i5++) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta nextMeta = next.getItemMeta();
                nextMeta.setDisplayName("§a向下翻页");
                next.setItemMeta(nextMeta);
                inventory.setItem(i5, next);
            }
        }
        recipient.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack clickedItem;
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Inventory inventory = event.getInventory();
            InventoryHolder holder = inventory.getHolder();
            if (holder instanceof CheckInMenuHolder) {
                event.setCancelled(true);
                int slot = event.getRawSlot();
                if (slot < 1 || slot >= 54 || (clickedItem = event.getCurrentItem()) == null) {
                    return;
                }
                String name = clickedItem.getItemMeta().getDisplayName();
                if (name.startsWith("§e")) {
                    performCheckIn(player);
                    return;
                } else if (name.startsWith("§7") && clickedItem.getType() == Material.WHITE_WOOL) {
                    player.sendMessage("§c还未到签到日期，无法签到！");
                    return;
                } else {
                    if (name.startsWith("§7") && clickedItem.getType() == Material.GRAY_WOOL) {
                        performMakeupCheckIn(player);
                        return;
                    }
                    return;
                }
            }
            if (holder instanceof MainMenuHolder) {
                event.setCancelled(true);
                handleMainMenuClick(event, player);
            } else if (holder instanceof GiftInventoryHolder) {
                GiftInventoryHolder giftHolder = (GiftInventoryHolder) holder;
                if (giftHolder.getType() == GiftInventoryType.SEND) {
                    handleSendMenuClick(event, player, giftHolder);
                } else if (giftHolder.getType() == GiftInventoryType.RECEIVE) {
                    handleReceiveMenuClick(event, player, giftHolder);
                }
            }
        }
    }

    private void handleSendMenuClick(InventoryClickEvent event, Player player, GiftInventoryHolder holder) {
        ItemStack item;
        ItemStack item2;
        int slot = event.getRawSlot();
        if (slot >= 27) {
            return;
        }
        if (slot == 22 || slot == 26) {
            event.setCancelled(true);
            if (slot != 22) {
                if (slot == 26) {
                    returnItemsToPlayer(event.getInventory(), player);
                    for (int i = 0; i < 27; i++) {
                        if (i != 22 && i != 26) {
                            event.getInventory().setItem(i, (ItemStack) null);
                        }
                    }
                    player.closeInventory();
                    return;
                }
                return;
            }
            List<ItemStack> items = new ArrayList<>();
            for (int i2 = 0; i2 < 27; i2++) {
                if (i2 != 22 && i2 != 26 && (item = event.getInventory().getItem(i2)) != null && item.getType() != Material.AIR) {
                    items.add(item);
                }
            }
            if (items.size() > 1) {
                player.sendMessage("§c最多只能寄1个物品！");
                return;
            }
            if (items.isEmpty()) {
                player.sendMessage("§c请放入物品后再确认！");
                return;
            }
            UUID recipientUUID = holder.getRecipientUUID();
            UUID senderUUID = holder.getSenderUUID();
            Gift gift = new Gift(senderUUID, recipientUUID, items);
            for (int i3 = 0; i3 < 27; i3++) {
                if (i3 != 22 && i3 != 26) {
                    event.getInventory().setItem(i3, (ItemStack) null);
                }
            }
            this.gifts.computeIfAbsent(recipientUUID, k -> {
                return new ArrayList();
            }).add(gift);
            saveData();
            player.sendMessage("§a快递已发送！");
            player.closeInventory();
            return;
        }
        int itemCount = 0;
        for (int i4 = 0; i4 < 27; i4++) {
            if (i4 != 22 && i4 != 26 && (item2 = event.getInventory().getItem(i4)) != null && item2.getType() != Material.AIR) {
                itemCount++;
            }
        }
        boolean isPlacingItem = false;
        if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
            isPlacingItem = true;
        } else if (event.getClickedInventory() != event.getInventory() && event.getCurrentItem() != null) {
            isPlacingItem = true;
        } else if (event.isShiftClick() && event.getClickedInventory() != event.getInventory()) {
            isPlacingItem = true;
        }
        if (isPlacingItem && itemCount >= 1) {
            event.setCancelled(true);
            player.sendMessage("§c最多只能寄1个物品！");
        }
    }

    private void handleReceiveMenuClick(InventoryClickEvent event, Player player, GiftInventoryHolder holder) {
        int slot = event.getRawSlot();
        
        // 处理第一行按钮点击
        if (slot >= 0 && slot < 9) {
            event.setCancelled(true);
            switch (slot) {
                case 0: // 签到系统
                    player.closeInventory();
                    player.performCommand("daily-check");
                    break;
                case 4: // 好友系统
                    player.closeInventory();
                    player.performCommand("friend");
                    break;
                case 8: // 管理员菜单
                    if (player.hasPermission("admin")) {
                        player.sendMessage("§a管理员菜单暂时未开放！");
                    } else {
                        player.sendMessage("§c你没有权限访问管理员菜单！");
                    }
                    break;
                default:
                    player.sendMessage("§c该系统暂时未开放！");
                    break;
            }
            return;
        }
        
        if (slot >= 45) {
            event.setCancelled(true);
            if (slot >= 45 && slot <= 47 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                openReceiveMenu(player, holder.getPage() - 1);
                return;
            }
            if (slot >= 48 && slot <= 50 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PURPLE_STAINED_GLASS_PANE) {
                int emptySlots = getEmptyInventorySlots(player);
                if (emptySlots <= 0) {
                    player.sendMessage("§c你的物品栏已满，无法领取更多物品！");
                    return;
                }
                List<Gift> recipientGifts = this.gifts.get(player.getUniqueId());
                if (recipientGifts != null && !recipientGifts.isEmpty()) {
                    int claimedCount = 0;
                    Iterator<Gift> iterator = recipientGifts.iterator();
                    while (iterator.hasNext() && claimedCount < emptySlots) {
                        Gift gift = iterator.next();
                        for (ItemStack item : gift.getItems()) {
                            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[]{item});
                            for (ItemStack left : leftover.values()) {
                                player.getWorld().dropItem(player.getLocation(), left);
                            }
                        }
                        iterator.remove();
                        claimedCount++;
                    }
                    if (recipientGifts.isEmpty()) {
                        this.gifts.remove(player.getUniqueId());
                    } else {
                        this.gifts.put(player.getUniqueId(), recipientGifts);
                    }
                    saveData();
                    player.sendMessage("§a成功领取 " + claimedCount + " 个快递！");
                    openReceiveMenu(player, holder.getPage());
                    return;
                }
                player.sendMessage("§c你没有未领取的快递！");
                return;
            }
            if (slot >= 51 && slot <= 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                openReceiveMenu(player, holder.getPage() + 1);
                return;
            }
            return;
        }
        List<Gift> recipientGifts2 = this.gifts.get(player.getUniqueId());
        if (recipientGifts2 != null) {
            int startIndex = holder.getPage() * 21;
            int giftIndex = startIndex + (slot - 18); // 从第三行开始
            if (giftIndex < recipientGifts2.size()) {
                recipientGifts2.remove(giftIndex);
                if (recipientGifts2.isEmpty()) {
                    this.gifts.remove(player.getUniqueId());
                } else {
                    this.gifts.put(player.getUniqueId(), recipientGifts2);
                }
                saveData();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GiftInventoryHolder) {
            GiftInventoryHolder holder = (GiftInventoryHolder) event.getInventory().getHolder();
            Player player = (Player) event.getPlayer();
            if (holder.getType() == GiftInventoryType.SEND) {
                returnItemsToPlayer(event.getInventory(), player);
            }
        }
    }

    private void returnItemsToPlayer(Inventory inventory, Player player) {
        ItemStack item;
        for (int i = 0; i < 27; i++) {
            if (i != 22 && i != 26 && (item = inventory.getItem(i)) != null && item.getType() != Material.AIR) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[]{item});
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), left);
                }
            }
        }
    }

    private int getEmptyInventorySlots(Player player) {
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<Gift> playerGifts = this.gifts.get(player.getUniqueId());
        if (playerGifts != null && !playerGifts.isEmpty()) {
            player.sendMessage("§a你有 " + playerGifts.size() + " 个未领取的快递！输入 /gift 查看");
        }
        if (!player.hasPlayedBefore()) {
            this.newPlayers.add(player.getUniqueId());
            Bukkit.broadcastMessage("§a欢迎新玩家 " + player.getName() + " 加入服务器！");
            player.sendMessage("§a点击下面的按钮欢迎新手！");
            player.sendMessage("§a[欢迎新手]" + String.valueOf(ChatColor.GREEN) + "<-- 点击欢迎新手");
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String message = event.getMessage();
        if (this.mutedPlayers.contains(playerUUID)) {
            event.setCancelled(true);
            player.sendMessage("§c你已被禁言，无法发送消息！");
            return;
        }
        checkMessageFrequency(player, event);
        if (message.equalsIgnoreCase("+1")) {
            handlePlusOne(player);
        } else if (message.equalsIgnoreCase("欢迎新手，新手可爱捏~")) {
            handleWelcomeNewbie(player);
        } else {
            checkMessageRepetition(player, message, event);
        }
    }

    /* JADX WARN: Type inference failed for: r0v21, types: [com.rushCT.RushCT$1] */
    private void checkMessageFrequency(final Player player, AsyncPlayerChatEvent event) {
        final UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        this.messageTimestamps.computeIfAbsent(playerUUID, k -> {
            return new ArrayList();
        });
        List<Long> timestamps = this.messageTimestamps.get(playerUUID);
        timestamps.removeIf(timestamp -> {
            return currentTime - timestamp.longValue() > 60000;
        });
        timestamps.add(Long.valueOf(currentTime));
        if (timestamps.size() > 7) {
            this.mutedPlayers.add(playerUUID);
            event.setCancelled(true);
            player.sendMessage("§c你发送消息过于频繁，已被禁言1分钟！");
            new BukkitRunnable() { // from class: com.rushCT.RushCT.1
                public void run() {
                    RushCT.this.mutedPlayers.remove(playerUUID);
                    player.sendMessage("§a禁言已解除，请注意发送消息的频率！");
                }
            }.runTaskLater(this, 1200L);
        }
    }

    /* JADX WARN: Type inference failed for: r0v27, types: [com.rushCT.RushCT$2] */
    private void checkMessageRepetition(Player player, final String message, AsyncPlayerChatEvent event) {
        if (message.isEmpty() || message.length() < 3) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        this.messageTrackers.computeIfAbsent(message, k -> {
            return new ArrayList();
        });
        List<MessageRecord> records = this.messageTrackers.get(message);
        
        // 清理超过2分钟的记录
        records.removeIf(record -> currentTime - record.timestamp > 120000);
        
        // 检查该玩家是否已经发送过这条消息，如果是则更新timestamp
        boolean playerAlreadySent = false;
        for (MessageRecord record : records) {
            if (record.playerUUID.equals(player.getUniqueId())) {
                record.timestamp = currentTime;
                playerAlreadySent = true;
                break;
            }
        }
        
        // 如果该玩家之前没有发送过，则添加新记录
        if (!playerAlreadySent) {
            records.add(new MessageRecord(player.getUniqueId(), currentTime));
        }
        
        // 如果2分钟内至少有2个不同玩家发送了至少3条消息（包括当前这条），则触发热词功能
        if (records.size() >= 3) {
            // 检查是否至少有2个不同玩家
            Set<UUID> uniquePlayers = new HashSet<>();
            for (MessageRecord record : records) {
                uniquePlayers.add(record.playerUUID);
            }
            
            if (uniquePlayers.size() >= 2) {
                event.setCancelled(true);
                
                Component hotMessageComponent = Component.text()
                    .content("")
                    .append(Component.text("[热门消息] ").color(NamedTextColor.GREEN))
                    .append(Component.text(message).color(NamedTextColor.WHITE))
                    .append(Component.text(" ").decoration(TextDecoration.BOLD, false))
                    .append(Component.text()
                        .content("[点我+1]")
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.runCommand("/plusone " + message))
                        .hoverEvent(HoverEvent.showText(Component.text("§d点击给这条消息+1").decoration(TextDecoration.BOLD, false)))
                        .build())
                    .build();
                
                Bukkit.broadcast(hotMessageComponent);
                this.activePlusOneMessages.add(message);
                new BukkitRunnable() {
                    public void run() {
                        RushCT.this.activePlusOneMessages.remove(message);
                    }
                }.runTaskLater(this, 3600L);
            }
        }
    }

    private void handlePlusOne(Player player) {
        if (this.activePlusOneMessages.isEmpty()) {
            player.sendMessage("§c当前没有可+1的消息！");
            return;
        }
        String latestMessage = this.activePlusOneMessages.iterator().next();
        player.chat(latestMessage);
        player.sendMessage("§a已发送+1消息！");
    }

    private void handleWelcomeNewbie(Player player) {
        if (this.newPlayers.isEmpty()) {
            player.sendMessage("§c当前没有新玩家！");
            return;
        }
        ItemStack gold = new ItemStack(Material.GOLD_INGOT, 1);
        player.getInventory().addItem(new ItemStack[]{gold});
        player.sendMessage("§a感谢你欢迎新手，奖励你1金币！");
        this.newPlayers.clear();
    }

    /* JADX INFO: loaded from: RushCT-1.0-SNAPSHOT.jar:com/rushCT/RushCT$GiftInventoryHolder.class */
    private static class GiftInventoryHolder implements InventoryHolder {
        private final GiftInventoryType type;
        private final UUID recipientUUID;
        private final UUID senderUUID;
        private final int page;

        public GiftInventoryHolder(GiftInventoryType type, UUID recipientUUID, UUID senderUUID) {
            this.type = type;
            this.recipientUUID = recipientUUID;
            this.senderUUID = senderUUID;
            this.page = 0;
        }

        public GiftInventoryHolder(GiftInventoryType type, UUID recipientUUID, UUID senderUUID, int page) {
            this.type = type;
            this.recipientUUID = recipientUUID;
            this.senderUUID = senderUUID;
            this.page = page;
        }

        public GiftInventoryType getType() {
            return this.type;
        }

        public UUID getRecipientUUID() {
            return this.recipientUUID;
        }

        public UUID getSenderUUID() {
            return this.senderUUID;
        }

        public int getPage() {
            return this.page;
        }

        public Inventory getInventory() {
            return null;
        }
    }

    /* JADX INFO: loaded from: RushCT-1.0-SNAPSHOT.jar:com/rushCT/RushCT$Gift.class */
    private static class Gift {
        private final UUID senderUUID;
        private final UUID recipientUUID;
        private final List<ItemStack> items;
        private final long timestamp = System.currentTimeMillis();

        public Gift(UUID senderUUID, UUID recipientUUID, List<ItemStack> items) {
            this.senderUUID = senderUUID;
            this.recipientUUID = recipientUUID;
            this.items = items;
        }

        public List<ItemStack> getItems() {
            return this.items;
        }

        public UUID getSenderUUID() {
            return this.senderUUID;
        }

        public long getTimestamp() {
            return this.timestamp;
        }

        public void saveToConfig(YamlConfiguration config, String path) {
            config.set(path + ".sender", this.senderUUID.toString());
            config.set(path + ".recipient", this.recipientUUID.toString());
            config.set(path + ".timestamp", Long.valueOf(this.timestamp));
            for (int i = 0; i < this.items.size(); i++) {
                config.set(path + ".items." + i, this.items.get(i));
            }
        }

        public static Gift loadFromConfig(YamlConfiguration config, String path) {
            try {
                UUID senderUUID = UUID.fromString(config.getString(path + ".sender"));
                UUID recipientUUID = UUID.fromString(config.getString(path + ".recipient"));
                List<ItemStack> items = new ArrayList<>();
                if (config.isConfigurationSection(path + ".items")) {
                    for (String key : config.getConfigurationSection(path + ".items").getKeys(false)) {
                        ItemStack item = config.getItemStack(path + ".items." + key);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
                return new Gift(senderUUID, recipientUUID, items);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private void openCheckInMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new CheckInMenuHolder(), 54, "每日签到");
        for (int i = 0; i < 54; i++) {
            ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = background.getItemMeta();
            meta.setDisplayName("§7");
            background.setItemMeta(meta);
            inventory.setItem(i, background);
        }
        generateCalendar(inventory, player);
        generateCoefficientPanel(inventory, player);
        player.openInventory(inventory);
    }

    private void generateCalendar(Inventory inventory, Player player) {
        Material material;
        String name;
        CheckInData data = this.checkInData.computeIfAbsent(player.getUniqueId(), k -> {
            return new CheckInData();
        });
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int today = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // 1=星期日, 2=星期一, ..., 7=星期六
        // 调整为：1=星期一, 2=星期二, ..., 7=星期日
        int adjustedFirstDay = firstDayOfWeek == 1 ? 7 : firstDayOfWeek - 1;
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        // 从第1行第2列开始（slot 1）
        int slot = 1;
        // 填充第一行的空白
        for (int i = 0; i < adjustedFirstDay - 1; i++) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName("§7");
            empty.setItemMeta(meta);
            inventory.setItem(slot, empty);
            slot++;
        }
        // 填充日期
        for (int day = 1; day <= daysInMonth; day++) {
            if (slot < 54) {
                List<String> lore = new ArrayList<>();
                if (day < today) {
                    if (data.hasCheckedIn(day)) {
                        material = Material.LIME_WOOL;
                        name = "§a" + day + "日";
                        lore.add("§7已签到");
                    } else {
                        material = Material.GRAY_WOOL;
                        name = "§7" + day + "日";
                        lore.add("§7未签到");
                        lore.add("§7点击补签（消耗65金币）");
                    }
                } else if (day == today) {
                    if (data.hasCheckedInToday()) {
                        material = Material.LIGHT_BLUE_WOOL;
                        name = "§b" + day + "日";
                        lore.add("§7今日已签到");
                    } else {
                        material = Material.YELLOW_WOOL;
                        name = "§e" + day + "日";
                        lore.add("§7点击签到");
                    }
                } else {
                    material = Material.WHITE_WOOL;
                    name = "§7" + day + "日";
                    lore.add("§7未到");
                    lore.add("§7无法签到");
                }
                ItemStack dateItem = new ItemStack(material);
                ItemMeta meta2 = dateItem.getItemMeta();
                meta2.setDisplayName(name);
                meta2.setLore(lore);
                dateItem.setItemMeta(meta2);
                inventory.setItem(slot, dateItem);
                slot++;
            } else {
                return;
            }
        }
    }

    private void generateCoefficientPanel(Inventory inventory, Player player) {
        CheckInData data = this.checkInData.computeIfAbsent(player.getUniqueId(), k -> {
            return new CheckInData();
        });
        ItemStack onlineItem = new ItemStack(Material.CLOCK);
        ItemMeta onlineMeta = onlineItem.getItemMeta();
        onlineMeta.setDisplayName("§a今日上线");
        onlineMeta.setLore(Arrays.asList(getCoefficientColor(4) + "系数: +4", "§7状态: 已上线"));
        onlineItem.setItemMeta(onlineMeta);
        inventory.setItem(45, onlineItem);
        int friendCount = getFriendCount(player);
        int friendCoefficient = 4 * friendCount;
        ItemStack friendItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta friendMeta = friendItem.getItemMeta();
        friendMeta.setDisplayName("§b好友总数");
        friendMeta.setLore(Arrays.asList(getCoefficientColor(friendCoefficient) + "系数: +" + friendCoefficient, "§7好友数: " + friendCount));
        friendItem.setItemMeta(friendMeta);
        inventory.setItem(46, friendItem);
        int activeFriends = getActiveFriendCount(player);
        int activeCoefficient = 4 * activeFriends;
        ItemStack activeItem = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta activeMeta = activeItem.getItemMeta();
        activeMeta.setDisplayName("§c好友活跃度");
        activeMeta.setLore(Arrays.asList(getCoefficientColor(activeCoefficient) + "系数: +" + activeCoefficient, "§7活跃好友: " + activeFriends));
        activeItem.setItemMeta(activeMeta);
        inventory.setItem(47, activeItem);
        int consecutiveDays = data.getConsecutiveDays();
        int consecutiveCoefficient = getConsecutiveCoefficient(consecutiveDays);
        ItemStack consecutiveItem = new ItemStack(Material.DIAMOND);
        ItemMeta consecutiveMeta = consecutiveItem.getItemMeta();
        consecutiveMeta.setDisplayName("§d连续签到");
        consecutiveMeta.setLore(Arrays.asList(getCoefficientColor(consecutiveCoefficient) + "系数: +" + consecutiveCoefficient, "§7连续天数: " + consecutiveDays));
        consecutiveItem.setItemMeta(consecutiveMeta);
        inventory.setItem(48, consecutiveItem);
        ItemStack reserveItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta reserveMeta = reserveItem.getItemMeta();
        reserveMeta.setDisplayName("§7预留扩展位");
        reserveItem.setItemMeta(reserveMeta);
        inventory.setItem(49, reserveItem);
        int totalCoefficient = 4 + friendCoefficient + activeCoefficient + consecutiveCoefficient;
        ItemStack totalItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta totalMeta = totalItem.getItemMeta();
        totalMeta.setDisplayName("§e总系数");
        totalMeta.setLore(Arrays.asList(getCoefficientColor(totalCoefficient) + "总系数: +" + totalCoefficient, "§7获得金币: " + totalCoefficient));
        totalItem.setItemMeta(totalMeta);
        inventory.setItem(53, totalItem);
    }

    private String getCoefficientColor(int coefficient) {
        if (coefficient < 4) {
            return "§7";
        }
        if (coefficient < 6) {
            return "§f";
        }
        if (coefficient < 8) {
            return "§9";
        }
        if (coefficient < 12) {
            return "§a";
        }
        if (coefficient < 16) {
            return "§e";
        }
        if (coefficient < 20) {
            return "§c";
        }
        if (coefficient < 25) {
            return "§r§d§l";
        }
        if (coefficient < 36) {
            return "§r§6§l";
        }
        if (coefficient < 64) {
            return "§r§b§l";
        }
        if (coefficient < 128) {
            return "§r§e§l";
        }
        return getRainbowTextForCoefficient(String.valueOf(coefficient));
    }

    private String getRainbowTextForCoefficient(String text) {
        char[] chars = text.toCharArray();
        String[] colors = {"§r§c§l", "§6§l", "§e§l", "§a§l", "§b§l", "§9§l", "§d§l"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            result.append(colors[i % colors.length]).append(chars[i]);
        }
        return result.toString();
    }

    private int getConsecutiveCoefficient(int days) {
        if (days >= 90) {
            return 256;
        }
        if (days >= 60) {
            return 128;
        }
        if (days >= 45) {
            return 64;
        }
        if (days >= 30) {
            return 36;
        }
        if (days >= 21) {
            return 25;
        }
        if (days >= 15) {
            return 20;
        }
        if (days >= 10) {
            return 16;
        }
        if (days >= 7) {
            return 12;
        }
        if (days >= 5) {
            return 8;
        }
        if (days >= 3) {
            return 6;
        }
        return days >= 2 ? 4 : 0;
    }

    private void performCheckIn(Player player) {
        CheckInData data = this.checkInData.computeIfAbsent(player.getUniqueId(), k -> {
            return new CheckInData();
        });
        if (data.hasCheckedInToday()) {
            player.sendMessage("§c你今天已经签到过了！");
            return;
        }
        data.checkIn();
        int reward = calculateReward(player);
        this.economySystem.depositPlayer(player, reward);
        player.sendMessage("§a签到成功！获得 " + reward + " 金币");
        saveData(); // 保存签到数据
        player.closeInventory();
        player.performCommand("menu");
    }

    private void performMakeupCheckIn(Player player) {
        CheckInData data = this.checkInData.computeIfAbsent(player.getUniqueId(), k -> {
            return new CheckInData();
        });
        if (data.hasUsedMakeupCheckIn()) {
            player.sendMessage("§c你本月已经使用过补签机会了！");
            return;
        }
        double playerGold = this.economySystem.getBalance(player);
        if (playerGold < 65.0d) {
            player.sendMessage("§c金币不足，补签需要65金币！");
            return;
        }
        this.economySystem.withdrawPlayer(player, 65.0d);
        data.useMakeupCheckIn();
        // 计算并发放签到奖励
        int reward = calculateReward(player);
        this.economySystem.depositPlayer(player, reward);
        player.sendMessage("§a补签成功！消耗了65金币，获得 " + reward + " 金币");
        saveData(); // 保存签到数据
        player.closeInventory();
        player.performCommand("menu");
    }

    private int calculateReward(Player player) {
        CheckInData data = this.checkInData.computeIfAbsent(player.getUniqueId(), k -> {
            return new CheckInData();
        });
        int friendCount = getFriendCount(player);
        int activeFriends = getActiveFriendCount(player);
        int consecutiveDays = data.getConsecutiveDays();
        return 4 + (4 * friendCount) + (4 * activeFriends) + getConsecutiveCoefficient(consecutiveDays);
    }

    private int getFriendCount(Player player) {
        return this.friendSystem.getFriendCount(player.getUniqueId());
    }

    private int getActiveFriendCount(Player player) {
        return this.friendSystem.getActiveFriendCount(player.getUniqueId());
    }

    /* JADX INFO: loaded from: RushCT-1.0-SNAPSHOT.jar:com/rushCT/RushCT$CheckInData.class */
    private static class CheckInData {
        private Set<Integer> checkedDays = new HashSet();
        private int consecutiveDays = 0;
        private int maxConsecutiveDays = 0;
        private boolean usedMakeupCheckIn = false;
        private long lastCheckInDate = 0;

        private CheckInData() {
        }

        public boolean hasCheckedIn(int day) {
            return this.checkedDays.contains(Integer.valueOf(day));
        }

        public boolean hasCheckedInToday() {
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            Calendar lastCheckIn = Calendar.getInstance();
            lastCheckIn.setTimeInMillis(this.lastCheckInDate);
            lastCheckIn.set(Calendar.HOUR_OF_DAY, 0);
            lastCheckIn.set(Calendar.MINUTE, 0);
            lastCheckIn.set(Calendar.SECOND, 0);
            lastCheckIn.set(Calendar.MILLISECOND, 0);
            return today.getTimeInMillis() == lastCheckIn.getTimeInMillis();
        }

        public void checkIn() {
            Calendar today = Calendar.getInstance();
            int day = today.get(Calendar.DAY_OF_MONTH);
            
            // 检查是否是每月第一天，如果是，重置当月签到数据（保留连签和最高连签天数）
            if (day == 1) {
                this.checkedDays.clear();
                this.usedMakeupCheckIn = false;
            }
            
            this.checkedDays.add(Integer.valueOf(day));
            if (isConsecutiveDay()) {
                this.consecutiveDays++;
            } else {
                // 检查是否断签超过2天
                if (!isConsecutiveDay() && this.lastCheckInDate > 0) {
                    Calendar todayCal = Calendar.getInstance();
                    todayCal.set(Calendar.HOUR_OF_DAY, 0);
                    todayCal.set(Calendar.MINUTE, 0);
                    todayCal.set(Calendar.SECOND, 0);
                    todayCal.set(Calendar.MILLISECOND, 0);
                    Calendar lastCheckInCal = Calendar.getInstance();
                    lastCheckInCal.setTimeInMillis(this.lastCheckInDate);
                    lastCheckInCal.set(Calendar.HOUR_OF_DAY, 0);
                    lastCheckInCal.set(Calendar.MINUTE, 0);
                    lastCheckInCal.set(Calendar.SECOND, 0);
                    lastCheckInCal.set(Calendar.MILLISECOND, 0);
                    long diffDays = (todayCal.getTimeInMillis() - lastCheckInCal.getTimeInMillis()) / 86400000;
                    if (diffDays > 2) {
                        // 断签超过2天，重置连签天数为1
                        this.consecutiveDays = 1;
                    } else {
                        // 连续签到，增加连签天数
                        this.consecutiveDays++;
                    }
                } else {
                    this.consecutiveDays = 1;
                }
            }
            // 更新最高连续签到天数
            if (this.consecutiveDays > this.maxConsecutiveDays) {
                this.maxConsecutiveDays = this.consecutiveDays;
            }
            this.lastCheckInDate = System.currentTimeMillis();
        }

        public boolean hasUsedMakeupCheckIn() {
            return this.usedMakeupCheckIn;
        }

        public void useMakeupCheckIn() {
            this.usedMakeupCheckIn = true;
            // 补签最近的未签到日期
            Calendar today = Calendar.getInstance();
            int currentDay = today.get(Calendar.DAY_OF_MONTH);
            for (int day = currentDay - 1; day >= 1; day--) {
                if (!hasCheckedIn(day)) {
                    this.checkedDays.add(Integer.valueOf(day));
                    break;
                }
            }
        }

        public int getConsecutiveDays() {
            return this.consecutiveDays;
        }

        public int getMaxConsecutiveDays() {
            return this.maxConsecutiveDays;
        }

        private boolean isConsecutiveDay() {
            if (this.lastCheckInDate == 0) {
                return false;
            }
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            Calendar yesterday = Calendar.getInstance();
            yesterday.setTimeInMillis(today.getTimeInMillis() - 86400000);
            Calendar lastCheckIn = Calendar.getInstance();
            lastCheckIn.setTimeInMillis(this.lastCheckInDate);
            lastCheckIn.set(Calendar.HOUR_OF_DAY, 0);
            lastCheckIn.set(Calendar.MINUTE, 0);
            lastCheckIn.set(Calendar.SECOND, 0);
            lastCheckIn.set(Calendar.MILLISECOND, 0);
            return lastCheckIn.getTimeInMillis() == yesterday.getTimeInMillis();
        }

        private void clearOldCheckInData() {
            Calendar today = Calendar.getInstance();
            int currentMonth = today.get(Calendar.MONTH);
            int currentYear = today.get(Calendar.YEAR);
            // 清除上个月及之前的签到数据
            Iterator<Integer> iterator = this.checkedDays.iterator();
            while (iterator.hasNext()) {
                int day = iterator.next().intValue();
                // 这里简化处理，只保留当前月的签到数据
                // 实际应该根据具体月份的天数来判断
                // 这里为了简化，假设所有月份都有31天
                // 实际应用中应该使用Calendar.getActualMaximum(Calendar.DAY_OF_MONTH)来获取当月天数
                iterator.remove();
            }
        }

        public void saveToConfig(YamlConfiguration config, String path) {
            List<Integer> checkedDaysList = new ArrayList<>(this.checkedDays);
            config.set(path + ".checkedDays", checkedDaysList);
            config.set(path + ".consecutiveDays", Integer.valueOf(this.consecutiveDays));
            config.set(path + ".maxConsecutiveDays", Integer.valueOf(this.maxConsecutiveDays));
            config.set(path + ".usedMakeupCheckIn", Boolean.valueOf(this.usedMakeupCheckIn));
            config.set(path + ".lastCheckInDate", Long.valueOf(this.lastCheckInDate));
        }

        public static CheckInData loadFromConfig(YamlConfiguration config, String path) {
            try {
                CheckInData data = new CheckInData();
                List<Integer> checkedDaysList = config.getIntegerList(path + ".checkedDays");
                for (Integer day : checkedDaysList) {
                    data.checkedDays.add(day);
                }
                data.consecutiveDays = config.getInt(path + ".consecutiveDays", 0);
                data.maxConsecutiveDays = config.getInt(path + ".maxConsecutiveDays", 0);
                data.usedMakeupCheckIn = config.getBoolean(path + ".usedMakeupCheckIn", false);
                data.lastCheckInDate = config.getLong(path + ".lastCheckInDate", 0);
                return data;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /* JADX INFO: loaded from: RushCT-1.0-SNAPSHOT.jar:com/rushCT/RushCT$CheckInMenuHolder.class */
    private static class CheckInMenuHolder implements InventoryHolder {
        private CheckInMenuHolder() {
        }

        public Inventory getInventory() {
            return null;
        }
    }

    private void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new MainMenuHolder(), 54, "服务器主菜单");
        for (int i = 0; i < 54; i++) {
            ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = background.getItemMeta();
            meta.setDisplayName("§7");
            background.setItemMeta(meta);
            inventory.setItem(i, background);
        }
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
        ItemStack land = new ItemStack(Material.WOODEN_HOE);
        ItemMeta landMeta = land.getItemMeta();
        landMeta.setDisplayName("§a§l领地系统");
        landMeta.setLore(Collections.singletonList("§7点击打开领地菜单"));
        land.setItemMeta(landMeta);
        inventory.setItem(3, land);
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
        player.openInventory(inventory);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = player.getInventory().getItemInMainHand();
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item.getType() == Material.CLOCK) {
            event.setCancelled(true);
            openMainMenu(player);
        }
    }

    private void handleMainMenuClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        switch (slot) {
            case 0:
                player.closeInventory();
                player.performCommand("daily-check");
                break;
            case 3:
                player.closeInventory();
                this.economySystem.openResidenceMenu(player);
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
                player.sendMessage("§c该系统暂时未开放！");
                break;
        }
    }

    /* JADX INFO: loaded from: RushCT-1.0-SNAPSHOT.jar:com/rushCT/RushCT$MainMenuHolder.class */
    private static class MainMenuHolder implements InventoryHolder {
        private MainMenuHolder() {
        }

        public Inventory getInventory() {
            return null;
        }
    }
    
    private void showHelpPage(CommandSender sender, int page) {
        List<String> commands = new ArrayList<>();
        commands.add("§e/rushct reload §7- 重载插件配置 (需要admin权限) [RushCT.java]");
        commands.add("§e/gift [玩家名称] §7- 打开快递系统 [RushCT.java]");
        commands.add("§e/friend [玩家名称] §7- 打开好友系统 [FriendSystem.java]");
        commands.add("§e/hello [玩家名称] §7- 打开玩家交互菜单 [FriendSystem.java]");
        commands.add("§e/passport [玩家名称] §7- 查看或编辑玩家信息 [FriendSystem.java]");
        commands.add("§e/menu §7- 打开服务器主菜单 [RushCT.java]");
        commands.add("§e/daily-check §7- 打开签到菜单 [RushCT.java]");
        commands.add("§e/resgui [领地名] §7- 打开领地管理菜单 [EconomySystem.java]");
        commands.add("§e/checkplaytime <玩家名称> <年> <月> <日> §7- 查看玩家指定日期在线时长 (需要admin权限) [FriendSystem.java]");
        commands.add("§e/plusone <消息> §7- 对热门消息进行加一操作 [RushCT.java]");
        
        int pageSize = 8;
        int totalPages = (int) Math.ceil((double) commands.size() / pageSize);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        
        sender.sendMessage("§m==============================================");
        sender.sendMessage("§a§lRushCT 插件指令列表 - 第" + page + "/" + totalPages + "页");
        sender.sendMessage("§m==============================================");
        
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, commands.size());
        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage(commands.get(i));
        }
        
        sender.sendMessage("§m==============================================");
        
        if (sender instanceof Player && totalPages > 1) {
            Player player = (Player) sender;
            this.helpPageCache.put(player.getUniqueId(), page);
            
            if (page > 1) {
                net.kyori.adventure.text.Component prevPage = net.kyori.adventure.text.Component.text("§e§l[◀ 上一页]")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/helppage " + (page - 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("§a点击查看上一页")));
                player.sendMessage(prevPage);
            }
            if (page < totalPages) {
                net.kyori.adventure.text.Component nextPage = net.kyori.adventure.text.Component.text("§e§l[下一页 ▶]")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/helppage " + (page + 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("§a点击查看下一页")));
                player.sendMessage(nextPage);
            }
        }
    }
    
    private void showHelpPage(Player player, int page) {
        showHelpPage((CommandSender) player, page);
    }
}
