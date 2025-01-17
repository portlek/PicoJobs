package com.gmail.picono435.picojobs;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONArray;
import org.json.JSONObject;

import com.gmail.picono435.picojobs.api.EconomyImplementation;
import com.gmail.picono435.picojobs.api.Job;
import com.gmail.picono435.picojobs.api.JobPlayer;
import com.gmail.picono435.picojobs.api.PicoJobsAPI;
import com.gmail.picono435.picojobs.api.Type;
import com.gmail.picono435.picojobs.commands.JobsAdminCommand;
import com.gmail.picono435.picojobs.commands.JobsCommand;
import com.gmail.picono435.picojobs.hooks.PlaceholderAPIHook;
import com.gmail.picono435.picojobs.hooks.PlayerPointsHook;
import com.gmail.picono435.picojobs.hooks.VaultHook;
import com.gmail.picono435.picojobs.hooks.economy.ExpImplementation;
import com.gmail.picono435.picojobs.hooks.economy.TokenManagerImplementation;
import com.gmail.picono435.picojobs.listeners.AliasesListeners;
import com.gmail.picono435.picojobs.listeners.ClickInventoryListener;
import com.gmail.picono435.picojobs.listeners.CreatePlayerListener;
import com.gmail.picono435.picojobs.listeners.ExecuteCommandListener;
import com.gmail.picono435.picojobs.listeners.jobs.FisherListener;
import com.gmail.picono435.picojobs.listeners.jobs.KillEntityListener;
import com.gmail.picono435.picojobs.listeners.jobs.KillerListener;
import com.gmail.picono435.picojobs.listeners.jobs.MilkListener;
import com.gmail.picono435.picojobs.listeners.jobs.PlaceListener;
import com.gmail.picono435.picojobs.listeners.jobs.RepairListener;
import com.gmail.picono435.picojobs.listeners.jobs.ShearListener;
import com.gmail.picono435.picojobs.listeners.jobs.SmeltListener;
import com.gmail.picono435.picojobs.listeners.jobs.TameListener;
import com.gmail.picono435.picojobs.listeners.jobs.BreakListener;
import com.gmail.picono435.picojobs.listeners.jobs.CraftListener;
import com.gmail.picono435.picojobs.listeners.jobs.EatListener;
import com.gmail.picono435.picojobs.listeners.jobs.EnchantListener;
import com.gmail.picono435.picojobs.listeners.jobs.FillListener;
import com.gmail.picono435.picojobs.managers.LanguageManager;
import com.gmail.picono435.picojobs.utils.FileCreator;

public class PicoJobsPlugin extends JavaPlugin {

	//PLUGIN
	private static PicoJobsPlugin instance;
	private boolean legacy;
	private boolean oldVersion;
	private String lastestPluginVersion;
	private String downloadUrl;
	private Metrics metrics;
	//DATA
	public Map<String, EconomyImplementation> economies = new HashMap<String, EconomyImplementation>();
	//JOBS DATA
	public Map<String, Job> jobs = new HashMap<String, Job>(); 
	//PLAYERS DATA
	public Map<UUID, JobPlayer> playersdata = new HashMap<UUID, JobPlayer>();
	public Map<String, Integer> salary = new HashMap<String, Integer>();
	public Map<String, Boolean> inJob = new HashMap<String, Boolean>();
	
