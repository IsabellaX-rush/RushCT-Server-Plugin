package com.rushCT;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class FriendSystem implements Listener {
    private final JavaPlugin plugin;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private File playtimesFile;
    private YamlConfiguration playtimesConfig;
    private final Map<UUID, List<Friend>> friends = new HashMap();
    private final Map<UUID, List<FriendRequest>> sentRequests = new HashMap();
    private final Map<UUID, List<FriendRequest>> receivedRequests = new HashMap();
    private final Map<UUID, Long> lastJoinTimes = new HashMap();
    private final Map<UUID, VisibilityMode> visibilityModes = new HashMap();
    private final Map<UUID, String> qqNumbers = new HashMap();
    private final Map<UUID, String> emailAddresses = new HashMap();
    private final Map<UUID, String> birthdays = new HashMap();
    private final Map<UUID, String> genders = new HashMap();
    private final Map<UUID, Integer> dailyPlayTimeSnapshots = new HashMap();
    private Map<UUID, EditMode> editModes = new HashMap();
    private Map<UUID, Integer> genderPageCache = new HashMap();
    private Map<UUID, Long> playerJoinTimes = new HashMap<>();
    private Map<UUID, Integer> totalPlayTimeMinutes = new HashMap<>();
    private Map<UUID, Long> sessionStartTimes = new HashMap<>();

    private enum EditMode {
        QQ,
        EMAIL,
        BIRTHDAY,
        GENDER
    }

    private void openPassportMenuSync(Player player, UUID targetUUID, boolean editable) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            openPassportMenu(player, targetUUID, editable);
        });
    }

    private enum FriendInventoryType {
        MAIN,
        SENT_REQUESTS,
        RECEIVED_REQUESTS,
        INTERACTION,
        PASSPORT
    }

    private enum VisibilityMode {
        PUBLIC,
        PRIVATE_EDITABLE,
        FRIENDS_ONLY,
        PRIVATE
    }

    public FriendSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadData();
        loadPlaytimes();
        cleanupOldRequests();
        scheduleDailySnapshot();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // 先获取旧的lastJoinTime用于检查是否需要重置
        Long oldLastJoinTime = this.lastJoinTimes.get(playerUUID);
        
        // 使用PlaceholderAPI获取当前总在线时长
        String placeholderResult = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%playtimes_playtime%");
        int currentTotal = parsePlayTimeToMinutes(placeholderResult);
        this.totalPlayTimeMinutes.put(playerUUID, currentTotal);
        
        // 初始化玩家的加入时间
        if (!this.playerJoinTimes.containsKey(playerUUID)) {
            this.playerJoinTimes.put(playerUUID, Long.valueOf(player.getFirstPlayed()));
        }
        
        if (!this.visibilityModes.containsKey(playerUUID)) {
            this.visibilityModes.put(playerUUID, VisibilityMode.PUBLIC);
        }
        
        // 检查是否需要重置今日在线时长（使用旧的lastJoinTime）
        if (oldLastJoinTime == null) {
            this.dailyPlayTimeSnapshots.put(playerUUID, currentTotal);
        } else {
            Calendar currentCal = Calendar.getInstance();
            currentCal.setTimeInMillis(currentTime);
            Calendar lastJoinCal = Calendar.getInstance();
            lastJoinCal.setTimeInMillis(oldLastJoinTime.longValue());
            
            boolean needReset = currentCal.get(Calendar.YEAR) != lastJoinCal.get(Calendar.YEAR) ||
                               currentCal.get(Calendar.MONTH) != lastJoinCal.get(Calendar.MONTH) ||
                               currentCal.get(Calendar.DAY_OF_MONTH) != lastJoinCal.get(Calendar.DAY_OF_MONTH);
            
            if (needReset) {
                this.dailyPlayTimeSnapshots.put(playerUUID, currentTotal);
            }
        }
        
        // 更新lastJoinTime和sessionStartTime
        this.lastJoinTimes.put(playerUUID, Long.valueOf(currentTime));
        this.sessionStartTimes.put(playerUUID, Long.valueOf(currentTime));
    }

    private boolean needResetTodayPlayTime(UUID playerUUID, long currentTime) {
        Long lastJoinTime = this.lastJoinTimes.get(playerUUID);
        if (lastJoinTime == null) {
            return true;
        }
        Calendar currentCal = Calendar.getInstance();
        currentCal.setTimeInMillis(currentTime);
        Calendar lastJoinCal = Calendar.getInstance();
        lastJoinCal.setTimeInMillis(lastJoinTime.longValue());
        return currentCal.get(Calendar.YEAR) != lastJoinCal.get(Calendar.YEAR) ||
               currentCal.get(Calendar.MONTH) != lastJoinCal.get(Calendar.MONTH) ||
               currentCal.get(Calendar.DAY_OF_MONTH) != lastJoinCal.get(Calendar.DAY_OF_MONTH);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        this.editModes.remove(playerUUID);
        this.lastJoinTimes.put(playerUUID, Long.valueOf(System.currentTimeMillis()));
        
        // 使用PlaceholderAPI获取最终总在线时长
        String placeholderResult = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%playtimes_playtime%");
        int finalTotal = parsePlayTimeToMinutes(placeholderResult);
        this.totalPlayTimeMinutes.put(playerUUID, finalTotal);
        
        // 计算今日在线时长并保存
        Integer yesterdaySnapshot = this.dailyPlayTimeSnapshots.get(playerUUID);
        int todayMinutes = yesterdaySnapshot != null ? Math.max(0, finalTotal - yesterdaySnapshot) : 0;
        
        // 保存到playtimes.yml
        savePlaytime(playerUUID, finalTotal, todayMinutes);
        
        // 保存玩家数据
        saveData();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        EditMode mode = this.editModes.get(player.getUniqueId());
        if (mode != null) {
            event.setCancelled(true);
            String message = event.getMessage();
            switch (mode) {
                case QQ:
                    this.qqNumbers.put(player.getUniqueId(), message);
                    player.sendMessage("§aQQ号已更新为：" + message);
                    break;
                case EMAIL:
                    this.emailAddresses.put(player.getUniqueId(), message);
                    player.sendMessage("§a邮箱地址已更新为：" + message);
                    break;
                case BIRTHDAY:
                    if (message.equalsIgnoreCase("confirm")) {
                        player.sendMessage("§c请先输入生日，然后发送confirm确认！");
                    } else {
                        String[] parts = message.split(" ");
                        if (parts.length != 3) {
                            player.sendMessage("§c格式错误！请输入：年 月 日");
                            return;
                        }
                        try {
                            int year = Integer.parseInt(parts[0]);
                            int month = Integer.parseInt(parts[1]);
                            int day = Integer.parseInt(parts[2]);

                            if (month < 1 || month > 12 || day < 1 || day > 31) {
                                player.sendMessage("§c日期无效！请输入有效的日期");
                                return;
                            }

                            int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
                            if (month == 2) {
                                boolean isLeapYear = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
                                if (isLeapYear) {
                                    daysInMonth[1] = 29;
                                }
                            }
                            if (day > daysInMonth[month - 1]) {
                                player.sendMessage("§c日期无效！该月份没有这么多天");
                                return;
                            }

                            String birthday = year + " " + month + " " + day;
                            this.birthdays.put(player.getUniqueId(), birthday);
                            player.sendMessage("§a生日已设置为：" + birthday);
                            player.sendMessage("§a设定后无法修改，仅用于发放奖励，参与活动等");
                            this.editModes.remove(player.getUniqueId());
                            openPassportMenuSync(player, player.getUniqueId(), true);
                        } catch (NumberFormatException e) {
                            player.sendMessage("§c格式错误！请输入数字");
                        }
                    }
                    break;
                case GENDER:
                    if (message.equalsIgnoreCase("取消")) {
                        player.sendMessage("§a已取消性别设置");
                        this.editModes.remove(player.getUniqueId());
                        this.genderPageCache.remove(player.getUniqueId());
                        openPassportMenuSync(player, player.getUniqueId(), true);
                    } else {
                        try {
                            int genderId = Integer.parseInt(message);
                            FileConfiguration config = this.plugin.getConfig();
                            String basePath = "genders.items." + genderId;
                            
                            if (config.isConfigurationSection(basePath)) {
                                String genderName = config.getString(basePath + ".name", "");
                                this.genders.put(player.getUniqueId(), genderName);
                                player.sendMessage("§a性别已设置为：" + translateColorCodes(genderName));
                                this.editModes.remove(player.getUniqueId());
                                this.genderPageCache.remove(player.getUniqueId());
                                openPassportMenuSync(player, player.getUniqueId(), true);
                            } else {
                                player.sendMessage("§c性别编号无效！");
                                int currentPage = this.genderPageCache.getOrDefault(player.getUniqueId(), 1);
                                showGenderList(player, currentPage);
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage("§c请输入有效的性别编号！");
                            int currentPage = this.genderPageCache.getOrDefault(player.getUniqueId(), 1);
                            showGenderList(player, currentPage);
                        }
                    }
                    break;
            }
        }
    }

    private void loadData() {
        this.dataFile = new File(this.plugin.getDataFolder(), "friends.yml");
        if (!this.dataFile.exists()) {
            this.dataFile.getParentFile().mkdirs();
            try {
                this.dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
        if (this.dataConfig.isConfigurationSection("friends")) {
            for (String uuidStr : this.dataConfig.getConfigurationSection("friends").getKeys(false)) {
                UUID playerUUID = UUID.fromString(uuidStr);
                List<Friend> playerFriends = new ArrayList<>();
                for (String friendKey : this.dataConfig.getConfigurationSection("friends." + uuidStr).getKeys(false)) {
                    Friend friend = Friend.loadFromConfig(this.dataConfig, "friends." + uuidStr + "." + friendKey);
                    if (friend != null) {
                        playerFriends.add(friend);
                    }
                }
                this.friends.put(playerUUID, playerFriends);
            }
        }
        if (this.dataConfig.isConfigurationSection("sentRequests")) {
            for (String uuidStr2 : this.dataConfig.getConfigurationSection("sentRequests").getKeys(false)) {
                UUID playerUUID2 = UUID.fromString(uuidStr2);
                List<FriendRequest> requests = new ArrayList<>();
                for (String requestKey : this.dataConfig.getConfigurationSection("sentRequests." + uuidStr2).getKeys(false)) {
                    FriendRequest request = FriendRequest.loadFromConfig(this.dataConfig, "sentRequests." + uuidStr2 + "." + requestKey);
                    if (request != null) {
                        requests.add(request);
                    }
                }
                this.sentRequests.put(playerUUID2, requests);
            }
        }
        if (this.dataConfig.isConfigurationSection("receivedRequests")) {
            for (String uuidStr3 : this.dataConfig.getConfigurationSection("receivedRequests").getKeys(false)) {
                UUID playerUUID3 = UUID.fromString(uuidStr3);
                List<FriendRequest> requests2 = new ArrayList<>();
                for (String requestKey2 : this.dataConfig.getConfigurationSection("receivedRequests." + uuidStr3).getKeys(false)) {
                    FriendRequest request2 = FriendRequest.loadFromConfig(this.dataConfig, "receivedRequests." + uuidStr3 + "." + requestKey2);
                    if (request2 != null) {
                        requests2.add(request2);
                    }
                }
                this.receivedRequests.put(playerUUID3, requests2);
            }
        }
        if (this.dataConfig.isConfigurationSection("dailyPlayTimeSnapshots")) {
            for (String uuidStr4 : this.dataConfig.getConfigurationSection("dailyPlayTimeSnapshots").getKeys(false)) {
                UUID playerUUID4 = UUID.fromString(uuidStr4);
                int snapshot = this.dataConfig.getInt("dailyPlayTimeSnapshots." + uuidStr4, 0);
                this.dailyPlayTimeSnapshots.put(playerUUID4, Integer.valueOf(snapshot));
            }
        }
        if (this.dataConfig.isConfigurationSection("lastJoinTimes")) {
            for (String uuidStr6 : this.dataConfig.getConfigurationSection("lastJoinTimes").getKeys(false)) {
                UUID playerUUID6 = UUID.fromString(uuidStr6);
                long lastJoinTime = this.dataConfig.getLong("lastJoinTimes." + uuidStr6, System.currentTimeMillis());
                this.lastJoinTimes.put(playerUUID6, Long.valueOf(lastJoinTime));
            }
        }
        if (this.dataConfig.isConfigurationSection("birthdays")) {
            for (String uuidStr7 : this.dataConfig.getConfigurationSection("birthdays").getKeys(false)) {
                UUID playerUUID7 = UUID.fromString(uuidStr7);
                String birthday = this.dataConfig.getString("birthdays." + uuidStr7, "");
                this.birthdays.put(playerUUID7, birthday);
            }
        }
        if (this.dataConfig.isConfigurationSection("genders")) {
            for (String uuidStr8 : this.dataConfig.getConfigurationSection("genders").getKeys(false)) {
                UUID playerUUID8 = UUID.fromString(uuidStr8);
                String gender = this.dataConfig.getString("genders." + uuidStr8, "");
                this.genders.put(playerUUID8, gender);
            }
        }
        if (this.dataConfig.isConfigurationSection("qqNumbers")) {
            for (String uuidStr9 : this.dataConfig.getConfigurationSection("qqNumbers").getKeys(false)) {
                UUID playerUUID9 = UUID.fromString(uuidStr9);
                String qqNumber = this.dataConfig.getString("qqNumbers." + uuidStr9, "");
                this.qqNumbers.put(playerUUID9, qqNumber);
            }
        }
        if (this.dataConfig.isConfigurationSection("emailAddresses")) {
            for (String uuidStr10 : this.dataConfig.getConfigurationSection("emailAddresses").getKeys(false)) {
                UUID playerUUID10 = UUID.fromString(uuidStr10);
                String emailAddress = this.dataConfig.getString("emailAddresses." + uuidStr10, "");
                this.emailAddresses.put(playerUUID10, emailAddress);
            }
        }
        if (this.dataConfig.isConfigurationSection("playerJoinTimes")) {
            for (String uuidStr11 : this.dataConfig.getConfigurationSection("playerJoinTimes").getKeys(false)) {
                UUID playerUUID11 = UUID.fromString(uuidStr11);
                long joinTime = this.dataConfig.getLong("playerJoinTimes." + uuidStr11, System.currentTimeMillis());
                this.playerJoinTimes.put(playerUUID11, joinTime);
            }
        }
        if (this.dataConfig.isConfigurationSection("totalPlayTimeMinutes")) {
            for (String uuidStr12 : this.dataConfig.getConfigurationSection("totalPlayTimeMinutes").getKeys(false)) {
                UUID playerUUID12 = UUID.fromString(uuidStr12);
                int totalMinutes = this.dataConfig.getInt("totalPlayTimeMinutes." + uuidStr12, 0);
                this.totalPlayTimeMinutes.put(playerUUID12, totalMinutes);
            }
        }
    }

    private void saveData() {
        this.dataConfig = new YamlConfiguration();
        for (Map.Entry<UUID, List<Friend>> entry : this.friends.entrySet()) {
            String uuidStr = entry.getKey().toString();
            List<Friend> playerFriends = entry.getValue();
            for (int i = 0; i < playerFriends.size(); i++) {
                playerFriends.get(i).saveToConfig(this.dataConfig, "friends." + uuidStr + ".friend" + i);
            }
        }
        for (Map.Entry<UUID, List<FriendRequest>> entry2 : this.sentRequests.entrySet()) {
            String uuidStr2 = entry2.getKey().toString();
            List<FriendRequest> requests = entry2.getValue();
            for (int i2 = 0; i2 < requests.size(); i2++) {
                requests.get(i2).saveToConfig(this.dataConfig, "sentRequests." + uuidStr2 + ".request" + i2);
            }
        }
        for (Map.Entry<UUID, List<FriendRequest>> entry3 : this.receivedRequests.entrySet()) {
            String uuidStr3 = entry3.getKey().toString();
            List<FriendRequest> requests2 = entry3.getValue();
            for (int i3 = 0; i3 < requests2.size(); i3++) {
                requests2.get(i3).saveToConfig(this.dataConfig, "receivedRequests." + uuidStr3 + ".request" + i3);
            }
        }
        for (Map.Entry<UUID, Integer> entry4 : this.dailyPlayTimeSnapshots.entrySet()) {
            String uuidStr4 = entry4.getKey().toString();
            int snapshot = entry4.getValue().intValue();
            this.dataConfig.set("dailyPlayTimeSnapshots." + uuidStr4, Integer.valueOf(snapshot));
        }
        for (Map.Entry<UUID, Long> entry6 : this.lastJoinTimes.entrySet()) {
            String uuidStr6 = entry6.getKey().toString();
            long lastJoinTime = entry6.getValue().longValue();
            this.dataConfig.set("lastJoinTimes." + uuidStr6, Long.valueOf(lastJoinTime));
        }
        for (Map.Entry<UUID, String> entry7 : this.birthdays.entrySet()) {
            String uuidStr7 = entry7.getKey().toString();
            String birthday = entry7.getValue();
            this.dataConfig.set("birthdays." + uuidStr7, birthday);
        }
        for (Map.Entry<UUID, String> entry8 : this.genders.entrySet()) {
            String uuidStr8 = entry8.getKey().toString();
            String gender = entry8.getValue();
            this.dataConfig.set("genders." + uuidStr8, gender);
        }
        for (Map.Entry<UUID, String> entry9 : this.qqNumbers.entrySet()) {
            String uuidStr9 = entry9.getKey().toString();
            String qqNumber = entry9.getValue();
            this.dataConfig.set("qqNumbers." + uuidStr9, qqNumber);
        }
        for (Map.Entry<UUID, String> entry10 : this.emailAddresses.entrySet()) {
            String uuidStr10 = entry10.getKey().toString();
            String emailAddress = entry10.getValue();
            this.dataConfig.set("emailAddresses." + uuidStr10, emailAddress);
        }
        for (Map.Entry<UUID, Long> entry11 : this.playerJoinTimes.entrySet()) {
            String uuidStr11 = entry11.getKey().toString();
            long joinTime = entry11.getValue().longValue();
            this.dataConfig.set("playerJoinTimes." + uuidStr11, Long.valueOf(joinTime));
        }
        for (Map.Entry<UUID, Integer> entry12 : this.totalPlayTimeMinutes.entrySet()) {
            String uuidStr12 = entry12.getKey().toString();
            int totalMinutes = entry12.getValue().intValue();
            this.dataConfig.set("totalPlayTimeMinutes." + uuidStr12, Integer.valueOf(totalMinutes));
        }
        try {
            this.dataConfig.save(this.dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPlaytimes() {
        this.playtimesFile = new File(this.plugin.getDataFolder(), "playtimes.yml");
        if (!this.playtimesFile.exists()) {
            this.playtimesFile.getParentFile().mkdirs();
            try {
                this.playtimesFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.playtimesConfig = YamlConfiguration.loadConfiguration(this.playtimesFile);
    }

    private void savePlaytime(UUID playerUUID, int totalMinutes, int todayMinutes) {
        String uuidStr = playerUUID.toString();
        this.playtimesConfig.set(uuidStr + ".total", Integer.valueOf(totalMinutes));
        this.playtimesConfig.set(uuidStr + ".today", Integer.valueOf(todayMinutes));
        this.playtimesConfig.set(uuidStr + ".lastUpdate", Long.valueOf(System.currentTimeMillis()));
        try {
            this.playtimesConfig.save(this.playtimesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getStoredTotalPlaytime(UUID playerUUID) {
        return this.playtimesConfig.getInt(playerUUID.toString() + ".total", 0);
    }

    private int getStoredTodayPlaytime(UUID playerUUID) {
        return this.playtimesConfig.getInt(playerUUID.toString() + ".today", 0);
    }

    private void cleanupOldRequests() {
        long thirtyDaysAgo = System.currentTimeMillis() - 2592000000L;
        for (Map.Entry<UUID, List<FriendRequest>> entry : this.sentRequests.entrySet()) {
            List<FriendRequest> requests = entry.getValue();
            Iterator<FriendRequest> iterator = requests.iterator();
            while (iterator.hasNext()) {
                FriendRequest request = iterator.next();
                if (request.getTimestamp() < thirtyDaysAgo) {
                    iterator.remove();
                }
            }
        }
        for (Map.Entry<UUID, List<FriendRequest>> entry2 : this.receivedRequests.entrySet()) {
            List<FriendRequest> requests2 = entry2.getValue();
            Iterator<FriendRequest> iterator2 = requests2.iterator();
            while (iterator2.hasNext()) {
                FriendRequest request2 = iterator2.next();
                if (request2.getTimestamp() < thirtyDaysAgo) {
                    iterator2.remove();
                }
            }
        }
        saveData();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player onlineTarget;
        String commandName = command.getName().toLowerCase();
        if (commandName.equals("hello")) {
            return handleHelloCommand(sender, args);
        }
        if (commandName.equals("passport")) {
            return handlePassportCommand(sender, args);
        }
        if (commandName.equals("genderpage")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令！");
                return true;
            }
            Player player = (Player) sender;
            if (args.length == 1) {
                try {
                    int page = Integer.parseInt(args[0]);
                    showGenderList(player, page);
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("§c请输入有效的页码！");
                    return true;
                }
            }
            player.sendMessage("§c用法: /genderpage <页码>");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以使用此命令！");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            openFriendMainMenu(player);
            return true;
        }
        if (args.length == 1) {
            String targetName2 = args[0];
            if (targetName2.equals(player.getName())) {
                player.sendMessage("§c不能向自己发送好友申请！");
                return true;
            }
            OfflinePlayer target2 = Bukkit.getOfflinePlayerIfCached(targetName2);
            if (target2 == null || !target2.hasPlayedBefore()) {
                player.sendMessage("§c该玩家不存在或从未加入过服务器！");
                return true;
            }
            List<Friend> playerFriends = this.friends.getOrDefault(player.getUniqueId(), new ArrayList());
            for (Friend friend : playerFriends) {
                if (friend.getFriendUUID().equals(target2.getUniqueId())) {
                    player.sendMessage("§c你们已经是好友了！");
                    return true;
                }
            }
            List<FriendRequest> playerRequests = this.sentRequests.getOrDefault(player.getUniqueId(), new ArrayList());
            Iterator<FriendRequest> it = playerRequests.iterator();
            while (it.hasNext()) {
                if (it.next().getTargetUUID().equals(target2.getUniqueId())) {
                    player.sendMessage("§c你已经向该玩家发送过好友申请了！");
                    return true;
                }
            }
            FriendRequest request = new FriendRequest(player.getUniqueId(), target2.getUniqueId());
            this.sentRequests.computeIfAbsent(player.getUniqueId(), k -> {
                return new ArrayList();
            }).add(request);
            this.receivedRequests.computeIfAbsent(target2.getUniqueId(), k2 -> {
                return new ArrayList();
            }).add(request);
            saveData();
            player.sendMessage("§a好友申请已发送！");
            if (target2.isOnline() && (onlineTarget = target2.getPlayer()) != null) {
                onlineTarget.sendMessage("§a你收到了来自 " + player.getName() + " 的好友申请！输入 /friend 查看");
                return true;
            }
            return true;
        }
        player.sendMessage("§c用法: /friend [玩家名称]");
        return true;
    }

    private boolean handleHelloCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以使用此命令！");
            return true;
        }
        Player player = (Player) sender;
        if (args.length != 1) {
            player.sendMessage("§c用法: /hello <玩家名称>");
            return true;
        }
        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null || !target.hasPlayedBefore()) {
            player.sendMessage("§c该玩家不存在或从未加入过服务器！");
            return true;
        }
        if (!target.isOnline()) {
            player.sendMessage("§c玩家不存在或已离线！");
            return true;
        }
        Player onlineTarget = target.getPlayer();
        if (onlineTarget == null) {
            player.sendMessage("§c玩家不存在或已离线！");
            return true;
        }
        openInteractionMenu(player, onlineTarget);
        return true;
    }

    private boolean handlePassportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以使用此命令！");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            openPassportMenu(player, player.getUniqueId(), true);
            return true;
        }
        if (args.length == 1) {
            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
            if (target == null || !target.hasPlayedBefore()) {
                player.sendMessage("§c该玩家不存在或从未加入过服务器！");
                return true;
            }
            openPassportMenu(player, target.getUniqueId(), false);
            return true;
        }
        player.sendMessage("§c用法: /passport [玩家名称]");
        return true;
    }

    private void openInteractionMenu(Player player, Player target) {
        Inventory inventory = Bukkit.createInventory(new FriendInventoryHolder(FriendInventoryType.INTERACTION, player.getUniqueId(), target.getUniqueId()), 54, "与" + target.getName() + "的互动");
        for (int i = 0; i < 54; i++) {
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = border.getItemMeta();
            meta.setDisplayName("§7");
            border.setItemMeta(meta);
            inventory.setItem(i, border);
        }
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta playerInfoMeta = playerInfo.getItemMeta();
        playerInfoMeta.setDisplayName("§a玩家信息");
        playerInfoMeta.setLore(Collections.singletonList("§7查看目标玩家信息"));
        playerInfo.setItemMeta(playerInfoMeta);
        inventory.setItem(19, playerInfo);
        ItemStack friendRequest = new ItemStack(Material.ROSE_BUSH);
        ItemMeta friendRequestMeta = friendRequest.getItemMeta();
        friendRequestMeta.setDisplayName("§a请求交友");
        friendRequestMeta.setLore(Collections.singletonList("§7向目标玩家发送好友申请"));
        friendRequest.setItemMeta(friendRequestMeta);
        inventory.setItem(21, friendRequest);
        ItemStack sendGift = new ItemStack(Material.CHEST);
        ItemMeta sendGiftMeta = sendGift.getItemMeta();
        sendGiftMeta.setDisplayName("§a寄件");
        sendGiftMeta.setLore(Collections.singletonList("§7向目标玩家发送快递"));
        sendGift.setItemMeta(sendGiftMeta);
        inventory.setItem(23, sendGift);
        ItemStack trade = new ItemStack(Material.EMERALD);
        ItemMeta tradeMeta = trade.getItemMeta();
        tradeMeta.setDisplayName("§a请求交易");
        tradeMeta.setLore(Arrays.asList("§7向目标玩家发送交易请求", "§7默认格式: /trade <玩家名>"));
        trade.setItemMeta(tradeMeta);
        inventory.setItem(25, trade);
        player.openInventory(inventory);
    }

    private void openPassportMenu(Player viewer, UUID targetUUID, boolean editable) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        if (target == null || !target.hasPlayedBefore()) {
            viewer.sendMessage("§c该玩家不存在或从未加入过服务器！");
            return;
        }
        VisibilityMode visibilityMode = this.visibilityModes.getOrDefault(targetUUID, VisibilityMode.PUBLIC);
        if (visibilityMode == VisibilityMode.PRIVATE && !viewer.getUniqueId().equals(targetUUID)) {
            viewer.sendMessage("§c该玩家信息仅自己可见！");
            return;
        }
        if (visibilityMode == VisibilityMode.FRIENDS_ONLY && !viewer.getUniqueId().equals(targetUUID)) {
            List<Friend> viewerFriends = this.friends.getOrDefault(viewer.getUniqueId(), new ArrayList());
            boolean isFriend = false;
            Iterator<Friend> it = viewerFriends.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                Friend friend = it.next();
                if (friend.getFriendUUID().equals(targetUUID)) {
                    isFriend = true;
                    break;
                }
            }
            if (!isFriend) {
                viewer.sendMessage("§c该玩家信息仅对好友开放！");
                return;
            }
        }
        String targetName = target.getName() != null ? target.getName() : "未知玩家";
        Inventory inventory = Bukkit.createInventory(new FriendInventoryHolder(FriendInventoryType.PASSPORT, viewer.getUniqueId(), targetUUID, editable), 54, targetName + "的信息");
        for (int i = 0; i < 9; i++) {
            ItemStack redPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = redPane.getItemMeta();
            meta.setDisplayName("§7");
            redPane.setItemMeta(meta);
            inventory.setItem(i, redPane);
            inventory.setItem(45 + i, redPane);
        }
        for (int i2 = 9; i2 < 45; i2++) {
            if (i2 % 9 == 0 || i2 % 9 == 8) {
                ItemStack redPane2 = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta2 = redPane2.getItemMeta();
                meta2.setDisplayName("§7");
                redPane2.setItemMeta(meta2);
                inventory.setItem(i2, redPane2);
            } else {
                ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta3 = grayPane.getItemMeta();
                meta3.setDisplayName("§7");
                grayPane.setItemMeta(meta3);
                inventory.setItem(i2, grayPane);
            }
        }
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta playerHeadMeta = playerHead.getItemMeta();
        playerHeadMeta.setDisplayName("§a玩家昵称");
        playerHeadMeta.setLore(Collections.singletonList("§7" + targetName));
        playerHead.setItemMeta(playerHeadMeta);
        inventory.setItem(10, playerHead);
        ItemStack joinDate = new ItemStack(Material.OAK_SAPLING);
        ItemMeta joinDateMeta = joinDate.getItemMeta();
        joinDateMeta.setDisplayName("§a加入服务器日期");
        String joinDateStr = getPlayerJoinDate(targetUUID);
        String formattedJoinDate = formatJoinDate(joinDateStr);
        long joinTime = getJoinDateTimestamp(formattedJoinDate);
        joinDateMeta.setLore(Collections.singletonList(getJoinDateFormat(joinTime, formattedJoinDate)));
        joinDate.setItemMeta(joinDateMeta);
        inventory.setItem(11, joinDate);

        ItemStack lastJoin = new ItemStack(Material.COMPASS);
        ItemMeta lastJoinMeta = lastJoin.getItemMeta();
        lastJoinMeta.setDisplayName("§a上次上线时间");
        long lastJoinTime = this.lastJoinTimes.getOrDefault(targetUUID, Long.valueOf(target.getLastPlayed())).longValue();
        Date lastJoinDate = new Date(lastJoinTime);
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String lastJoinStr = dateTimeFormat.format(lastJoinDate);
        lastJoinMeta.setLore(Collections.singletonList("§7" + lastJoinStr));
        lastJoin.setItemMeta(lastJoinMeta);
        inventory.setItem(12, lastJoin);

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        int totalMinutes, todayMinutes;
        
        if (targetPlayer != null && targetPlayer.isOnline()) {
            // 玩家在线，使用实时数据
            String totalPlayTimeStr = getPlayerTotalPlayTime(targetUUID);
            totalMinutes = parsePlayTimeToMinutes(totalPlayTimeStr);
            int yesterdaySnapshot = this.dailyPlayTimeSnapshots.getOrDefault(targetUUID, 0).intValue();
            todayMinutes = totalMinutes - yesterdaySnapshot;
            if (todayMinutes < 0) todayMinutes = 0;
        } else {
            // 玩家离线，使用存储的数据
            totalMinutes = getStoredTotalPlaytime(targetUUID);
            todayMinutes = getStoredTodayPlaytime(targetUUID);
        }

        ItemStack totalPlayTime = new ItemStack(Material.COMPASS);
        ItemMeta totalPlayTimeMeta = totalPlayTime.getItemMeta();
        totalPlayTimeMeta.setDisplayName("§a在线总时长");
        long totalPlayTimeMs = totalMinutes * 60000L;
        totalPlayTimeMeta.setLore(Collections.singletonList(getPlayTimeFormat(totalPlayTimeMs, false)));
        totalPlayTime.setItemMeta(totalPlayTimeMeta);
        inventory.setItem(19, totalPlayTime);

        ItemStack todayPlayTime = new ItemStack(Material.CLOCK);
        ItemMeta todayPlayTimeMeta = todayPlayTime.getItemMeta();
        todayPlayTimeMeta.setDisplayName("§a今日在线时长");
        long todayPlayTimeMs = todayMinutes * 60000L;
        todayPlayTimeMeta.setLore(Collections.singletonList(getPlayTimeFormat(todayPlayTimeMs, true)));
        todayPlayTime.setItemMeta(todayPlayTimeMeta);
        inventory.setItem(20, todayPlayTime);
        ItemStack maxConsecutiveDays = new ItemStack(Material.DIAMOND);
        ItemMeta maxConsecutiveDaysMeta = maxConsecutiveDays.getItemMeta();
        maxConsecutiveDaysMeta.setDisplayName("§a最高连签天数");
        RushCT plugin = (RushCT) this.plugin;
        int maxDays = plugin.getMaxConsecutiveDays(targetUUID);
        maxConsecutiveDaysMeta.setLore(Collections.singletonList(getMaxConsecutiveDaysFormat(maxDays)));
        maxConsecutiveDays.setItemMeta(maxConsecutiveDaysMeta);
        inventory.setItem(21, maxConsecutiveDays);
        ItemStack birthday = new ItemStack(Material.CAKE);
        ItemMeta birthdayMeta = birthday.getItemMeta();
        birthdayMeta.setDisplayName("§a生日");
        String birthdayStr = this.birthdays.getOrDefault(targetUUID, "");
        if (editable) {
            List<String> lore = new ArrayList<>();
            lore.add("§7当前: " + (birthdayStr.isEmpty() ? "未设置" : birthdayStr));
            if (birthdayStr.isEmpty()) {
                lore.add("§7左键：设置生日");
                lore.add("§7格式: 年 月 日");
                lore.add("§7例如: 2000 1 1");
                lore.add("§7仅能设置一次，设定后无法修改");
            }
            lore.add("§7仅用于发放奖励，参与活动等");
            lore.add("§7不作为防沉迷判定依据");
            birthdayMeta.setLore(lore);
        } else if (visibilityMode == VisibilityMode.PRIVATE_EDITABLE) {
            birthdayMeta.setLore(Collections.singletonList("§7***"));
        } else {
            birthdayMeta.setLore(Collections.singletonList(getBirthdayFormat(birthdayStr)));
        }
        birthday.setItemMeta(birthdayMeta);
        inventory.setItem(14, birthday);

        ItemStack qq = new ItemStack(Material.PANDA_SPAWN_EGG);
        ItemMeta qqMeta = qq.getItemMeta();
        qqMeta.setDisplayName("§aQQ号");
        String qqNumber = this.qqNumbers.getOrDefault(targetUUID, "");
        if (editable) {
            String[] strArr = new String[3];
            strArr[0] = "§7当前: " + (qqNumber.isEmpty() ? "未设置" : qqNumber);
            strArr[1] = "§7左键：输入新内容";
            strArr[2] = "§7右键：清空内容";
            qqMeta.setLore(Arrays.asList(strArr));
        } else if (visibilityMode == VisibilityMode.PRIVATE_EDITABLE) {
            qqMeta.setLore(Collections.singletonList("§7***"));
        } else {
            qqMeta.setLore(Collections.singletonList("§7" + (qqNumber.isEmpty() ? "未设置" : qqNumber)));
        }
        qq.setItemMeta(qqMeta);
        inventory.setItem(15, qq);
        ItemStack email = new ItemStack(Material.BOOK);
        ItemMeta emailMeta = email.getItemMeta();
        emailMeta.setDisplayName("§a邮箱地址");
        String emailAddress = this.emailAddresses.getOrDefault(targetUUID, "");
        if (editable) {
            String[] strArr2 = new String[3];
            strArr2[0] = "§7当前: " + (emailAddress.isEmpty() ? "未设置" : emailAddress);
            strArr2[1] = "§7左键：输入新内容";
            strArr2[2] = "§7右键：清空内容";
            emailMeta.setLore(Arrays.asList(strArr2));
        } else if (visibilityMode == VisibilityMode.PRIVATE_EDITABLE) {
            emailMeta.setLore(Collections.singletonList("§7***"));
        } else {
            emailMeta.setLore(Collections.singletonList("§7" + (emailAddress.isEmpty() ? "未设置" : emailAddress)));
        }
        email.setItemMeta(emailMeta);
        inventory.setItem(16, email);
        ItemStack gender = new ItemStack(Material.PINK_DYE);
        ItemMeta genderMeta = gender.getItemMeta();
        genderMeta.setDisplayName("§a性别");
        String genderText = this.genders.getOrDefault(targetUUID, "");
        FileConfiguration config = this.plugin.getConfig();
        String genderName;
        if (genderText.isEmpty()) {
            // 如果没有设置，使用第一个默认性别
            if (config.isConfigurationSection("genders.items")) {
                List<Integer> genderIds = new ArrayList<>();
                for (String key : config.getConfigurationSection("genders.items").getKeys(false)) {
                    try {
                        genderIds.add(Integer.parseInt(key));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
                Collections.sort(genderIds);
                if (!genderIds.isEmpty()) {
                    String defaultGenderName = config.getString("genders.items." + genderIds.get(0) + ".name", "默认");
                    genderName = translateColorCodes(defaultGenderName);
                } else {
                    genderName = "默认";
                }
            } else {
                genderName = "默认";
            }
        } else {
            genderName = translateColorCodes(genderText);
        }
        if (editable) {
            genderMeta.setLore(Arrays.asList("§7当前: " + genderName, "§7左键：设置性别"));
        } else {
            genderMeta.setLore(Collections.singletonList("§7" + genderName));
        }
        gender.setItemMeta(genderMeta);
        inventory.setItem(24, gender);

        if (editable) {
            ItemStack visibility = new ItemStack(Material.TRIPWIRE_HOOK);
            ItemMeta visibilityMeta = visibility.getItemMeta();
            visibilityMeta.setDisplayName("§a信息可见性");
            String visibilityStr = getVisibilityModeName(visibilityMode);
            visibilityMeta.setLore(Arrays.asList("§7当前: " + visibilityStr, "§7左键点击切换模式"));
            visibility.setItemMeta(visibilityMeta);
            inventory.setItem(49, visibility);
        }
        viewer.openInventory(inventory);
    }

    private String formatPlayTime(long milliseconds) {
        long totalMinutes = milliseconds / 60000;
        long hours = totalMinutes / 60;
        long minutes = ((totalMinutes % 60) / 5) * 5;
        if (minutes > 0) {
            String str = minutes + "分钟";
        }
        return hours + "小时" + hours;
    }

    private String getVisibilityModeName(VisibilityMode mode) {
        switch (mode) {
            case PUBLIC:
                return "始终对外可见";
            case PRIVATE_EDITABLE:
                return "可修改信息仅自己可见";
            case FRIENDS_ONLY:
                return "仅对好友可见";
            case PRIVATE:
                return "仅自己可见";
            default:
                return "未知";
        }
    }

    private String getPlayTimeFormat(long milliseconds, boolean isToday) {
        long totalMinutes = milliseconds / 60000;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        String timeStr;
        if (hours > 0 && minutes > 0) {
            timeStr = hours + "小时" + minutes + "分钟";
        } else if (hours > 0) {
            timeStr = hours + "小时";
        } else {
            timeStr = minutes + "分钟";
        }
        if (isToday) {
            if (totalMinutes < 5) {
                return "§7" + timeStr;
            }
            if (totalMinutes < 15) {
                return "§7" + timeStr;
            }
            if (totalMinutes < 20) {
                return "§f" + timeStr;
            }
            if (totalMinutes < 30) {
                return "§9" + timeStr;
            }
            if (totalMinutes < 45) {
                return "§a" + timeStr;
            }
            if (totalMinutes < 60) {
                return "§e" + timeStr;
            }
            if (totalMinutes < 90) {
                return "§c" + timeStr;
            }
            if (totalMinutes < 120) {
                return "§r§d§l" + timeStr;
            }
            if (totalMinutes < 150) {
                return "§r§6§l" + timeStr;
            }
            if (totalMinutes < 180) {
                return "§r§b§l" + timeStr;
            }
            if (totalMinutes < 210) {
                return "§r§e§l" + timeStr;
            }
            return getRainbowText(timeStr, true);
        }
        if (totalMinutes < 5) {
            return "§7" + timeStr;
        }
        if (totalMinutes < 60) {
            return "§7" + timeStr;
        }
        if (totalMinutes < 120) {
            return "§f" + timeStr;
        }
        if (totalMinutes < 240) {
            return "§9" + timeStr;
        }
        if (totalMinutes < 480) {
            return "§a" + timeStr;
        }
        if (totalMinutes < 960) {
            return "§e" + timeStr;
        }
        if (totalMinutes < 1440) {
            return "§c" + timeStr;
        }
        if (totalMinutes < 1920) {
            return "§r§d§l" + timeStr;
        }
        if (totalMinutes < 2520) {
            return "§r§6§l" + timeStr;
        }
        if (totalMinutes < 3360) {
            return "§r§b§l" + timeStr;
        }
        if (totalMinutes < 4800) {
            return "§r§e§l" + timeStr;
        }
        return getRainbowText(timeStr, true);
    }

    private String getMaxConsecutiveDaysFormat(int days) {
        if (days < 3) {
            return "§7" + days + "天";
        }
        if (days < 5) {
            return "§f" + days + "天";
        }
        if (days < 7) {
            return "§9" + days + "天";
        }
        if (days < 10) {
            return "§a" + days + "天";
        }
        if (days < 15) {
            return "§e" + days + "天";
        }
        if (days < 21) {
            return "§c" + days + "天";
        }
        if (days < 30) {
            return "§r§d§l" + days + "天";
        }
        if (days < 45) {
            return "§r§6§l" + days + "天";
        }
        if (days < 60) {
            return "§r§b§l" + days + "天";
        }
        if (days < 90) {
            return "§r§e§l" + days + "天";
        }
        return "§r§c§l" + days + "天";
    }

    private String getBirthdayFormat(String birthdayStr) {
        if (birthdayStr.isEmpty()) {
            return "§7未设置";
        }

        String[] parts = birthdayStr.split(" ");
        if (parts.length != 3) {
            return "§7格式错误";
        }

        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            Calendar now = Calendar.getInstance();
            int currentYear = now.get(Calendar.YEAR);
            int currentMonth = now.get(Calendar.MONTH) + 1;
            int currentDay = now.get(Calendar.DAY_OF_MONTH);

            Calendar birthday = Calendar.getInstance();
            birthday.set(currentYear, month - 1, day);

            if (birthday.before(now)) {
                birthday.set(currentYear + 1, month - 1, day);
            }

            long diffInMillis = birthday.getTimeInMillis() - now.getTimeInMillis();
            long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);

            if (diffInDays <= 7 && diffInDays >= -7) {
                return getBoldRainbowText(birthdayStr);
            } else if (currentMonth == month) {
                return "§r§e§l" + birthdayStr;
            } else {
                return "§f" + birthdayStr;
            }
        } catch (NumberFormatException e) {
            return "§7格式错误";
        }
    }

    private String getRainbowText(String text, boolean bold) {
        char[] chars = text.toCharArray();
        String[] colors = {"§r§c", "§6", "§e", "§a", "§b", "§9", "§d"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            result.append(colors[i % colors.length]);
            if (bold) {
                result.append("§l");
            }
            result.append(chars[i]);
        }
        return result.toString();
    }

    private String getRainbowText(String text) {
        return getRainbowText(text, false);
    }

    private String getBoldRainbowText(String text) {
        return getRainbowText(text, true);
    }

    private String getPlayerJoinDate(UUID playerUUID) {
        Long joinTime = this.playerJoinTimes.get(playerUUID);
        if (joinTime != null && joinTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            return sdf.format(new Date(joinTime.longValue()));
        }
        return "2026/01/01";
    }

    private String formatJoinDate(String inputDate) {
        try {
            if (inputDate == null || inputDate.isEmpty()) {
                return "2026/01/01";
            }
            String[] parts = inputDate.split("/");
            if (parts.length != 3) {
                return "2026/01/01";
            }
            String month = parts[0];
            String day = parts[1];
            String year = parts[2];
            return year + "/" + month + "/" + day;
        } catch (Exception e) {
            return "2026/01/01";
        }
    }

    private long getJoinDateTimestamp(String formattedDate) {
        try {
            if (formattedDate == null || formattedDate.isEmpty()) {
                return System.currentTimeMillis();
            }
            String[] parts = formattedDate.split("/");
            if (parts.length != 3) {
                return System.currentTimeMillis();
            }
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1;
            int day = Integer.parseInt(parts[2]);
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, 0, 0, 0);
            return calendar.getTimeInMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String getPlayerTotalPlayTime(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        
        if (player != null && player.isOnline()) {
            // 对在线玩家使用PlaceholderAPI获取实时总在线时长
            String placeholderResult = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%playtimes_playtime%");
            int totalMinutes = parsePlayTimeToMinutes(placeholderResult);
            
            long days = totalMinutes / (24 * 60);
            long hours = (totalMinutes % (24 * 60)) / 60;
            long minutes = totalMinutes % 60;
            
            if (days > 0) {
                return days + "天" + hours + "时" + minutes + "分";
            } else if (hours > 0) {
                return hours + "时" + minutes + "分";
            } else {
                return minutes + "分";
            }
        } else {
            // 对离线玩家使用存储的值
            int totalMinutes = this.totalPlayTimeMinutes.getOrDefault(playerUUID, 0);
            
            long days = totalMinutes / (24 * 60);
            long hours = (totalMinutes % (24 * 60)) / 60;
            long minutes = totalMinutes % 60;
            
            if (days > 0) {
                return days + "天" + hours + "时" + minutes + "分";
            } else if (hours > 0) {
                return hours + "时" + minutes + "分";
            } else {
                return minutes + "分";
            }
        }
    }

    private int parsePlayTimeToMinutes(String playTimeStr) {
        try {
            if (playTimeStr == null || playTimeStr.isEmpty()) {
                return 0;
            }

            int days = 0;
            int hours = 0;
            int minutes = 0;

            int dayIndex = playTimeStr.indexOf("天");
            if (dayIndex != -1) {
                days = Integer.parseInt(playTimeStr.substring(0, dayIndex));
                playTimeStr = playTimeStr.substring(dayIndex + 1);
            }

            int hourIndex = playTimeStr.indexOf("时");
            if (hourIndex != -1) {
                hours = Integer.parseInt(playTimeStr.substring(0, hourIndex));
                playTimeStr = playTimeStr.substring(hourIndex + 1);
            }

            int minuteIndex = playTimeStr.indexOf("分");
            if (minuteIndex != -1) {
                minutes = Integer.parseInt(playTimeStr.substring(0, minuteIndex));
            }

            return days * 24 * 60 + hours * 60 + minutes;
        } catch (Exception e) {
            return 0;
        }
    }

    private void scheduleDailySnapshot() {
        Calendar now = Calendar.getInstance();
        Calendar nextMidnight = Calendar.getInstance();
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
        long delay = nextMidnight.getTimeInMillis() - now.getTimeInMillis();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID playerUUID = player.getUniqueId();
                String playTimeStr = getPlayerTotalPlayTime(playerUUID);
                int totalMinutes = parsePlayTimeToMinutes(playTimeStr);
                this.dailyPlayTimeSnapshots.put(playerUUID, totalMinutes);
                savePlaytimeData(playerUUID);
            }

            cleanupOldPlaytimeData();
            scheduleDailySnapshot();
        }, delay / 50);
    }

    private void savePlaytimeData(UUID playerUUID) {
        try {
            File playtimesFolder = new File(plugin.getDataFolder(), "playtimes");
            if (!playtimesFolder.exists()) {
                playtimesFolder.mkdirs();
            }

            File playerFile = new File(playtimesFolder, playerUUID.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();

            if (playerFile.exists()) {
                config.load(playerFile);
            }

            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            String dateKey = year + "-" + month + "-" + day;

            String totalPlayTimeStr = getPlayerTotalPlayTime(playerUUID);
            int totalMinutes = parsePlayTimeToMinutes(totalPlayTimeStr);
            int yesterdaySnapshot = this.dailyPlayTimeSnapshots.getOrDefault(playerUUID, 0).intValue();
            int todayMinutes = totalMinutes - yesterdaySnapshot;
            if (todayMinutes < 0) todayMinutes = 0;

            config.set(dateKey + ".total", totalMinutes);
            config.set(dateKey + ".today", todayMinutes);

            config.save(playerFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupOldPlaytimeData() {
        try {
            File playtimesFolder = new File(plugin.getDataFolder(), "playtimes");
            if (!playtimesFolder.exists()) {
                return;
            }

            int retainDays = plugin.getConfig().getInt("playtimeRetainDays", 15);

            Calendar cutoffDate = Calendar.getInstance();
            cutoffDate.add(Calendar.DAY_OF_YEAR, -retainDays);

            for (File playerFile : playtimesFolder.listFiles()) {
                if (playerFile.isFile() && playerFile.getName().endsWith(".yml")) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

                    for (String dateKey : config.getKeys(false)) {
                        try {
                            String[] parts = dateKey.split("-");
                            if (parts.length == 3) {
                                int year = Integer.parseInt(parts[0]);
                                int month = Integer.parseInt(parts[1]) - 1;
                                int day = Integer.parseInt(parts[2]);

                                Calendar dataDate = Calendar.getInstance();
                                dataDate.set(year, month, day);

                                if (dataDate.before(cutoffDate)) {
                                    config.set(dateKey, null);
                                }
                            }
                        } catch (Exception e) {
                        }
                    }

                    config.save(playerFile);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkPlayerPlaytime(CommandSender sender, UUID playerUUID, int year, int month, int day) {
        try {
            File playtimesFolder = new File(plugin.getDataFolder(), "playtimes");
            File playerFile = new File(playtimesFolder, playerUUID.toString() + ".yml");

            if (!playerFile.exists()) {
                sender.sendMessage("§c该玩家没有在线时长记录！");
                return;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
            String dateKey = year + "-" + month + "-" + day;

            if (!config.isConfigurationSection(dateKey)) {
                sender.sendMessage("§c该日期没有在线时长记录！");
                return;
            }

            int totalMinutes = config.getInt(dateKey + ".total", 0);
            int todayMinutes = config.getInt(dateKey + ".today", 0);

            long totalHours = totalMinutes / 60;
            long totalMins = totalMinutes % 60;
            long todayHours = todayMinutes / 60;
            long todayMins = todayMinutes % 60;

            String totalTimeStr = totalHours > 0 ? totalHours + "小时" + totalMins + "分钟" : totalMins + "分钟";
            String todayTimeStr = todayHours > 0 ? todayHours + "小时" + todayMins + "分钟" : todayMins + "分钟";

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
            String playerName = player.getName() != null ? player.getName() : "未知玩家";

            sender.sendMessage("§a===== " + playerName + " 的在线时长记录 =====");
            sender.sendMessage("§e日期: " + year + "-" + month + "-" + day);
            sender.sendMessage("§e总在线时长: " + totalTimeStr);
            sender.sendMessage("§e当日在线时长: " + todayTimeStr);
            sender.sendMessage("§a=============================");
        } catch (Exception e) {
            sender.sendMessage("§c查看在线时长时出错：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showGenderList(Player player, int page) {
        FileConfiguration config = this.plugin.getConfig();
        
        // 获取所有性别配置
        List<Integer> genderIds = new ArrayList<>();
        if (config.isConfigurationSection("genders.items")) {
            for (String key : config.getConfigurationSection("genders.items").getKeys(false)) {
                try {
                    genderIds.add(Integer.parseInt(key));
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            Collections.sort(genderIds);
        }
        
        int pageSize = 8;
        int totalPages = (int) Math.ceil((double) genderIds.size() / pageSize);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        this.genderPageCache.put(player.getUniqueId(), page);

        // 页眉分割线
        player.sendMessage("§m==============================================");
        player.sendMessage("§a§l性别列表 - 第" + page + "/" + totalPages + "页");
        player.sendMessage("§m==============================================");

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, genderIds.size());
        for (int i = startIndex; i < endIndex; i++) {
            int genderId = genderIds.get(i);
            String basePath = "genders.items." + genderId;
            String genderName = translateColorCodes(config.getString(basePath + ".name", ""));
            String englishName = translateColorCodes(config.getString(basePath + ".english", ""));
            String description = translateColorCodes(config.getString(basePath + ".description", ""));
            
            // 显示格式：[编号] <性别名称> - <英文名称> - <描述>
            String display = "§7[§e" + genderId + "§7] " + genderName + " §r§2- §6" + englishName + " §r§2- §r" + description;
            player.sendMessage(display);
        }

        player.sendMessage("§m==============================================");
        
        if (totalPages > 1) {
            if (page > 1) {
                net.kyori.adventure.text.Component prevPage = net.kyori.adventure.text.Component.text("§e§l[◀ 上一页]")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/genderpage " + (page - 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("§a点击查看上一页")));
                player.sendMessage(prevPage);
            }
            if (page < totalPages) {
                net.kyori.adventure.text.Component nextPage = net.kyori.adventure.text.Component.text("§e§l[下一页 ▶]")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/genderpage " + (page + 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("§a点击查看下一页")));
                player.sendMessage(nextPage);
            }
        }

        player.sendMessage("§7请输入性别编号选择性别，或输入'取消'取消设置");
    }

    private String translateColorCodes(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    private String getJoinDateFormat(long joinTime, String joinDateStr) {
        Calendar cal = Calendar.getInstance();

        cal.set(2025, 7, 31, 0, 0, 0);
        long time20250831 = cal.getTimeInMillis();

        cal.set(2026, 1, 27, 0, 0, 0);
        long time20260227 = cal.getTimeInMillis();

        cal.set(2026, 7, 31, 0, 0, 0);
        long time20260831 = cal.getTimeInMillis();

        cal.set(2027, 1, 27, 0, 0, 0);
        long time20270227 = cal.getTimeInMillis();

        cal.set(2027, 7, 31, 0, 0, 0);
        long time20270831 = cal.getTimeInMillis();

        cal.set(2028, 1, 27, 0, 0, 0);
        long time20280227 = cal.getTimeInMillis();

        cal.set(2028, 7, 31, 0, 0, 0);
        long time20280831 = cal.getTimeInMillis();

        cal.set(2029, 1, 27, 0, 0, 0);
        long time20290227 = cal.getTimeInMillis();

        cal.set(2029, 7, 31, 0, 0, 0);
        long time20290831 = cal.getTimeInMillis();

        cal.set(2030, 1, 27, 0, 0, 0);
        long time20300227 = cal.getTimeInMillis();

        if (joinTime <= time20250831) {
            return getRainbowText(joinDateStr, true);
        } else if (joinTime <= time20260227) {
            return "§r§e§l" + joinDateStr;
        } else if (joinTime <= time20260831) {
            return "§r§b§l" + joinDateStr;
        } else if (joinTime <= time20270227) {
            return "§r§6§l" + joinDateStr;
        } else if (joinTime <= time20270831) {
            return "§r§d§l" + joinDateStr;
        } else if (joinTime <= time20280227) {
            return "§c" + joinDateStr;
        } else if (joinTime <= time20280831) {
            return "§e" + joinDateStr;
        } else if (joinTime <= time20290227) {
            return "§a" + joinDateStr;
        } else if (joinTime <= time20290831) {
            return "§9" + joinDateStr;
        } else if (joinTime <= time20300227) {
            return "§f" + joinDateStr;
        } else {
            return "§7" + joinDateStr;
        }
    }

    private void openFriendMainMenu(Player player) {
        ItemStack sentRequestItem;
        ItemStack receivedRequestItem;
        Material material;
        Inventory inventory = Bukkit.createInventory(new FriendInventoryHolder(FriendInventoryType.MAIN, player.getUniqueId()), 54, "好友系统");
        
        // 填充背景
        for (int i = 0; i < 54; i++) {
            ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = background.getItemMeta();
            meta.setDisplayName("§7");
            background.setItemMeta(meta);
            inventory.setItem(i, background);
        }
        
        // 第一行：与主菜单一致，第5格为湖蓝色玻璃板
        ItemStack signIn = new ItemStack(Material.COMPASS);
        ItemMeta signInMeta = signIn.getItemMeta();
        signInMeta.setDisplayName("§c签到系统");
        signInMeta.setLore(Collections.singletonList("§7点击打开签到菜单"));
        signIn.setItemMeta(signInMeta);
        inventory.setItem(0, signIn);
        
        ItemStack events = new ItemStack(Material.GOAT_HORN);
        ItemMeta eventsMeta = events.getItemMeta();
        eventsMeta.setDisplayName("§6活动系统");
        eventsMeta.setLore(Collections.singletonList("§7暂时未开放"));
        events.setItemMeta(eventsMeta);
        inventory.setItem(1, events);
        
        ItemStack economy = new ItemStack(Material.GOLD_INGOT);
        ItemMeta economyMeta = economy.getItemMeta();
        economyMeta.setDisplayName("§e经济系统");
        economyMeta.setLore(Collections.singletonList("§7暂时未开放"));
        economy.setItemMeta(economyMeta);
        inventory.setItem(2, economy);
        
        ItemStack land = new ItemStack(Material.WOODEN_HOE);
        ItemMeta landMeta = land.getItemMeta();
        landMeta.setDisplayName("§a领地系统");
        landMeta.setLore(Collections.singletonList("§7暂时未开放"));
        land.setItemMeta(landMeta);
        inventory.setItem(3, land);
        
        // 第5格：湖蓝色玻璃板
        ItemStack cyanGlass = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta cyanMeta = cyanGlass.getItemMeta();
        cyanMeta.setDisplayName("§7");
        cyanGlass.setItemMeta(cyanMeta);
        inventory.setItem(4, cyanGlass);
        
        ItemStack quest = new ItemStack(Material.BOOK);
        ItemMeta questMeta = quest.getItemMeta();
        questMeta.setDisplayName("§9任务系统");
        questMeta.setLore(Collections.singletonList("§7暂时未开放"));
        quest.setItemMeta(questMeta);
        inventory.setItem(5, quest);
        
        ItemStack gift = new ItemStack(Material.SHULKER_BOX);
        ItemMeta giftMeta = gift.getItemMeta();
        giftMeta.setDisplayName("§d快递系统");
        giftMeta.setLore(Collections.singletonList("§7点击打开快递菜单"));
        gift.setItemMeta(giftMeta);
        inventory.setItem(6, gift);
        
        ItemStack teleport = new ItemStack(Material.ENDER_PEARL);
        ItemMeta teleportMeta = teleport.getItemMeta();
        teleportMeta.setDisplayName("§7传送系统");
        teleportMeta.setLore(Collections.singletonList("§7暂时未开放"));
        teleport.setItemMeta(teleportMeta);
        inventory.setItem(7, teleport);
        
        ItemStack admin = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta adminMeta = admin.getItemMeta();
        adminMeta.setDisplayName("§4管理员菜单");
        adminMeta.setLore(Collections.singletonList("§7需要管理员权限"));
        admin.setItemMeta(adminMeta);
        inventory.setItem(8, admin);
        
        // 第二行：湖蓝色玻璃板
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, cyanGlass);
        }
        
        // 3-5行：好友列表（上限27个）
        List<Friend> playerFriends = this.friends.getOrDefault(player.getUniqueId(), new ArrayList());
        int slot = 18; // 第三行开始
        for (int i = 0; i < Math.min(playerFriends.size(), 27); i++) {
            Friend friend = playerFriends.get(i);
            OfflinePlayer friendPlayer = Bukkit.getOfflinePlayer(friend.getFriendUUID());
            String friendName = friendPlayer.getName() != null ? friendPlayer.getName() : "未知玩家";
            if (friendPlayer.isOnline()) {
                material = Material.GREEN_WOOL;
            } else if (friend.isBanned()) {
                material = Material.RED_WOOL;
            } else {
                material = Material.GRAY_WOOL;
            }
            ItemStack friendItem = new ItemStack(material);
            ItemMeta meta = friendItem.getItemMeta();
            meta.setDisplayName("§a" + friendName);
            List<String> lore = new ArrayList<>();
            if (friendPlayer.isOnline()) {
                lore.add("§7状态: 在线");
            } else {
                lore.add("§7状态: 离线");
                Long lastJoinTime = this.lastJoinTimes.get(friend.getFriendUUID());
                if (lastJoinTime != null) {
                    Date lastJoinDate = new Date(lastJoinTime.longValue());
                    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String lastJoinStr = dateTimeFormat.format(lastJoinDate);
                    lore.add("§7上次上线: " + lastJoinStr);
                }
            }
            if (friend.isBanned()) {
                lore.add("§c已被封禁");
            }
            meta.setLore(lore);
            friendItem.setItemMeta(meta);
            inventory.setItem(slot, friendItem);
            slot++;
        }
        
        // 第6行1-6格：保持原有功能
        List<FriendRequest> sentRequestList = this.sentRequests.getOrDefault(player.getUniqueId(), new ArrayList());
        if (sentRequestList.isEmpty()) {
            sentRequestItem = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
            ItemMeta meta2 = sentRequestItem.getItemMeta();
            meta2.setDisplayName("§a已发送的申请");
            meta2.setLore(Collections.singletonList("§7无未处理的申请"));
            sentRequestItem.setItemMeta(meta2);
        } else {
            int requestCount = sentRequestList.size();
            Material material2 = requestCount >= 64 ? Material.RED_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
            sentRequestItem = new ItemStack(material2, Math.min(requestCount, 64));
            ItemMeta meta3 = sentRequestItem.getItemMeta();
            meta3.setDisplayName("§a已发送的申请");
            meta3.setLore(Collections.singletonList("§7申请数量: " + requestCount));
            sentRequestItem.setItemMeta(meta3);
        }
        for (int i2 = 45; i2 <= 47; i2++) {
            inventory.setItem(i2, sentRequestItem);
        }
        
        List<FriendRequest> receivedRequestList = this.receivedRequests.getOrDefault(player.getUniqueId(), new ArrayList());
        if (receivedRequestList.isEmpty()) {
            receivedRequestItem = new ItemStack(Material.LIGHT_BLUE_WOOL);
            ItemMeta meta4 = receivedRequestItem.getItemMeta();
            meta4.setDisplayName("§a待处理的申请");
            meta4.setLore(Collections.singletonList("§7无待处理的申请"));
            receivedRequestItem.setItemMeta(meta4);
        } else {
            int requestCount2 = receivedRequestList.size();
            Material material3 = requestCount2 >= 64 ? Material.RED_WOOL : Material.YELLOW_WOOL;
            receivedRequestItem = new ItemStack(material3, Math.min(requestCount2, 64));
            ItemMeta meta5 = receivedRequestItem.getItemMeta();
            meta5.setDisplayName("§a待处理的申请");
            meta5.setLore(Collections.singletonList("§7申请数量: " + requestCount2));
            receivedRequestItem.setItemMeta(meta5);
        }
        for (int i3 = 48; i3 <= 50; i3++) {
            inventory.setItem(i3, receivedRequestItem);
        }
        
        // 第6行7-9格：返回主菜单按钮
        ItemStack backToMain = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = backToMain.getItemMeta();
        backMeta.setDisplayName("§c返回主菜单");
        backMeta.setLore(Collections.singletonList("§7点击返回服务器主菜单"));
        backToMain.setItemMeta(backMeta);
        for (int i4 = 51; i4 <= 53; i4++) {
            inventory.setItem(i4, backToMain);
        }
        
        player.openInventory(inventory);
    }

    private void openSentRequestsMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new FriendInventoryHolder(FriendInventoryType.SENT_REQUESTS, player.getUniqueId()), 54, "已发送的申请");
        List<FriendRequest> sentRequestList = this.sentRequests.getOrDefault(player.getUniqueId(), new ArrayList());
        int slot = 0;
        for (FriendRequest request : sentRequestList) {
            if (slot >= 54) {
                break;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(request.getTargetUUID());
            String targetName = target.getName() != null ? target.getName() : "未知玩家";
            ItemStack requestItem = new ItemStack(Material.PAPER);
            ItemMeta meta = requestItem.getItemMeta();
            meta.setDisplayName("§a" + targetName);
            List<String> lore = new ArrayList<>();
            Date date = new Date(request.getTimestamp());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String timeStr = sdf.format(date);
            lore.add("§7申请日期: " + timeStr);
            lore.add("§7右键点击撤销申请");
            meta.setLore(lore);
            requestItem.setItemMeta(meta);
            inventory.setItem(slot, requestItem);
            slot++;
        }
        player.openInventory(inventory);
    }

    private void openReceivedRequestsMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new FriendInventoryHolder(FriendInventoryType.RECEIVED_REQUESTS, player.getUniqueId()), 54, "待处理的申请");
        List<FriendRequest> receivedRequestList = this.receivedRequests.getOrDefault(player.getUniqueId(), new ArrayList());
        int slot = 0;
        for (FriendRequest request : receivedRequestList) {
            if (slot >= 54) {
                break;
            }
            OfflinePlayer sender = Bukkit.getOfflinePlayer(request.getSenderUUID());
            String senderName = sender.getName() != null ? sender.getName() : "未知玩家";
            ItemStack requestItem = new ItemStack(Material.PAPER);
            ItemMeta meta = requestItem.getItemMeta();
            meta.setDisplayName("§a" + senderName);
            List<String> lore = new ArrayList<>();
            Date date = new Date(request.getTimestamp());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String timeStr = sdf.format(date);
            lore.add("§7申请日期: " + timeStr);
            lore.add("§7左键点击同意");
            lore.add("§7右键点击拒绝");
            meta.setLore(lore);
            requestItem.setItemMeta(meta);
            inventory.setItem(slot, requestItem);
            slot++;
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof FriendInventoryHolder) {
            FriendInventoryHolder holder = (FriendInventoryHolder) event.getInventory().getHolder();
            Player player = (Player) event.getWhoClicked();
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= event.getInventory().getSize()) {
                return;
            }
            if (holder.getType() == FriendInventoryType.MAIN) {
                handleMainMenuClick(event, player, holder);
                return;
            }
            if (holder.getType() == FriendInventoryType.SENT_REQUESTS) {
                handleSentRequestsMenuClick(event, player, holder);
                return;
            }
            if (holder.getType() == FriendInventoryType.RECEIVED_REQUESTS) {
                handleReceivedRequestsMenuClick(event, player, holder);
            } else if (holder.getType() == FriendInventoryType.INTERACTION) {
                handleInteractionMenuClick(event, player, holder);
            } else if (holder.getType() == FriendInventoryType.PASSPORT) {
                handlePassportMenuClick(event, player, holder);
            }
        }
    }

    private void handleInteractionMenuClick(InventoryClickEvent event, Player player, FriendInventoryHolder holder) {
        UUID targetUUID;
        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || (targetUUID = holder.getTargetUUID()) == null) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§c玩家不存在或已离线！");
            player.closeInventory();
            return;
        }
        Player onlineTarget = target.getPlayer();
        if (onlineTarget == null) {
            player.sendMessage("§c玩家不存在或已离线！");
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 19:
                player.closeInventory();
                player.performCommand("passport " + onlineTarget.getName());
                break;
            case 21:
                player.closeInventory();
                player.performCommand("friend " + onlineTarget.getName());
                break;
            case 23:
                player.closeInventory();
                player.performCommand("gift " + onlineTarget.getName());
                break;
            case 25:
                player.closeInventory();
                player.performCommand("trade " + onlineTarget.getName());
                break;
        }
    }

    private void handlePassportMenuClick(InventoryClickEvent event, Player player, FriendInventoryHolder holder) {
        UUID targetUUID;
        VisibilityMode nextMode;
        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) {
        }
        boolean editable = holder.isEditable();
        if (editable && (targetUUID = holder.getTargetUUID()) != null) {
            switch (slot) {
                case 14:
                    if (event.isLeftClick()) {
                        String birthdayStr = this.birthdays.getOrDefault(targetUUID, "");
                        if (birthdayStr.isEmpty()) {
                            player.closeInventory();
                            player.sendMessage("§a请在聊天栏输入生日，格式：年 月 日");
                            player.sendMessage("§a例如：2000 1 1");
                            player.sendMessage("§a仅能设置一次，设定后无法修改");
                            player.sendMessage("§a输入后发送confirm确认");
                            this.editModes.put(player.getUniqueId(), EditMode.BIRTHDAY);
                        }
                    }
                    break;
                case 24:
                    if (event.isLeftClick()) {
                        player.closeInventory();
                        showGenderList(player, 1);
                        this.editModes.put(player.getUniqueId(), EditMode.GENDER);
                    }
                    break;
                case 15:
                    if (event.isLeftClick()) {
                        player.closeInventory();
                        player.sendMessage("§a请在聊天栏输入新的QQ号：");
                        this.editModes.put(player.getUniqueId(), EditMode.QQ);
                    } else if (event.isRightClick()) {
                        this.qqNumbers.remove(targetUUID);
                        player.sendMessage("§a已清空QQ号！");
                        openPassportMenu(player, targetUUID, true);
                    }
                    break;
                case 16:
                    if (event.isLeftClick()) {
                        player.closeInventory();
                        player.sendMessage("§a请在聊天栏输入新的邮箱地址：");
                        this.editModes.put(player.getUniqueId(), EditMode.EMAIL);
                    } else if (event.isRightClick()) {
                        this.emailAddresses.remove(targetUUID);
                        player.sendMessage("§a已清空邮箱地址！");
                        openPassportMenu(player, targetUUID, true);
                    }
                    break;
                case 49:
                    if (event.isLeftClick()) {
                        VisibilityMode currentMode = this.visibilityModes.getOrDefault(targetUUID, VisibilityMode.PUBLIC);
                        switch (currentMode) {
                            case PUBLIC:
                                nextMode = VisibilityMode.PRIVATE_EDITABLE;
                                break;
                            case PRIVATE_EDITABLE:
                                nextMode = VisibilityMode.FRIENDS_ONLY;
                                break;
                            case FRIENDS_ONLY:
                                nextMode = VisibilityMode.PRIVATE;
                                break;
                            case PRIVATE:
                                nextMode = VisibilityMode.PUBLIC;
                                break;
                            default:
                                nextMode = VisibilityMode.PUBLIC;
                                break;
                        }
                        this.visibilityModes.put(targetUUID, nextMode);
                        player.sendMessage("§a信息可见性已修改为：" + getVisibilityModeName(nextMode));
                        openPassportMenu(player, targetUUID, true);
                    }
                    break;
            }
        }
    }

    private void handleMainMenuClick(InventoryClickEvent event, Player player, FriendInventoryHolder holder) {
        int slot = event.getRawSlot();
        
        // 处理第一行按钮点击
        if (slot >= 0 && slot < 9) {
            switch (slot) {
                case 0: // 签到系统
                    player.closeInventory();
                    player.performCommand("daily-check");
                    break;
                case 4: // 好友系统（当前菜单）
                    break;
                case 6: // 快递系统
                    player.closeInventory();
                    player.performCommand("gift");
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
        
        // 处理好友列表点击
        if (slot >= 18 && slot < 45) {
            List<Friend> playerFriends = this.friends.getOrDefault(player.getUniqueId(), new ArrayList());
            int friendIndex = slot - 18; // 从第三行开始
            if (friendIndex < playerFriends.size()) {
                Friend friend = playerFriends.get(friendIndex);
                UUID friendUUID = friend.getFriendUUID();
                if (event.isLeftClick()) {
                    openPassportMenu(player, friendUUID, false);
                    return;
                } else {
                    if (event.isRightClick() && event.isShiftClick()) {
                        OfflinePlayer friendPlayer = Bukkit.getOfflinePlayer(friendUUID);
                        String friendName = friendPlayer != null ? friendPlayer.getName() : "未知玩家";
                        player.sendMessage("§a确定要删除好友 " + friendName + " 吗？输入 [确认] 或 [取消]");
                        return;
                    }
                    return;
                }
            }
            return;
        }
        
        // 处理第6行1-6格
        if (slot >= 45 && slot <= 47) {
            openSentRequestsMenu(player);
        } else if (slot >= 48 && slot <= 50) {
            openReceivedRequestsMenu(player);
        } else if (slot >= 51 && slot <= 53) {
            // 处理返回主菜单按钮
            player.closeInventory();
            player.performCommand("menu");
        }
    }

    private void handleSentRequestsMenuClick(InventoryClickEvent event, Player player, FriendInventoryHolder holder) {
        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.PAPER) {
            return;
        }
        List<FriendRequest> sentRequestList = this.sentRequests.getOrDefault(player.getUniqueId(), new ArrayList());
        if (slot < sentRequestList.size()) {
            FriendRequest request = sentRequestList.get(slot);
            sentRequestList.remove(request);
            if (sentRequestList.isEmpty()) {
                this.sentRequests.remove(player.getUniqueId());
            } else {
                this.sentRequests.put(player.getUniqueId(), sentRequestList);
            }
            List<FriendRequest> receivedRequestList = this.receivedRequests.getOrDefault(request.getTargetUUID(), new ArrayList());
            receivedRequestList.removeIf(r -> {
                return r.getSenderUUID().equals(player.getUniqueId()) && r.getTargetUUID().equals(request.getTargetUUID());
            });
            if (receivedRequestList.isEmpty()) {
                this.receivedRequests.remove(request.getTargetUUID());
            } else {
                this.receivedRequests.put(request.getTargetUUID(), receivedRequestList);
            }
            saveData();
            player.sendMessage("§a好友申请已撤销！");
            openFriendMainMenu(player);
        }
    }

    private void handleReceivedRequestsMenuClick(InventoryClickEvent event, Player player, FriendInventoryHolder holder) {
        Player onlineSender;
        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.PAPER) {
            return;
        }
        List<FriendRequest> receivedRequestList = this.receivedRequests.getOrDefault(player.getUniqueId(), new ArrayList());
        if (slot < receivedRequestList.size()) {
            FriendRequest request = receivedRequestList.get(slot);
            OfflinePlayer sender = Bukkit.getOfflinePlayer(request.getSenderUUID());
            if (event.isLeftClick()) {
                Friend friend1 = new Friend(player.getUniqueId(), request.getSenderUUID());
                Friend friend2 = new Friend(request.getSenderUUID(), player.getUniqueId());
                this.friends.computeIfAbsent(player.getUniqueId(), k -> {
                    return new ArrayList();
                }).add(friend1);
                this.friends.computeIfAbsent(request.getSenderUUID(), k2 -> {
                    return new ArrayList();
                }).add(friend2);
                player.sendMessage("§a已同意 " + sender.getName() + " 的好友申请！");
                if (sender.isOnline() && (onlineSender = sender.getPlayer()) != null) {
                    onlineSender.sendMessage("§a" + player.getName() + " 同意了你的好友申请！");
                }
            } else if (event.isRightClick()) {
                player.sendMessage("§a已拒绝 " + sender.getName() + " 的好友申请！");
            }
            receivedRequestList.remove(request);
            if (receivedRequestList.isEmpty()) {
                this.receivedRequests.remove(player.getUniqueId());
            } else {
                this.receivedRequests.put(player.getUniqueId(), receivedRequestList);
            }
            List<FriendRequest> sentRequestList = this.sentRequests.getOrDefault(request.getSenderUUID(), new ArrayList());
            sentRequestList.removeIf(r -> {
                return r.getSenderUUID().equals(request.getSenderUUID()) && r.getTargetUUID().equals(player.getUniqueId());
            });
            if (sentRequestList.isEmpty()) {
                this.sentRequests.remove(request.getSenderUUID());
            } else {
                this.sentRequests.put(request.getSenderUUID(), sentRequestList);
            }
            saveData();
            openFriendMainMenu(player);
        }
    }

    public int getFriendCount(UUID playerUUID) {
        List<Friend> playerFriends = this.friends.getOrDefault(playerUUID, new ArrayList());
        return playerFriends.size();
    }

    public int getActiveFriendCount(UUID playerUUID) {
        List<Friend> playerFriends = this.friends.getOrDefault(playerUUID, new ArrayList());
        int activeCount = 0;
        long sevenDaysAgo = System.currentTimeMillis() - 604800000;
        for (Friend friend : playerFriends) {
            UUID friendUUID = friend.getFriendUUID();
            Long lastJoinTime = this.lastJoinTimes.get(friendUUID);
            if (lastJoinTime != null && lastJoinTime.longValue() >= sevenDaysAgo) {
                activeCount++;
            }
        }
        return activeCount;
    }

    private static class FriendInventoryHolder implements InventoryHolder {
        private final FriendInventoryType type;
        private final UUID playerUUID;
        private final UUID targetUUID;
        private final boolean editable;

        public FriendInventoryHolder(FriendInventoryType type, UUID playerUUID) {
            this.type = type;
            this.playerUUID = playerUUID;
            this.targetUUID = null;
            this.editable = false;
        }

        public FriendInventoryHolder(FriendInventoryType type, UUID playerUUID, UUID targetUUID) {
            this.type = type;
            this.playerUUID = playerUUID;
            this.targetUUID = targetUUID;
            this.editable = false;
        }

        public FriendInventoryHolder(FriendInventoryType type, UUID playerUUID, UUID targetUUID, boolean editable) {
            this.type = type;
            this.playerUUID = playerUUID;
            this.targetUUID = targetUUID;
            this.editable = editable;
        }

        public FriendInventoryType getType() {
            return this.type;
        }

        public UUID getPlayerUUID() {
            return this.playerUUID;
        }

        public UUID getTargetUUID() {
            return this.targetUUID;
        }

        public boolean isEditable() {
            return this.editable;
        }

        public Inventory getInventory() {
            return null;
        }
    }

    private static class Friend {
        private final UUID playerUUID;
        private final UUID friendUUID;
        private final boolean banned = false;

        public Friend(UUID playerUUID, UUID friendUUID) {
            this.playerUUID = playerUUID;
            this.friendUUID = friendUUID;
        }

        public UUID getFriendUUID() {
            return this.friendUUID;
        }

        public boolean isBanned() {
            return this.banned;
        }

        public void saveToConfig(YamlConfiguration config, String path) {
            config.set(path + ".player", this.playerUUID.toString());
            config.set(path + ".friend", this.friendUUID.toString());
            config.set(path + ".banned", Boolean.valueOf(this.banned));
        }

        public static Friend loadFromConfig(YamlConfiguration config, String path) {
            try {
                UUID playerUUID = UUID.fromString(config.getString(path + ".player"));
                UUID friendUUID = UUID.fromString(config.getString(path + ".friend"));
                return new Friend(playerUUID, friendUUID);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static class FriendRequest {
        private final UUID senderUUID;
        private final UUID targetUUID;
        private final long timestamp = System.currentTimeMillis();

        public FriendRequest(UUID senderUUID, UUID targetUUID) {
            this.senderUUID = senderUUID;
            this.targetUUID = targetUUID;
        }

        public UUID getSenderUUID() {
            return this.senderUUID;
        }

        public UUID getTargetUUID() {
            return this.targetUUID;
        }

        public long getTimestamp() {
            return this.timestamp;
        }

        public void saveToConfig(YamlConfiguration config, String path) {
            config.set(path + ".sender", this.senderUUID.toString());
            config.set(path + ".target", this.targetUUID.toString());
            config.set(path + ".timestamp", Long.valueOf(this.timestamp));
        }

        public static FriendRequest loadFromConfig(YamlConfiguration config, String path) {
            try {
                UUID senderUUID = UUID.fromString(config.getString(path + ".sender"));
                UUID targetUUID = UUID.fromString(config.getString(path + ".target"));
                long timestamp = config.getLong(path + ".timestamp");
                FriendRequest request = new FriendRequest(senderUUID, targetUUID);
                try {
                    Field timestampField = FriendRequest.class.getDeclaredField("timestamp");
                    timestampField.setAccessible(true);
                    timestampField.set(request, Long.valueOf(timestamp));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return request;
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
