package it.multicoredev.discord;

import it.multicoredev.discord.player.MusicPlayer;
import it.multicoredev.mclib.yaml.Configuration;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Copyright © 2020 by Lorenzo Magni
 * This file is part of MusicCore.
 * MusicCore is under "The 3-Clause BSD License", you can find a copy <a href="https://opensource.org/licenses/BSD-3-Clause">here</a>.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
public class MusicCore {
    private Configuration config;
    private JDA jda;
    private MusicPlayer player;
    private String token;

    public void main(String[] args) {
        config = new Configuration(new File("config.yml"), getClass().getClassLoader().getResourceAsStream("config.yml"), true);

        try {
            config.autoload();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        token = config.getString("bot-token");
        if (token == null || token.trim().isEmpty()) {
            new IOException("bot-token cannot be null or empty.").printStackTrace();
            System.exit(-1);
        }

        player = new MusicPlayer(config);

        try {
            jda = new JDABuilder(AccountType.BOT)
                    .setToken(token)
                    .addEventListeners(new BotListener(player, config))
                    .build()
                    .awaitReady();
        } catch (LoginException | InterruptedException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        for (String id : config.getSection("guilds").getKeys()) {
            String path = "guilds." + id + ".";

            Guild guild = jda.getGuildById(id);
            if (guild == null) continue;

            boolean start = false;

            if (config.getBoolean(path + "autostart")) {
                String channelId = config.getString(path + "channel");

                VoiceChannel channel = guild.getVoiceChannelById(channelId);
                if (channel == null) continue;

                AudioManager audioManager = channel.getGuild().getAudioManager();
                if (audioManager.isAttemptingToConnect()) continue;

                audioManager.openAudioConnection(channel);
                start = true;
            }

            if (config.contains(path + "playlist")) {
                for (String url : config.getStringList(path + "playlist")) {
                    player.addToPlaylist(guild, url);
                }
            }

            if (config.contains(path + "loop")) {
                player.setLooping(guild, config.getBoolean(path + "loop"));
            }

            if (start) {
                ScheduledExecutorService runLater = Executors.newSingleThreadScheduledExecutor();
                runLater.schedule(() -> player.play(guild), 2, TimeUnit.SECONDS);
            }
        }

    }
}
