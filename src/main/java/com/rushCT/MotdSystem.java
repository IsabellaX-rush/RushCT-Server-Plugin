package com.rushCT;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class MotdSystem implements Listener {
    private final JavaPlugin plugin;
    private File motdFile;
    private YamlConfiguration motdConfig;
    private boolean enabled = true;
    private List<String> firstLines = new ArrayList<>();
    private List<String> secondLines = new ArrayList<>();
    private final Random random = new Random();
    
    // 虚假在线人数相关
    private boolean fakePlayersEnabled = true;
    private int maxPlayers = 20;
    private int playerBase = 0;
    private boolean randomFakePlayersEnabled = true;
    private int minFakePlayerPercentage = 30;
    private int maxFakePlayerPercentage = 50;
    private int currentFakePlayerCount = 0;

    public MotdSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadMotdConfig();
        
        // 初始化虚假在线人数
        if (this.fakePlayersEnabled && this.randomFakePlayersEnabled) {
            updateFakePlayerCount();
            // 每5分钟更新一次虚假在线人数
            new BukkitRunnable() {
                @Override
                public void run() {
                    updateFakePlayerCount();
                }
            }.runTaskTimer(plugin, 0L, 6000L); // 5分钟 = 6000 ticks
        }
    }

    private void loadMotdConfig() {
        this.motdFile = new File(this.plugin.getDataFolder(), "motd.yml");
        if (!this.motdFile.exists()) {
            this.plugin.getDataFolder().mkdirs();
            createDefaultMotdConfig();
        }
        this.motdConfig = YamlConfiguration.loadConfiguration(this.motdFile);
        this.enabled = this.motdConfig.getBoolean("enabled", true);
        this.firstLines = this.motdConfig.getStringList("first-lines");
        this.secondLines = this.motdConfig.getStringList("second-lines");
        
        // 加载虚假在线人数相关配置
        this.fakePlayersEnabled = this.motdConfig.getBoolean("fake-players.enabled", true);
        this.maxPlayers = this.motdConfig.getInt("fake-players.max-players", 20);
        this.playerBase = this.motdConfig.getInt("fake-players.player-base", 0);
        this.randomFakePlayersEnabled = this.motdConfig.getBoolean("fake-players.random-fake-players.enabled", true);
        this.minFakePlayerPercentage = this.motdConfig.getInt("fake-players.random-fake-players.min-percentage", 30);
        this.maxFakePlayerPercentage = this.motdConfig.getInt("fake-players.random-fake-players.max-percentage", 50);
        
        if (this.firstLines.isEmpty() || this.secondLines.isEmpty()) {
            this.enabled = false;
            this.plugin.getLogger().warning("MOTD系统已禁用：第一行或第二行文本列表为空！");
        }
    }

    private void createDefaultMotdConfig() {
        this.motdConfig = new YamlConfiguration();
        
        this.motdConfig.set("enabled", Boolean.valueOf(true));
        this.motdConfig.setComments("enabled", Arrays.asList("# 是否启用MOTD功能", "# true: 启用, false: 禁用"));
        
        this.motdConfig.set("first-lines", Arrays.asList(
            "<rainbow>--欢迎来到</rainbow>&l<gradient:#55FFFF:#5555FF>ServerName</gradient>&r&a生存&r<rainbow>服务器！--</rainbow>",
            "&7当前在线: &a%online%&7/&f%max%"
        ));
        this.motdConfig.setComments("first-lines", Arrays.asList(
            "# 第一行MOTD文本列表",
            "# 每次刷新服务器列表时会随机选择一条",
            "# 支持 & 颜色代码、<rainbow>彩虹文字、<#RRGGBB>自定义颜色和<gradient>渐变色",
            "# 示例: &c红色文字, <#FF5555>自定义颜色, <gradient:#FF0000:#00FF00>渐变色</gradient>"
        ));
        
        this.motdConfig.set("second-lines", Arrays.asList(
            "&6欢迎来到 <rainbow>ServerName</rainbow> &6服务器!",
            "&b体验 &d精彩 &b的游戏内容!"
        ));
        this.motdConfig.setComments("second-lines", Arrays.asList(
            "# 第二行MOTD文本列表",
            "# 每次刷新服务器列表时会随机选择一条",
            "# 支持 & 颜色代码、<rainbow>彩虹文字、<#RRGGBB>自定义颜色和<gradient>渐变色",
            "# 可用变量: %online% (在线人数), %max% (最大人数)"
        ));
        
        // 虚假在线人数配置
        this.motdConfig.set("fake-players.enabled", Boolean.valueOf(false));
        this.motdConfig.setComments("fake-players.enabled", Arrays.asList(
            "# 是否启用虚假在线人数",
            "# true: 启用, false: 禁用"
        ));
        
        this.motdConfig.set("fake-players.max-players", Integer.valueOf(20));
        this.motdConfig.setComments("fake-players.max-players", Arrays.asList(
            "# 最高玩家数",
            "# 服务器列表中显示的最大玩家数"
        ));
        
        this.motdConfig.set("fake-players.player-base", Integer.valueOf(0));
        this.motdConfig.setComments("fake-players.player-base", Arrays.asList(
            "# 玩家底数",
            "# 基础的虚假玩家数量"
        ));
        
        this.motdConfig.set("fake-players.random-fake-players.enabled", Boolean.valueOf(false));
        this.motdConfig.setComments("fake-players.random-fake-players.enabled", Arrays.asList(
            "# 是否启用随机假玩家数",
            "# true: 启用, false: 禁用"
        ));
        
        this.motdConfig.set("fake-players.random-fake-players.min-percentage", Integer.valueOf(30));
        this.motdConfig.setComments("fake-players.random-fake-players.min-percentage", Arrays.asList(
            "# 随机假玩家占比范围最小值",
            "# 单位: %"
        ));
        
        this.motdConfig.set("fake-players.random-fake-players.max-percentage", Integer.valueOf(50));
        this.motdConfig.setComments("fake-players.random-fake-players.max-percentage", Arrays.asList(
            "# 随机假玩家占比范围最大值",
            "# 单位: %"
        ));
        
        try {
            this.motdConfig.save(this.motdFile);
        } catch (IOException e) {
            this.plugin.getLogger().severe("无法创建motd.yml配置文件！");
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!this.enabled) {
            return;
        }
        
        if (this.firstLines.isEmpty() || this.secondLines.isEmpty()) {
            return;
        }
        
        int realOnlinePlayers = event.getNumPlayers();
        int onlinePlayers = realOnlinePlayers;
        int maxPlayers = this.maxPlayers;
        
        // 处理虚假在线人数
        if (this.fakePlayersEnabled) {
            if (this.randomFakePlayersEnabled) {
                onlinePlayers = realOnlinePlayers + this.playerBase + this.currentFakePlayerCount;
            } else {
                onlinePlayers = realOnlinePlayers + this.playerBase;
            }
            event.setMaxPlayers(maxPlayers);
        }
        
        String firstLine = this.firstLines.get(this.random.nextInt(this.firstLines.size()));
        String secondLine = this.secondLines.get(this.random.nextInt(this.secondLines.size()));
        
        firstLine = formatMotdText(firstLine, onlinePlayers, maxPlayers);
        secondLine = formatMotdText(secondLine, onlinePlayers, maxPlayers);
        
        event.setMotd(firstLine + "\n" + secondLine);
    }

    private String formatMotdText(String text, int online, int max) {
        String formatted = text;
        
        formatted = formatted.replace("%online%", String.valueOf(online));
        formatted = formatted.replace("%max%", String.valueOf(max));
        
        formatted = ChatColor.translateAlternateColorCodes('&', formatted);
        
        formatted = formatCustomColorText(formatted);
        formatted = formatGradientText(formatted);
        formatted = formatRainbowText(formatted);
        
        return formatted;
    }

    private String formatCustomColorText(String text) {
        String result = text;
        int startIndex;
        while ((startIndex = result.indexOf("<#")) != -1) {
            int endIndex = result.indexOf(">", startIndex);
            if (endIndex == -1) {
                break;
            }
            
            String colorCode = result.substring(startIndex + 2, endIndex);
            if (colorCode.length() == 6) {
                try {
                    // 验证颜色代码是否有效
                    Integer.parseInt(colorCode, 16);
                    String customColor = "§x";
                    for (char c : colorCode.toCharArray()) {
                        customColor += "§" + c;
                    }
                    
                    int textEndIndex = result.indexOf("</#", endIndex);
                    if (textEndIndex == -1) {
                        break;
                    }
                    
                    String coloredText = result.substring(endIndex + 1, textEndIndex);
                    String formattedText = customColor + coloredText;
                    
                    result = result.substring(0, startIndex) + formattedText + result.substring(textEndIndex + 4);
                } catch (NumberFormatException e) {
                    // 无效的颜色代码，跳过
                    result = result.substring(0, startIndex) + result.substring(endIndex + 1);
                }
            } else {
                // 颜色代码长度不正确，跳过
                result = result.substring(0, startIndex) + result.substring(endIndex + 1);
            }
        }
        return result;
    }

    private String formatGradientText(String text) {
        String result = text;
        int startIndex;
        while ((startIndex = result.indexOf("<gradient:")) != -1) {
            int endIndex = result.indexOf(">", startIndex);
            if (endIndex == -1) {
                break;
            }
            
            String gradientColors = result.substring(startIndex + 10, endIndex);
            String[] colorCodes = gradientColors.split(":");
            if (colorCodes.length >= 2) {
                int textEndIndex = result.indexOf("</gradient>", endIndex);
                if (textEndIndex == -1) {
                    break;
                }
                
                String gradientText = result.substring(endIndex + 1, textEndIndex);
                String formattedText = getGradientText(gradientText, colorCodes);
                
                result = result.substring(0, startIndex) + formattedText + result.substring(textEndIndex + 11);
            } else {
                // 颜色数量不足，跳过
                result = result.substring(0, startIndex) + result.substring(endIndex + 1);
            }
        }
        return result;
    }

    private String formatRainbowText(String text) {
        String result = text;
        int startIndex;
        while ((startIndex = result.indexOf("<rainbow>")) != -1) {
            int endIndex = result.indexOf("</rainbow>", startIndex);
            if (endIndex == -1) {
                break;
            }
            
            String rainbowText = result.substring(startIndex + 9, endIndex);
            String rainbowColored = getRainbowText(rainbowText);
            
            result = result.substring(0, startIndex) + rainbowColored + result.substring(endIndex + 10);
        }
        return result;
    }

    private String getRainbowText(String text) {
        char[] chars = text.toCharArray();
        String[] colors = {"§r§c", "§6", "§e", "§a", "§b", "§9", "§d"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            result.append(colors[i % colors.length]).append(chars[i]);
        }
        return result.toString();
    }

    private String getGradientText(String text, String[] colorCodes) {
        char[] chars = text.toCharArray();
        StringBuilder result = new StringBuilder();
        
        int colorCount = colorCodes.length;
        int charCount = chars.length;
        
        for (int i = 0; i < charCount; i++) {
            float progress = (float) i / (charCount - 1);
            int colorIndex = (int) (progress * (colorCount - 1));
            float colorProgress = progress * (colorCount - 1) - colorIndex;
            
            String startColor = colorCodes[colorIndex];
            String endColor = colorCodes[Math.min(colorIndex + 1, colorCount - 1)];
            
            String mixedColor = mixColors(startColor, endColor, colorProgress);
            result.append(mixedColor).append(chars[i]);
        }
        
        return result.toString();
    }

    private String mixColors(String color1, String color2, float progress) {
        try {
            int r1 = Integer.parseInt(color1.substring(1, 3), 16);
            int g1 = Integer.parseInt(color1.substring(3, 5), 16);
            int b1 = Integer.parseInt(color1.substring(5, 7), 16);
            
            int r2 = Integer.parseInt(color2.substring(1, 3), 16);
            int g2 = Integer.parseInt(color2.substring(3, 5), 16);
            int b2 = Integer.parseInt(color2.substring(5, 7), 16);
            
            int r = (int) (r1 + (r2 - r1) * progress);
            int g = (int) (g1 + (g2 - g1) * progress);
            int b = (int) (b1 + (b2 - b1) * progress);
            
            String hex = String.format("%02x%02x%02x", r, g, b);
            String customColor = "§x";
            for (char c : hex.toCharArray()) {
                customColor += "§" + c;
            }
            
            return customColor;
        } catch (Exception e) {
            return "§r";
        }
    }

    public void reloadMotdConfig() {
        loadMotdConfig();
        this.plugin.getLogger().info("MOTD配置已重新加载！");
    }
    
    private void updateFakePlayerCount() {
        if (this.fakePlayersEnabled && this.randomFakePlayersEnabled) {
            int availableSlots = this.maxPlayers - this.playerBase;
            if (availableSlots > 0) {
                int percentage = this.minFakePlayerPercentage + this.random.nextInt(this.maxFakePlayerPercentage - this.minFakePlayerPercentage + 1);
                this.currentFakePlayerCount = (int) (availableSlots * percentage / 100.0);
            }
        }
    }
    

}
