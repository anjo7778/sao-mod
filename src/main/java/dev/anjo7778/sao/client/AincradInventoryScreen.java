package dev.anjo7778.sao.client;

import dev.anjo7778.sao.network.ProfileNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Native Aincrad presentation of the real vanilla player container.
 * Slot identities, crafting, quick-move, swapping, dragging and cursor stacks remain
 * Minecraft's authoritative InventoryMenu/AbstractContainerScreen behavior.
 * No second inventory, custom item packets or cosmetic copies.
 */
public final class AincradInventoryScreen extends AbstractContainerScreen<InventoryMenu> {
    private static final int WHITE=0xfff9fcfb,INK=0xff3e4b50,GRAY=0xff6c7e83,PALE=0xeef3f7f6,
        EDGE=0xffaebcc0,DARK=0xe3445258,SLOT=0xffdfe7e6,AMBER=0xffe2a126;
    private final AincradMenu parent;
    private final LocalPlayer owner;

    public AincradInventoryScreen(LocalPlayer player,AincradMenu parent){
        super(player.inventoryMenu,player.getInventory(),Component.literal("AINCRAD / ITEMS"));
        this.owner=player;this.parent=parent;
        imageWidth=176;imageHeight=166;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial){
        // AbstractContainerScreen renders its backdrop through this hook.
        // Skip the vanilla blur/dim, but explicitly draw the native container panels.
        renderBg(g,partial,mouseX,mouseY);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);
        renderTooltip(g,mouseX,mouseY);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos;
        frame(g,x-5,y-19,186,189);
        g.fill(x-5,y-19,x-2,y-3,AMBER);
        text(g,"ITEMS / EQUIPMENT",x+2,y-14,INK,172);
        text(g,"CRAFT",x+99,y+6,GRAY,66);
        text(g,"INVENTORY",x+8,y+74,GRAY,154);
        for(Slot slot:menu.slots){
            if(!slot.isActive())continue;
            boolean selected=slot.index>=36&&slot.index<=44&&slot.index-36==owner.getInventory().selected;
            int sx=x+slot.x,sy=y+slot.y;
            g.fill(sx-1,sy-1,sx+17,sy+17,selected?AMBER:EDGE);
            g.fill(sx,sy,sx+16,sy+16,SLOT);
            if(!slot.hasItem()&&slot.index==45)g.fill(sx+3,sy+12,sx+13,sy+13,EDGE);
        }
        // Craft result indicator and dividers leave the vanilla hit boxes unchanged.
        g.fill(x+134,y+35,x+148,y+36,GRAY);
        for(int i=0;i<4;i++)g.fill(x+147-i,y+32+i,x+148,y+33+i,GRAY);
        for(int i=0;i<3;i++)g.fill(x+145+i,y+37+i,x+148,y+38+i,GRAY);
        g.fill(x+7,y+137,x+169,y+138,EDGE);
        if(minecraft!=null&&minecraft.player==owner){
            g.enableScissor(x+26,y+7,x+76,y+79);
            try{InventoryScreen.renderEntityInInventoryFollowsMouse(g,x+26,y+7,x+76,y+79,29,.0625f,mouseX,mouseY,owner);}
            finally{g.disableScissor();}
        }
        if(width>=350){
            int px=x-88,py=y-19;
            frame(g,px,py,77,99);
            g.fill(px,py,px+3,py+16,AMBER);
            text(g,"CHARACTER",px+6,py+5,INK,65);
            text(g,owner.getName().getString(),px+6,py+26,INK,65);
            var s=ProfileNetwork.clientSnapshot();
            if(s!=null&&s.owner().equals(owner.getUUID())&&s.available())text(g,"Lv. "+s.level(),px+6,py+39,GRAY,65);
            text(g,"Armor "+owner.getArmorValue(),px+6,py+55,GRAY,65);
            text(g,"Enchant Lv "+owner.experienceLevel,px+6,py+68,GRAY,65);
            text(g,"ESC  Return",px+6,py+85,INK,65);
        }
        if(width>=390){
            int px=x+188,py=y-19;
            frame(g,px,py,82,72);
            g.fill(px,py,px+3,py+16,AMBER);
            text(g,"CONTROLS",px+6,py+5,INK,70);
            text(g,"Click: move",px+6,py+25,GRAY,70);
            text(g,"Shift: transfer",px+6,py+38,GRAY,70);
            text(g,"Right: split",px+6,py+51,GRAY,70);
        }
    }
    private static void frame(GuiGraphics g,int x,int y,int w,int h){
        g.fill(x+2,y+3,x+w+2,y+h+3,0x50516367);
        g.fill(x,y,x+w,y+h,PALE);
        g.fill(x,y,x+w,y+1,WHITE);g.fill(x,y,x+1,y+h,WHITE);
        g.fill(x+w-1,y+1,x+w,y+h,EDGE);g.fill(x,y+h-1,x+w,y+h,EDGE);
        g.fill(x+4,y+16,x+w-4,y+17,EDGE);
    }
    private static void text(GuiGraphics g,String value,int x,int y,int color,int max){
        AincradType.draw(g,Minecraft.getInstance().font,value,x,y,color,max);
    }
    // UI coordinates are based on the actual slots, not a separate inventory representation.
    int slotX(int index){return leftPos+menu.getSlot(index).x+8;}
    int slotY(int index){return topPos+menu.getSlot(index).y+8;}
    @Override public void onClose(){
        // Vanilla closes the real container and handles any carried stack first.
        var client=minecraft;
        boolean returnToMenu=parent!=null&&client!=null&&client.player==owner&&owner.isAlive()
            &&owner.containerMenu==owner.inventoryMenu;
        super.onClose();
        if(returnToMenu&&client.player==owner&&owner.isAlive()&&owner.containerMenu==owner.inventoryMenu)client.setScreen(parent);
    }
}
