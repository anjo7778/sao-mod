package dev.anjo7778.sao.client;

import dev.anjo7778.sao.entity.FrenzyBoar;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original cuboid boar mesh with independently animated limbs, ears, head and tail. */
public final class FrenzyBoarModel extends HierarchicalModel<FrenzyBoar> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("sao","frenzy_boar"),"main");
    private final ModelPart root,head,leftFront,rightFront,leftRear,rightRear,tail,leftEar,rightEar;
    public FrenzyBoarModel(ModelPart root){
        this.root=root;head=root.getChild("head");leftFront=root.getChild("left_front");rightFront=root.getChild("right_front");
        leftRear=root.getChild("left_rear");rightRear=root.getChild("right_rear");tail=root.getChild("tail");
        leftEar=head.getChild("left_ear");rightEar=head.getChild("right_ear");
    }
    public static LayerDefinition layer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-7,-6,-11,14,12,22),PartPose.offset(0,9.5f,0));
        root.addOrReplaceChild("shoulders",CubeListBuilder.create().texOffs(0,36).addBox(-7,-8,-5,14,16,10),PartPose.offset(0,9.5f,-6));
        root.addOrReplaceChild("mane",CubeListBuilder.create().texOffs(50,36).addBox(-1,-3,-10,2,4,20),PartPose.offset(0,2,-1));
        var head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(74,0).addBox(-5,-5,-8,10,10,10),PartPose.offset(0,9,-10));
        head.addOrReplaceChild("snout",CubeListBuilder.create().texOffs(74,22).addBox(-4,-2,-5,8,6,5),PartPose.offset(0,1,-8));
        head.addOrReplaceChild("left_ear",CubeListBuilder.create().texOffs(24,64).addBox(-1,-5,-1,3,5,2),PartPose.offsetAndRotation(3.5f,-4,-2,-.28f,0,.35f));
        head.addOrReplaceChild("right_ear",CubeListBuilder.create().texOffs(24,64).mirror().addBox(-2,-5,-1,3,5,2),PartPose.offsetAndRotation(-3.5f,-4,-2,-.28f,0,-.35f));
        head.addOrReplaceChild("left_tusk",CubeListBuilder.create().texOffs(36,64).addBox(-1,-5,-1,2,5,2),PartPose.offsetAndRotation(4,5,-10,-.32f,0,.25f));
        head.addOrReplaceChild("right_tusk",CubeListBuilder.create().texOffs(36,64).mirror().addBox(-1,-5,-1,2,5,2),PartPose.offsetAndRotation(-4,5,-10,-.32f,0,-.25f));
        leg(root,"left_front",4.5f,-6);leg(root,"right_front",-4.5f,-6);leg(root,"left_rear",4.5f,7);leg(root,"right_rear",-4.5f,7);
        root.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(50,64).addBox(-1,0,-1,2,5,2),PartPose.offsetAndRotation(0,8,11,-.9f,0,0));
        return LayerDefinition.create(mesh,128,96);
    }
    private static void leg(PartDefinition root,String name,float x,float z){
        root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(0,64).addBox(-2.5f,0,-2.5f,5,9,5),PartPose.offset(x,15,z));
    }
    @Override public ModelPart root(){return root;}
    @Override public void setupAnim(FrenzyBoar boar,float limbSwing,float limbAmount,float age,float yaw,float pitch){
        root.getAllParts().forEach(ModelPart::resetPose);
        float a=Mth.cos(limbSwing*.9f)*1.15f*Math.min(1,limbAmount);
        float b=Mth.cos(limbSwing*.9f+Mth.PI)*1.15f*Math.min(1,limbAmount);
        leftFront.xRot=a;rightRear.xRot=a;rightFront.xRot=b;leftRear.xRot=b;
        head.yRot=Mth.clamp(yaw*.017453292f,-.6f,.6f);
        head.xRot=Mth.clamp(pitch*.017453292f,-.35f,.35f)-Mth.sin(attackTime*Mth.PI)*.52f;
        tail.yRot=Mth.sin(age*.17f)*.28f;
        leftEar.zRot+=Mth.sin(age*.11f)*.07f;rightEar.zRot-=Mth.sin(age*.11f)*.07f;
    }
}
