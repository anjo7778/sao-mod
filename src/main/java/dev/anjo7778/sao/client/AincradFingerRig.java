package dev.anjo7778.sao.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anjo7778.sao.entity.CityNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Small articulated digits attached to the end of the selected vanilla cuboid arm.
 * The unmodified arm provides the palm; the mesh samples the existing skin's wrist UV.
 * Each digit has two segments, with independent knuckle and tip rotations.
 * Only an active, free menu hand receives geometry. No skin/model replacement,
 * item mutation, global pose override, or geometry on armor/sleeve parts.
 */
public final class AincradFingerRig {
    private static final Map<ModelPart,Active> ACTIVE=new WeakHashMap<>();
    private static final Mesh[] MESHES=new Mesh[4];
    private AincradFingerRig(){}
    private static final class Active{
        final WeakReference<LivingEntity> actor;
        final HumanoidArm arm;
        final boolean slim;
        final float extension;
        boolean drawn;
        Active(LivingEntity actor,HumanoidArm arm,boolean slim,float extension){
            this.actor=new WeakReference<>(actor);this.arm=arm;this.slim=slim;this.extension=extension;
        }
    }
    private record Mesh(ModelPart root,ModelPart[] fingers,ModelPart[] tips,ModelPart thumb,ModelPart thumbTip){}
    public static void clear(PlayerModel<?> model){
        ACTIVE.remove(model.rightArm);ACTIVE.remove(model.leftArm);
    }
    public static void arm(PlayerModel<?> model,LivingEntity actor,HumanoidArm arm,float extension){
        if(arm==null||!MenuPreferences.fingers)return;
        boolean slim=actor instanceof AbstractClientPlayer p&&p.getSkin().model()==PlayerSkin.Model.SLIM;
        var part=arm==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        ACTIVE.put(part,new Active(actor,arm,slim,Math.max(0,Math.min(1,extension))));
    }
    /** Used by client regression probes to distinguish a pose from a submitted finger mesh. */
    public static boolean rendered(ModelPart part){
        var a=ACTIVE.get(part);return a!=null&&a.drawn;
    }
    public static float extension(float t){
        t=Math.max(0,Math.min(1,t));
        if(t<.26f)return smooth(t/.26f);
        if(t>.86f)return 1-smooth((t-.86f)/.14f);
        return 1;
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static boolean valid(Active active,LivingEntity e){
        if(e==null||!e.isAlive()||!MenuPreferences.fingers||MenuGesture.freeArm(e)!=active.arm)return false;
        if(e instanceof CityNpc npc)return MenuPreferences.npcMenus&&npc.menuOpen();
        return e==Minecraft.getInstance().player&&MenuGesture.progress(0)>=0;
    }
    public static void render(ModelPart part,PoseStack pose,VertexConsumer out,int light,int overlay,int color){
        var active=ACTIVE.get(part);
        if(active==null||!part.visible||!valid(active,active.actor.get())||active.extension<=.01f)return;
        var mesh=mesh(active.arm,active.slim);
        float open=active.extension;
        for(int i=0;i<4;i++){
            // Index + middle extend, the ring and little finger curl toward the palm.
            float bend=i<2?-.13f-(1-open)*.98f:-1.25f;
            mesh.fingers[i].xRot=bend;
            mesh.fingers[i].zRot=(i==0?.06f:i==1?-.055f:0)*(active.arm==HumanoidArm.RIGHT?1:-1);
            mesh.tips[i].xRot=i<2?-.08f-(1-open)*.65f:-1.08f;
        }
        mesh.thumb.xRot=-.68f;mesh.thumbTip.xRot=-.82f;
        pose.pushPose();
        part.translateAndRotate(pose);
        mesh.root.render(pose,out,light,overlay,color);
        pose.popPose();
        active.drawn=true;
    }
    private static Mesh mesh(HumanoidArm arm,boolean slim){
        int k=(arm==HumanoidArm.RIGHT?0:2)+(slim?1:0);
        if(MESHES[k]!=null)return MESHES[k];
        boolean right=arm==HumanoidArm.RIGHT;
        float width=slim?3:4;
        float min=right?(slim?-2:-3):-1;
        float max=min+width;
        float step=width/4f,thickness=step*.78f;
        int u=right?44:36,v=right?28:60;
        var root=new MeshDefinition();
        PartDefinition hand=root.getRoot();
        float[] lengths={1.16f,1.28f,1.10f,.83f};
        for(int i=0;i<4;i++){
            float x=right?max-step*(i+.5f):min+step*(i+.5f);
            float first=lengths[i],second=first*.83f;
            var digit=hand.addOrReplaceChild("finger"+i,
                CubeListBuilder.create().texOffs(u,v).addBox(-thickness/2,0,-.42f,thickness,first,.84f),
                PartPose.offset(x,9.96f,-.50f));
            digit.addOrReplaceChild("tip",CubeListBuilder.create().texOffs(u,v+1)
                .addBox(-thickness*.43f,0,-.37f,thickness*.86f,second,.74f),
                PartPose.offset(0,first,0));
        }
        float thumbSide=right?1:-1;
        var thumb=hand.addOrReplaceChild("thumb",CubeListBuilder.create().texOffs(u,v)
            .addBox(-.40f,0,-.43f,.80f,.94f,.86f),
            PartPose.offsetAndRotation(right?max-.16f:min+.16f,9.25f,.62f,-.68f,0,-thumbSide*.60f));
        thumb.addOrReplaceChild("tip",CubeListBuilder.create().texOffs(u,v+1)
            .addBox(-.35f,0,-.36f,.70f,.70f,.72f),PartPose.offset(0,.94f,0));
        ModelPart baked=LayerDefinition.create(root,64,64).bakeRoot();
        ModelPart[] fingers=new ModelPart[4],tips=new ModelPart[4];
        for(int i=0;i<4;i++){fingers[i]=baked.getChild("finger"+i);tips[i]=fingers[i].getChild("tip");}
        var thumbPart=baked.getChild("thumb");
        return MESHES[k]=new Mesh(baked,fingers,tips,thumbPart,thumbPart.getChild("tip"));
    }
}
