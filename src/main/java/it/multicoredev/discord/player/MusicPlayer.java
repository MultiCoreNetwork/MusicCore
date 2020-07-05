package it.multicoredev.discord.player;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import it.multicoredev.discord.Utils;
import it.multicoredev.mclib.yaml.Configuration;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class MusicPlayer {
    private final Configuration config;
    private AudioPlayerManager playerManager;
    private Map<Long, GuildMusicManager> musicManagers;

    public MusicPlayer(Configuration config) {
        this.config = config;
        musicManagers = new HashMap<>();
        playerManager = new DefaultAudioPlayerManager();

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long id = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(id);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(id, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        connectToFirstVoiceChannel(guild.getAudioManager());

        musicManager.scheduler.queueAndPlay(track);
    }

    public void play(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild);

        musicManager.scheduler.playFirst();
    }

    public void loadAndPlay(TextChannel channel, String url) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (musicManager.player.getPlayingTrack() == null) {
                    Utils.sendMessage(channel, config.getString("messages.play"), new String[]{"{track}", "{author}"}, new String[]{track.getInfo().title, track.getInfo().author});
                } else {
                    Utils.sendMessage(channel, config.getString("messages.playlist-add"), new String[]{"{track}", "{author}"}, new String[]{track.getInfo().title, track.getInfo().author});
                }

                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                for (AudioTrack track : playlist.getTracks()) {
                    play(channel.getGuild(), musicManager, track);
                }

                Utils.sendMessage(channel, config.getString("messages.playlist-add-playlist"), "{playlist}", playlist.getName());
            }

            @Override
            public void noMatches() {
                Utils.sendMessage(channel, config.getString("messages.not-found"));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                Utils.sendMessage(channel, config.getString("messages.not-loaded"));
            }
        });
    }

    public void addToPlaylist(Guild guild, String url) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild);

        playerManager.loadItemOrdered(musicManager, url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.scheduler.queue(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
            }

            @Override
            public void noMatches() {
            }

            @Override
            public void loadFailed(FriendlyException exception) {
            }
        });
    }

    public void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        Utils.sendMessage(channel, config.getString("messages.skip"));
    }

    public void playPause(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        if (!musicManager.player.isPaused()) {
            musicManager.player.setPaused(true);
            Utils.sendMessage(channel, config.getString("messages.pause"));
        } else {
            musicManager.player.setPaused(false);
            Utils.sendMessage(channel, config.getString("messages.resume"));
        }
    }

    public void stop(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.player.stopTrack();

        Utils.sendMessage(channel, config.getString("messages.stop"));
    }

    public void emptyPlaylist(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        musicManager.scheduler.empty();
        Utils.sendMessage(channel, config.getString("messages.empty"));
    }

    public void setVolume(TextChannel channel, int volume) {
        if (volume > 100) volume = 100;
        else if (volume < 0) volume = 0;

        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.player.setVolume(volume);

        Utils.sendMessage(channel, config.getString("messages.volume"), "{volume}", String.valueOf(volume));
    }

    public void loop(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        musicManager.scheduler.updateLooping();

        config.set("guilds." + channel.getGuild().getId() + ".loop", musicManager.scheduler.isLooping());

        if (musicManager.scheduler.isLooping()) {
            Utils.sendMessage(channel, config.getString("messages.loop-on"));
        } else {
            Utils.sendMessage(channel, config.getString("messages.loop-off"));
        }
    }

    public void setLooping(Guild guild, boolean loop) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild);

        musicManager.scheduler.setLooping(loop);
    }

    public AudioTrack getPlayingTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        return musicManager.player.getPlayingTrack();
    }

    public List<AudioTrack> getPlaylist(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        return musicManager.scheduler.getPlaylist();
    }

    public List<String> getPlaylistSrc(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        List<AudioTrack> playlist = musicManager.scheduler.getPlaylist();
        List<String> srcs = new ArrayList<>();

        for (AudioTrack track : playlist) {
            srcs.add(track.getInfo().uri);
        }

        return srcs;
    }
}
