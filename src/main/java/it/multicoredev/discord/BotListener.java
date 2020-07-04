package it.multicoredev.discord;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import it.multicoredev.discord.player.MusicPlayer;
import it.multicoredev.mclib.yaml.Configuration;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.Arrays;
import java.util.List;
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
public class BotListener extends ListenerAdapter {
    private final MusicPlayer player;
    private final Configuration config;

    public BotListener(MusicPlayer player, Configuration config) {
        this.player = player;
        this.config = config;
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        TextChannel channel = event.getChannel();

        String[] messages = event.getMessage().getContentRaw().split(" ");
        String command = messages[0].toLowerCase();
        String[] args = Arrays.copyOfRange(messages, 1, messages.length);

        dispatchCommand(event, channel, command, args);
    }

    private void dispatchCommand(GuildMessageReceivedEvent event, TextChannel channel, String command, String[] args) {
        //TODO Check permissions
        if (command.equals(getString("commands.join"))) {
            if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.VOICE_CONNECT)) {
                Utils.sendMessage(channel, config.getString("messages.insufficient-perms"));
                return;
            }

            Member member = event.getMember();

            if (member == null) {
                Utils.sendMessage(channel, config.getString("messages.internal-error"));
                Utils.sendMessage(channel, "*Member is null.*");
                return;
            }

            GuildVoiceState voiceState = member.getVoiceState();

            if (voiceState == null) {
                Utils.sendMessage(channel, config.getString("messages.internal-error"));
                Utils.sendMessage(channel, "*GuildVoiceState is null.*");
                return;
            }

            VoiceChannel voiceChannel = voiceState.getChannel();

            if (voiceChannel == null) {
                Utils.sendMessage(channel, config.getString("messages.internal-error"));
                Utils.sendMessage(channel, "*Can't connect to VoiceChannel*");
                return;
            }

            AudioManager audioManager = channel.getGuild().getAudioManager();

            if (audioManager.isAttemptingToConnect()) {
                Utils.sendMessage(channel, config.getString("messages.chill-my-friend"));
                return;
            }

            audioManager.openAudioConnection(voiceChannel);
            Utils.sendMessage(channel, config.getString("messages.channel-join"), "{channel}", voiceChannel.getName());
        } else if (command.equals(getString("commands.leave"))) {
            GuildVoiceState voiceState = channel.getGuild().getSelfMember().getVoiceState();

            if (voiceState == null) {
                Utils.sendMessage(channel, config.getString("messages.internal-error"));
                Utils.sendMessage(channel, "*GuildVoiceState is null.*");
                return;
            }

            VoiceChannel voiceChannel = voiceState.getChannel();

            if (voiceChannel == null) {
                Utils.sendMessage(channel, config.getString("messages.not-connected"));
                return;
            }

            channel.getGuild().getAudioManager().closeAudioConnection();
            Utils.sendMessage(channel, config.getString("messages.channel-leave"));
        } else if (command.equals(getString("commands.volume"))) {
            if (args.length < 1) {
                Utils.sendMessage(channel, config.getString("messages.incorrect-usage"));
                return;
            }

            int volume;
            try {
                volume = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                return;
            }

            player.setVolume(channel, volume);
        } else if (command.equals(getString("commands.play"))) {
            if (args.length == 1) {
                player.loadAndPlay(channel, args[0]);
            } else if (args.length == 0) {
                //TODO check if is paused then resume or if is stopped then start playing
            } else {
                Utils.sendMessage(channel, config.getString("messages.incorrect-usage"));
            }
        } else if (command.equals(getString("commands.pause"))) {
            player.playPause(channel);
        } else if (command.equals(getString("commands.skip"))) {
            player.skipTrack(channel);
        } else if (command.equals(getString("commands.stop"))) {
            player.stop(channel);
        } else if (command.equals(getString("commands.empty"))) {
            player.emptyPlaylist(channel);
        } else if (command.equals(getString("commands.loop"))) {
            player.loop();
        } else if (command.equals(getString("commands.save"))) {
            config.set("playlist", player.getPlaylistSrc(channel.getGuild()));
            Utils.sendMessage(channel, config.getString("messages.save"));
        } else if (command.equals(getString("commands.autostart"))) {
            boolean autostart = config.getBoolean("autostart");
            config.set("autostart", !autostart);

            Utils.sendMessage(channel, autostart ? config.getString("messages.autostart-off") : config.getString("messages.autostart-on"));
        } else if (command.equals(getString("commands.playlist"))) {
            List<AudioTrack> playlist = player.getPlaylist(channel.getGuild());

            for (AudioTrack track : playlist) {
                Utils.sendMessage(channel, config.getString("messages.audio-track"),
                        new String[]{
                                "{track}",
                                "{author}",
                                "{duration}"},
                        new String[]{
                                track.getInfo().title,
                                track.getInfo().author,
                                getTrackDuration(track.getDuration())
                        });
            }
        }
    }

    private String getString(String path) {
        return config.getString(path).toLowerCase();
    }

    private String getTrackDuration(long duration) {
        long seconds = duration / 1000;
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds = seconds - TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds = TimeUnit.MINUTES.toSeconds(minutes);

        return (hours > 0 ? String.format("%02d", hours) + ":" : "") +
                (minutes > 0 ? String.format("%02d", minutes) + ":" : "") +
                String.format("%02d", seconds);
    }
}
