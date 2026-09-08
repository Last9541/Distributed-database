package internal.lsm.implementation.lru;

import java.util.Objects;

public class LruSizeKey {
    private long id;
    private long offset;

    public LruSizeKey(long id, long offset) {
        this.id = id;
        this.offset = offset;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LruSizeKey lruSizeKey = (LruSizeKey) o;
        return id == lruSizeKey.id && offset == lruSizeKey.offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, offset);
    }
}
