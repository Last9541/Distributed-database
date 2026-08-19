package internal.lsm;

import internal.lsm.errors.NotImplemented;

public interface Lsm {

    default void put(byte[] key, byte[] value){
        throw new NotImplemented();
    };
    default byte[] get(byte[] key){
        throw new NotImplemented();
    };
    default void delete(byte[] key){
        throw new NotImplemented();
    }; //todo maybe boolean instaed of void for put and delete


    default String stats()
    {
        return "TODO: Prints the resolved config values and “engine status: stub”";
    }

    default void close(){
        throw new NotImplemented();
    };

}
