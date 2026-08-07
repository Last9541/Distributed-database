Put(key, value)


If key exists, new value replaces old.


Empty key is illegal; empty value is allowed.


On success, later Get(key) returns this value.


Get(key)


Returns the latest visible value for key.


If the latest record is a tombstone (Delete), treat as not found.


Delete(key)


Marks key as deleted (we’ll implement tombstones later).


Deleting a non-existent key is not an error.


Close()


Finish all work and release resources.


After Close, any call should return a “store closed” error.
