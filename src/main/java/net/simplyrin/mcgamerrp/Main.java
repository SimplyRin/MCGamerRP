package net.simplyrin.mcgamerrp;

import java.time.OffsetDateTime;

import com.connorlinfoot.discordrp.DiscordRP;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.DiscordBuild;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.exceptions.NoDiscordClientException;

import net.md_5.bungee.api.ChatColor;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.simplyrin.mcgamerrp.utils.ThreadPool;

/**
 * Created by SimplyRin on 2019/10/06.
 *
 * Copyright (c) 2019 SimplyRin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
@Mod(modid = "MCGamerRP", version = "1.0")
public class Main {

	private IPCClient ipcClient;
	private boolean isMCGamer;

	private IPCClient _default;
	private DiscordBuild discordBuild;

	private OffsetDateTime offsetDateTime;

	private String serverNumber;

	private GameStatus gameStatus;

	@EventHandler
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
		String address = event.manager.getRemoteAddress().toString().toLowerCase();
		String serverIP = Minecraft.getMinecraft().getCurrentServerData().serverIP;

		if (address.contains("mcgamer.net") || serverIP.contains("mcgamer.net")) {
			this.isMCGamer = true;
			this.gameStatus = GameStatus.Lobby;

			try {
				Class.forName("com.connorlinfoot.discordrp.DiscordRP");

				IPCClient ipcClient = DiscordRP.getInstance().getIpcClient();

				this._default = ipcClient;
				this.discordBuild = ipcClient.getDiscordBuild();

				ipcClient.close();
			} catch (ClassNotFoundException e) {
			}

			System.out.println("Connected to mcgamer.net");
			this.setRichPresence(true);
		}
	}

	@SubscribeEvent
	public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
		this.offsetDateTime = null;
		this.disconnect();

		if (this.isMCGamer) {
			this.isMCGamer = false;

			if (this._default != null & this.discordBuild != null) {
				try {
					this._default.connect(this.discordBuild);
				} catch (Exception e) {
				}
			}
		}
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		String message = ChatColor.stripColor(event.message.getFormattedText()).trim();
		// String[] args = message.split(" ");

		if (!this.isMCGamer) {
			return;
		}

		GameStatus gameStatus = this.gameStatus;

		if (message.equals("Sending you to a hub...")) {
			this.gameStatus = GameStatus.Lobby;
		}
		if (message.startsWith("You joined Survival Games")) {
			this.gameStatus = GameStatus.InGame;
		}
		if (message.equals("[MCSG] You have been eliminated from the games.")) {
			this.gameStatus = GameStatus.Spectating;
		}

		if (!this.gameStatus.equals(gameStatus)) {
			if (this.gameStatus.equals(GameStatus.Lobby)) {
				ThreadPool.run(() -> {
					try {
						Thread.sleep(1000);
					} catch (Exception e) {
					}
					this.setRichPresence(false);
				});
				return;
			}
			this.setRichPresence(false);
		}
	}

	public void setRichPresence(boolean threadSleep) {
		ThreadPool.run(() -> {
			if (threadSleep) {
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
				}
			}

			Minecraft.getMinecraft().addScheduledTask(() -> {
				ScoreObjective scoreObjective = Minecraft.getMinecraft().theWorld.getScoreboard().getObjectiveInDisplaySlot(1);

				String server = "Unknown";

				Scoreboard scoreboard = scoreObjective.getScoreboard();
				if (scoreboard != null) {
					for (Score score : scoreboard.getSortedScores(scoreObjective)) {
						String line = ChatColor.stripColor(ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(score.getPlayerName()), score.getPlayerName())).trim();

						if (line.contains("NA")) {
							server = "North America";
							this.serverNumber = line.split(" ")[1];
						}
						if (line.contains("EU")) {
							server = "Europe";
							this.serverNumber = line.split(" ")[1];
						}
						if (line.contains("OCE")) {
							server = "Oceania";
							this.serverNumber = line.split(" ")[1];
						}
					}
				}

				System.out.println("Current Status: " + this.gameStatus.toString());

				String details = "Server: " + server + ", " + this.serverNumber;

				if (this.gameStatus.equals(GameStatus.Lobby)) {
					this.connect(details, "Lobby");
				}
				if (this.gameStatus.equals(GameStatus.InGame)) {
					this.connect(details, "In Game");
				}
				if (this.gameStatus.equals(GameStatus.Spectating)) {
					this.connect(details, "Death, Spectating");
				}
			});
		});
	}

	public void connect(String detail, String state) {
		this.disconnect();

		ThreadPool.run(() -> {
			this.ipcClient = new IPCClient(630281392319758368L);
			this.ipcClient.setListener(new IPCListener(){
				@Override
				public void onReady(IPCClient client) {
					RichPresence.Builder builder = new RichPresence.Builder();
					builder.setDetails(detail);
					if (state != null) {
						builder.setState(state);
					}
					if (offsetDateTime == null) {
						offsetDateTime = OffsetDateTime.now();
					}
					builder.setStartTimestamp(offsetDateTime);
					builder.setLargeImage("mcsg", "play.mcgamer.net");
					client.sendRichPresence(builder.build());
				}
			});
			try {
				this.ipcClient.connect();
			} catch (NoDiscordClientException e) {
				this.ipcClient = null;
				System.out.println("You don't have Discord Client!");
				return;
			}
		});
	}

	public void disconnect() {
		if (this.ipcClient != null) {
			this.ipcClient.close();
			this.ipcClient = null;
		}
	}

}