	public void onEnable() {
		instance = this;
		Bukkit.getServer();
		sendConsoleMessage(Level.INFO, "Plugin created by: Picono435#2011. Thank you for use it.");
		
		if(checkLegacy() ) {
			sendConsoleMessage(Level.WARNING, "Checked that you are using a LEGACY spigot/bukkit version. We will use the old Material Support.");
		}
		
		// CREATING AND CONFIGURING INTERNAL FILES
		saveDefaultConfig();
		LanguageManager.createLanguageFile();
		if(!FileCreator.generateFiles());
		if(!getConfig().contains("config-version") || !getConfig().getString("config-version").equalsIgnoreCase(getDescription().getVersion())) {
			sendConsoleMessage(Level.WARNING, "You were using a old configuration file... Updating it and removing comments, for more information check our WIKI.");
			getConfig().options().copyDefaults(true);
			getConfig().set("config-version", getDescription().getVersion());
			saveConfig();
			LanguageManager.updateFile();
		}
		
		// STARTING BSTATS
        metrics = new Metrics(this, 8553);
        
        // SETTING UP AND REQUIRED AND OPTIONAL DEPENDENCIES
        PicoJobsAPI.registerEconomy(new ExpImplementation());
        VaultHook.setupVault();
        PlayerPointsHook.setupPlayerPoints();
        PicoJobsAPI.registerEconomy(new TokenManagerImplementation());
        PlaceholderAPIHook.setupPlaceholderAPI();
        new BukkitRunnable() {
        	public void run() {
        		Bukkit.getConsoleSender().sendMessage("[PicoJobs] " + economies.size() + " " + ChatColor.GREEN + "economy implementations successfully registered!");
        	}
        }.runTaskLater(this, 1L);
        
        // GETTING DATA FROM STORAGE
		sendConsoleMessage(Level.INFO, "Getting data from storage...");
		if(!generateJobsFromConfig()) return;
		PicoJobsAPI.getStorageManager().getData();
		metrics.addCustomChart(new SingleLineChart("created_jobs", new Callable<Integer>() {
        	@Override
        	public Integer call() throws Exception {
        		return jobs.size();
        	}
        }));
	
		// REGISTERING COMMANDS
		this.getCommand("jobs").setExecutor(new JobsCommand());
		this.getCommand("jobsadmin").setExecutor(new JobsAdminCommand());
		// REGISTERING LISTENERS
		Bukkit.getPluginManager().registerEvents(new CreatePlayerListener(), this);
		Bukkit.getPluginManager().registerEvents(new ClickInventoryListener(), this);
		Bukkit.getPluginManager().registerEvents(new ExecuteCommandListener(), this);
		Bukkit.getPluginManager().registerEvents(new AliasesListeners(), this);
		Bukkit.getPluginManager().registerEvents(new BreakListener(), this);
		Bukkit.getPluginManager().registerEvents(new TameListener(), this);
		Bukkit.getPluginManager().registerEvents(new ShearListener(), this);
		Bukkit.getPluginManager().registerEvents(new FillListener(), this);
		Bukkit.getPluginManager().registerEvents(new KillerListener(), this);
		Bukkit.getPluginManager().registerEvents(new FisherListener(), this);
		Bukkit.getPluginManager().registerEvents(new PlaceListener(), this);
		Bukkit.getPluginManager().registerEvents(new CraftListener(), this);
		Bukkit.getPluginManager().registerEvents(new EatListener(), this);
		Bukkit.getPluginManager().registerEvents(new EnchantListener(), this);
		Bukkit.getPluginManager().registerEvents(new MilkListener(), this);
		Bukkit.getPluginManager().registerEvents(new RepairListener(), this);
		Bukkit.getPluginManager().registerEvents(new SmeltListener(), this);
		Bukkit.getPluginManager().registerEvents(new KillEntityListener(), this);
		
		sendConsoleMessage(Level.INFO, "The plugin was succefully enabled.");
				
		checkVersion();
				
		long saveInterval = PicoJobsAPI.getSettingsManager().getSaveInterval();
		if(saveInterval != 0) {
			new BukkitRunnable() {
				public void run() {
					PicoJobsAPI.getStorageManager().saveData();
				}
			}.runTaskTimerAsynchronously(this, saveInterval, saveInterval);
		}
	}
	
	public void onDisable() {
		sendConsoleMessage(Level.INFO, "Saving data and configurations...");
		jobs.clear();
		PicoJobsAPI.getStorageManager().saveData();
		
		sendConsoleMessage(Level.INFO, "The plugin was succefully disabled.");
	}
	
	public static PicoJobsPlugin getInstance() {
		return instance;
	}
	
	public void sendConsoleMessage(Level level, String message) {
		this.getLogger().log(level, message);
	}
	
	public boolean isLegacy() {
		return legacy;
	}
	
	public boolean isOldVersion() {
		return oldVersion;
	}
	
	public String getLastestPluginVersion() {
		return lastestPluginVersion;
	}
	
	public String getLastestDownloadUrl() {
		return downloadUrl;
	}
	
	public boolean generateJobsFromConfig() {
		jobs.clear();
		ConfigurationSection jobsc = FileCreator.getJobsConfig().getConfigurationSection("jobs");
		for(String jobid : jobsc.getKeys(false)) {
			ConfigurationSection jobc = jobsc.getConfigurationSection(jobid);
			String displayname = jobc.getString("displayname");
			String tag = jobc.getString("tag");
			String typeString = jobc.getString("type");
			Type type = Type.getType(typeString.toUpperCase());
			double method = jobc.getDouble(type.getConfigMethod());
			double salary = jobc.getDouble("salary");
			boolean requiresPermission = jobc.getBoolean("require-permission");
			double salaryFrequency = jobc.getDouble("salary-frequency");
			double methodFrequency = jobc.getDouble("method-frequency");
			String economy = jobc.getString("economy");
			if(economy != null) {
				economy = economy.toUpperCase();
			}
			String workMessage = jobc.getString("work-message");
			ConfigurationSection guic = jobc.getConfigurationSection("gui");
			int slot = guic.getInt("slot");
			String item = guic.getString("item");
			int itemData = guic.getInt("item-data");
			boolean enchanted = guic.getBoolean("enchanted");
			
			// CALCULATING OPTIONALS
			
			boolean useWhitelist = jobc.getBoolean("use-whitelist");
			List<String> whitelist = jobc.getStringList(type.getWhitelistConfig() + "-whitelist");
			
			Job job = new Job(jobid, displayname, tag, type, method, salary, requiresPermission, salaryFrequency, methodFrequency, economy, workMessage, slot, item, itemData, enchanted, useWhitelist, whitelist);
			jobs.put(jobid, job);
						
			metrics.addCustomChart(new DrilldownPie("jobs", () -> {
		        Map<String, Map<String, Integer>> map = new HashMap<>();
		        Map<String, Integer> entry = new HashMap<>();
		        entry.put(jobid, 1);
		        map.put(type.name(), entry);
		        return map;
		    }));

			metrics.addCustomChart(new DrilldownPie("active_economy", () -> {
		        Map<String, Map<String, Integer>> map = new HashMap<>();
		        Map<String, Integer> entry = new HashMap<>();
		        String eco = job.getEconomy();
		        if(eco.equalsIgnoreCase("VAULT")) {
		        	if(VaultHook.isEnabled() && VaultHook.hasEconomyPlugin()) {
		        		entry.put(VaultHook.getEconomy().getName(), 1);
			        } else {
			        	entry.put("Others", 1);
			        }
		        	map.put("VAULT", entry);
		        } else {
		        	entry.put(eco, 1);
		        	map.put(eco, entry);
		        }
		        return map;
		    }));
		}
		return true;
	}
	
