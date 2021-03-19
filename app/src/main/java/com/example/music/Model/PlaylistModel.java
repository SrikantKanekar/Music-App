package com.example.music.Model;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

public class PlaylistModel implements Serializable {
    private String name;
    private ArrayList<String> songs;
    private String key;

    public PlaylistModel() {}

    public PlaylistModel(String name, ArrayList<String> songs) {
        this.name = name;
        this.songs = songs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<String> getSongs() {
        return songs;
    }

    public void setSongs(ArrayList<String> songs) {
        this.songs = songs;
    }

    @Exclude
    public String getKey() {
        return key;
    }

    @Exclude
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistModel that = (PlaylistModel) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(songs, that.songs) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, songs, key);
    }
}
