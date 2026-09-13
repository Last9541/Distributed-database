package internal.lsm.implementation.lru;

public class HitMiss {
    private int hit;
    private int miss;

    public HitMiss(int hit, int miss) {
        this.hit = hit;
        this.miss = miss;
    }

    public int getHit() {
        return hit;
    }

    public void setHit(int hit) {
        this.hit = hit;
    }

    public int getMiss() {
        return miss;
    }

    public void setMiss(int miss) {
        this.miss = miss;
    }
}
