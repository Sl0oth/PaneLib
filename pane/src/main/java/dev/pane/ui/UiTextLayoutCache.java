package dev.pane.ui;

/** One focused editor layout, bounded by text length; no global cache or old edit versions. */
final class UiTextLayoutCache {
    private Object key,value;
    private int characters;
    @SuppressWarnings("unchecked")
    <T> T get(Object nextKey,int length,java.util.function.Supplier<T> factory) {
        if(nextKey.equals(key))return (T)value;
        T result=factory.get();
        if(length<=65_536){key=nextKey;value=result;characters=length;}else clear();
        return result;
    }
    void clear(){key=null;value=null;characters=0;}
    int retainedCharacters(){return characters;}
}
