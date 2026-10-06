package dev.baranhan.flashmod.client.render;

import java.util.Arrays;

/** Tekrar kullanilan, buyuyebilen nokta listesi (render thread). x,y,z + alpha + genislik carpani. */
public final class Polyline {
    public float[] x = new float[64], y = new float[64], z = new float[64], a = new float[64], w = new float[64];
    public int size;

    public Polyline clear() {
        size = 0;
        return this;
    }

    public void add(float px, float py, float pz, float alpha, float width) {
        if (size == x.length) grow();
        x[size] = px;
        y[size] = py;
        z[size] = pz;
        a[size] = alpha;
        w[size] = width;
        size++;
    }

    public void add(float px, float py, float pz, float alpha) {
        add(px, py, pz, alpha, 1F);
    }

    private void grow() {
        int n = x.length * 2;
        x = Arrays.copyOf(x, n);
        y = Arrays.copyOf(y, n);
        z = Arrays.copyOf(z, n);
        a = Arrays.copyOf(a, n);
        w = Arrays.copyOf(w, n);
    }
}
