package dev.pane.ui;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class UiHudNativeCacheTest {
 @Test void stableFramesReuseMapButTransformsAndSourceChangesRefresh(){
  var c=new UiHudNativeCache();var local=Map.of("item",new NativeBounds(1,2,10,20));var rect=new UiRect(20,30,100,50);
  var first=c.resolve(local,rect,100,50,2);
  for(int i=0;i<10000;i++)assertSame(first,c.resolve(local,rect,100,50,2));
  assertEquals(1,c.builds());
  var moved=c.resolve(local,new UiRect(25,30,100,50),100,50,2);assertNotSame(first,moved);
  assertEquals(52,moved.get("item").x());
  assertNotSame(moved,c.resolve(local,new UiRect(25,30,100,50),100,50,3));
  c.clear();var updated=c.resolve(Map.of("item",new NativeBounds(5,2,10,20)),rect,100,50,2);
  assertEquals(50,updated.get("item").x());assertEquals(4,c.builds());
 }
}
