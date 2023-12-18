package org.pente.gameServer.client.awt;

import java.applet.AudioClip;
import java.util.Hashtable;

public class Sounds {

    private Hashtable sounds = new Hashtable();

    public void addSound(AudioClip sound, String name) {
        sounds.put(name, sound);
    }

    public AudioClip getSound(String name) {
        return (AudioClip) sounds.get(name);
    }

    public void playSound(String name) {
        AudioClip sound = getSound(name);
        if (sound != null) {
            sound.play();
        }
    }
}
