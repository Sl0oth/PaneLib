package dev.pane.demo;
import dev.pane.ui.*;
import java.util.*;
import net.minecraft.client.Minecraft;
/** Controlled state injection into actual rendered widgets; does not claim gameplay simulation. */
final class HudEfficiencyAuditTest {
 private final WayfarerHudShowcase owner;private HudShowcaseState.Live state=HudShowcaseState.DEFAULT;
 private int stage,checks;private boolean failed;private long next,maps;private long[] paints,uploads;
 HudEfficiencyAuditTest(WayfarerHudShowcase owner){this.owner=owner;}
 void tick(Minecraft c){try{
  if(System.nanoTime()<next)return;
  switch(stage++){
   case 0->{owner.huds().forEach(h->h.setMotionScale(0));apply();}
   case 1->{baseline();for(int i=0;i<6;i++)owner.huds().get(i).setValue("legacy-shared-state-probe",1);}
   case 2->{check(changed().size()==6,"baseline shared state invalidates six real panels");System.out.println("[HudEfficiencyTest] legacy uploads="+uploaded());baseline();update(1);}
   case 3->{only(Set.of(4),"altitude changes compass only");System.out.println("[HudEfficiencyTest] projected altitude uploads="+uploaded());baseline();update(2);}
   case 4->{only(Set.of(2),"health changes vitals only");baseline();update(3);}
   case 5->{only(Set.of(3),"selected item/count changes equipment only");check(owner.belt().nativePlacementCount()==maps,"equipment repaint reuses unchanged slot geometry");baseline();update(4);}
   case 6->{only(Set.of(0),"biome changes location only");baseline();owner.compassHud().setValue("heading",90D);}
   case 7->{only(Set.of(4),"heading changes compass only");baseline();for(int i=0;i<100;i++)apply();}
   case 8->{only(Set.of(),"equal state repeated 100 times has zero paints");check(uploaded()==0,"equal state produces zero uploads");check(owner.belt().nativePlacementCount()==maps,"stable native frames reuse mapped bounds");check(owner.belt().nativeItemCount()==9,"native items continue drawing while cached");baseline();}
   case 9->{only(Set.of(),"idle has zero paints");check(uploaded()==0,"idle has zero uploads");check(owner.belt().nativePlacementCount()==maps,"idle has zero native-slot remapping");System.out.println("[HudEfficiencyTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);c.stop();}
  }next=System.nanoTime()+650_000_000L;
 }catch(Throwable e){e.printStackTrace();System.out.println("[HudEfficiencyTest] complete FAIL "+e);c.stop();}}
 private void update(int mode){var v=state;state=new HudShowcaseState.Live(mode==2?17:v.health(),v.max(),v.food(),v.armor(),v.level(),mode==3?1:v.selected(),mode==3?"Torch":v.item(),mode==4?"plains":v.biome(),v.x(),mode==1?v.y()+1:v.y(),v.z(),v.heading(),mode==3?List.of(1,1,1,1,12,47,32,1,1):v.counts());apply();}
 private void apply(){HudShowcaseState.apply(owner.huds(),state,false);}
 private void baseline(){paints=owner.huds().stream().mapToLong(h->h.snapshot().paints()).toArray();uploads=owner.huds().stream().mapToLong(h->h.snapshot().uploadedPixels()).toArray();maps=owner.belt().nativePlacementCount();}
 private Set<Integer> changed(){var set=new HashSet<Integer>();for(int i=0;i<7;i++)if(owner.huds().get(i).snapshot().paints()!=paints[i])set.add(i);return set;}
 private long uploaded(){long n=0;for(int i=0;i<7;i++)n+=owner.huds().get(i).snapshot().uploadedPixels()-uploads[i];return n;}
 private void only(Set<Integer> expected,String label){check(changed().equals(expected),label+" actual="+changed());}
 private void check(boolean ok,String label){checks++;failed|=!ok;System.out.println("[HudEfficiencyTest] "+label+": "+(ok?"PASS":"FAIL"));}
}
