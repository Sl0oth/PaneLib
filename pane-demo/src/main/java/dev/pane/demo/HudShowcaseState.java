package dev.pane.demo;
import dev.pane.ui.*;
import java.util.*;
/** Dependencies shared by the two showcase consumers; no new reactive framework. */
final class HudShowcaseState {
 record Live(int health,int max,int food,int armor,int level,int selected,String item,String biome,int x,int y,int z,int heading,List<Integer> counts){}
 record Location(String biome,int x,int y,int z){}
 record Vitals(int health,int max,int food,int armor){}
 record Equipment(int level,int selected,String item,List<Integer> counts){}
 record Navigation(int y,int heading){}
 static final Live DEFAULT=new Live(20,20,20,0,0,0,"Iron Sword","Wilderness",0,64,0,0,List.of(1,1,1,1,12,48,32,1,1));
 static void apply(List<UiHud> huds,Live v,boolean coordinates){
  huds.get(0).setValue("location",new Location(v.biome,coordinates?v.x:0,coordinates?v.y:0,coordinates?v.z:0));
  huds.get(2).setValue("vitals",new Vitals(v.health,v.max,v.food,v.armor));
  huds.get(3).setValue("equipment",new Equipment(v.level,v.selected,v.item,List.copyOf(v.counts)));
  huds.get(4).setValue("navigation",new Navigation(coordinates?0:v.y,coordinates?v.heading:0));
  // Journal and badge have authored static content; neither depends on sampled player state.
 }
 static Location location(UiContext ui){return ui.value("location") instanceof Location v?v:new Location("Wilderness",0,64,0);}
 static Vitals vitals(UiContext ui){return ui.value("vitals") instanceof Vitals v?v:new Vitals(20,20,20,0);}
 static Equipment equipment(UiContext ui){return ui.value("equipment") instanceof Equipment v?v:new Equipment(0,0,"Iron Sword",DEFAULT.counts);}
 static Navigation navigation(UiContext ui){return ui.value("navigation") instanceof Navigation v?v:new Navigation(64,0);}
}