	private boolean checkLegacy() {
		try {
			String serverVersionString = Bukkit.getBukkitVersion();
			int spaceIndex = serverVersionString.indexOf("-");
			serverVersionString = serverVersionString.substring(0, spaceIndex);
			DefaultArtifactVersion legacyVersion = new DefaultArtifactVersion("1.13.2");
			DefaultArtifactVersion serverVersion = new DefaultArtifactVersion(serverVersionString);
			if(serverVersion.compareTo(legacyVersion) <= 0) {
				legacy = true;
			}
			return legacy;
		} catch (Exception e) {
			return legacy;
		}
	}
	
	private void checkVersion() {
		String version = "1.0";
		try {
            URL url = new URL("https://servermods.forgesvc.net/servermods/files?projectIds=385252");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setInstanceFollowRedirects(false);

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            JSONArray jsonArray =  new JSONArray(content.toString());
            JSONObject json = (JSONObject) jsonArray.get(jsonArray.length() - 1);
            version = (String)json.get("name");
            version = version.replaceFirst("PicoJobs ", "");

			DefaultArtifactVersion pluginVersion = new DefaultArtifactVersion(getDescription().getVersion());
			DefaultArtifactVersion lastestVersion = new DefaultArtifactVersion(version);
			lastestPluginVersion = version;
			downloadUrl = (String)json.get("downloadUrl");
			if(lastestVersion.compareTo(pluginVersion) > 0) {
				new BukkitRunnable() {
					public void run() {
						sendConsoleMessage(Level.WARNING, "Version: " + lastestVersion.toString() + " is out! You are still running version: " + pluginVersion.toString());
						if(getConfig().getBoolean("auto-update")) {
							if(updatePlugin(Bukkit.getConsoleSender(), "[PicoJobs] Plugin was updated to version "+ lastestVersion.toString() + " sucefully. Please restart the server to finish the update.")) {
								sendConsoleMessage(Level.INFO, "Updating the plugin to the lastest version...");
							} else {
								sendConsoleMessage(Level.WARNING, "An error occuried while updating the plugin.");
							}
						}
						return;
					}
				}.runTaskLater(this, 40L);
				oldVersion = true;
			} else {
				new BukkitRunnable() {
					public void run() {
						sendConsoleMessage(Level.INFO, "You are using the lastest version of the plugin.");
						return;
					}
				}.runTaskLater(this, 40L);
			}
		} catch (Exception e) {
			sendConsoleMessage(Level.WARNING, "Could not get the lastest version.");
			return;
		}
	}
	
	public boolean updatePlugin(CommandSender p, String message) {
		try {
			URL url = new URL(PicoJobsPlugin.getInstance().getLastestDownloadUrl());
			
			Method getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
			getFileMethod.setAccessible(true);
			File oldFile = (File) getFileMethod.invoke(PicoJobsPlugin.getInstance());

			File fileOutput = new File(Bukkit.getUpdateFolderFile().getPath() + File.separatorChar + oldFile.getName());
			if(!fileOutput.exists()) {
				fileOutput.mkdirs();
			}
			
			downloadFile(url, fileOutput, p, message);
			return true;
		} catch(Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}
	
	private void downloadFile(URL url, File fileOutput, CommandSender p, String message) {
		new BukkitRunnable() {
			public void run() {
				try {
					if (fileOutput.exists()) {
						fileOutput.delete();
				    }
					fileOutput.createNewFile();
				    OutputStream out = new BufferedOutputStream(new FileOutputStream(fileOutput.getPath()));
				    URLConnection conn = url.openConnection();
				    String encoded = Base64.getEncoder().encodeToString(("username"+":"+"password").getBytes(StandardCharsets.UTF_8));  //Java 8
				    conn.setRequestProperty("Authorization", "Basic "+ encoded);
				    InputStream in = conn.getInputStream();
				    byte[] buffer = new byte[1024];

				    int numRead;
				    while ((numRead = in.read(buffer)) != -1) {
				        out.write(buffer, 0, numRead);
				    }
				    if (in != null) {
				        in.close();
				    }
				    if (out != null) {
				        out.close();
				    }
				    p.sendMessage(message);
				    oldVersion = false;
				} catch(Exception ex) {
					ex.printStackTrace();
				}
			}
		}.runTaskAsynchronously(PicoJobsPlugin.getInstance());
	}
}
